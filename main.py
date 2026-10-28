"""Drive.Net API -- Real-Time V2V Vehicle Safety, GPS Distance, and Explainable Driving Behaviour Engine.

Run:  uvicorn main:app --reload --port 8000 --host 0.0.0.0

Provides:
  - Genuine multi-device V2V real-time location exchange & geodesic proximity filtering.
  - WebSocket bidirectional telemetry streaming (/ws/v2v/{vehicle_id}).
  - REST endpoints for V2V location updates and active vehicle discovery.
  - Mountain road, hairpin bend, and switchback awareness caution logic.
  - Lateral instability and lane drift classification.
  - Temporal risk scoring with SHAP explainability (/predict and /predict/batch).
"""

from __future__ import annotations

import logging
from typing import Dict, List, Optional

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

import features as F
from v2v_engine import (
    LocationUpdate,
    NearbyVehicleInfo,
    v2v_registry,
    evaluate_lateral_instability,
    haversine_distance,
    initial_bearing,
    determine_relative_position
)

app = FastAPI(
    title="Drive.Net API",
    version="3.1.0",
    description="Drive.Net Real-Time Connected Vehicle Safety, GPS-Based V2V & Explainable Risk Engine",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("drive_net")

try:
    model, _ = F.load_model()
    FEATURE_ORDER = F.model_feature_order(model)
    MODEL_LOADED = True
except Exception as e:
    logger.warning(f"Initial model loading deferred (model file will be loaded upon training completion): {e}")
    MODEL_LOADED = False
    FEATURE_ORDER = F.FEATURES


# --------------------------------------------------------------------------- #
# Schemas
# --------------------------------------------------------------------------- #
class TelemetryPoint(BaseModel):
    """One raw telemetry sample from vehicle or smartphone sensors."""
    Timestamp: float = Field(..., ge=0, description="Seconds since trip start")
    Speed_kmh: float = Field(..., ge=0, description="GPS speed (km/h)")
    Accel_X_Filtered: float = Field(..., description="Lateral accel, Kalman-filtered (g)")
    Accel_Y_Filtered: float = Field(..., description="Longitudinal accel, KF (g)")
    Accel_Z_Filtered: float = Field(..., description="Vertical accel, KF (g)")
    Pitch: float = Field(..., description="Device pitch (degrees)")
    Yaw: float = Field(..., description="Heading (radians) -- used via Yaw_std")


class TelemetryWindow(BaseModel):
    """A contiguous, time-ordered buffer of samples from one trip."""
    readings: List[TelemetryPoint] = Field(..., min_length=2)
    trip_id: Optional[str] = Field(None, description="Optional label, echoed back")


class LateralCheckRequest(BaseModel):
    speed_kmh: float
    lateral_accel_g: float
    yaw_rate_rads: Optional[float] = None
    accuracy_m: float = 5.0


def window_to_frame(window: TelemetryWindow) -> pd.DataFrame:
    frame = pd.DataFrame([r.model_dump() for r in window.readings])
    missing = [c for c in F.RAW_INPUT_COLS if c not in frame.columns]
    if missing:
        raise HTTPException(422, f"Missing required columns: {missing}")
    return frame


def window_span(frame: pd.DataFrame) -> float:
    return float(frame["Timestamp"].max() - frame["Timestamp"].min())


def coverage_warning(span: float) -> Optional[str]:
    if span < F.MIN_WINDOW_SECONDS:
        return (
            f"Window spans {span:.1f}s but the model's longest rolling window is "
            f"{F.MIN_WINDOW_SECONDS:.0f}s. Send at least {F.MIN_WINDOW_SECONDS:.0f}s "
            f"of history for a fully confident score."
        )
    return None


def row_payload(row: pd.Series, index: int) -> Dict:
    impacts = {name: float(row.get(f"SHAP_{name}", 0.0)) for name in FEATURE_ORDER if f"SHAP_{name}" in row}
    top_feature = max(impacts, key=lambda k: abs(impacts[k])) if impacts else "Speed_kmh"
    pred_risk = int(row.get("Predicted_Risk", 0))

    return {
        "index": index,
        "timestamp": float(row["Timestamp"]),
        "risk_level": pred_risk,
        "risk_label": f"{F.CLASS_NAMES[pred_risk]} Risk",
        "behaviour": F.CLASS_BEHAVIOURS[pred_risk],
        "confidence_scores": {
            f"{name.lower()}_risk": float(row.get(f"Proba_{name}", 0.33))
            for name in F.CLASS_NAMES
        },
        "temporal_features": {name: float(row.get(name, 0.0)) for name in FEATURE_ORDER},
        "explainability_shap_impacts": impacts,
        "top_contributing_feature": top_feature,
    }


# --------------------------------------------------------------------------- #
# Core Endpoints
# --------------------------------------------------------------------------- #
@app.get("/")
def home():
    global MODEL_LOADED, model, FEATURE_ORDER
    if not MODEL_LOADED:
        try:
            model, _ = F.load_model()
            FEATURE_ORDER = F.model_feature_order(model)
            MODEL_LOADED = True
        except Exception:
            pass

    return {
        "status": "Drive.Net Backend is Online!",
        "application": "Drive.Net",
        "version": app.version,
        "model_loaded": MODEL_LOADED,
        "n_features": len(FEATURE_ORDER),
        "min_window_seconds": F.MIN_WINDOW_SECONDS,
        "v2v_active_vehicles": v2v_registry.active_vehicle_count(),
    }


@app.get("/schema")
def schema():
    """Runtime feature contract -- the mobile client reads this dynamically."""
    return {
        "required_input_columns": F.RAW_INPUT_COLS,
        "rolling_windows": list(F.ROLL_WINDOWS),
        "min_window_seconds": F.MIN_WINDOW_SECONDS,
        "nominal_hz": F.NOMINAL_HZ,
        "recommended_buffer_samples": int(F.MIN_WINDOW_SECONDS * F.NOMINAL_HZ),
        "model_features": FEATURE_ORDER,
        "engineered_features": {
            "instantaneous": F.INSTANT_FEATURES,
            "jerk": F.JERK_FEATURES,
            "rolling": F.ROLLING_FEATURES,
        },
        "classes": {i: f"{n} Risk" for i, n in enumerate(F.CLASS_NAMES)},
    }


# --------------------------------------------------------------------------- #
# Real-Time V2V Vehicle-to-Vehicle Communication Endpoints
# --------------------------------------------------------------------------- #
@app.post("/api/v2v/location")
def update_v2v_location(payload: LocationUpdate):
    """Publish real vehicle GPS location and receive validated nearby active vehicles."""
    accepted, message = v2v_registry.register_update(payload)
    if not accepted:
        raise HTTPException(status_code=400, detail=message)

    nearby = v2v_registry.get_nearby_vehicles_for(payload.vehicle_id)

    # Check for immediate alerts (winding road, aggressive tailgater, etc.)
    alerts = [v.warning_message for v in nearby if v.warning_message is not None]

    return {
        "status": "ok",
        "vehicle_id": payload.vehicle_id,
        "nearby_count": len(nearby),
        "nearby_vehicles": [v.model_dump() for v in nearby],
        "active_alerts": alerts,
        "server_timestamp": pd.Timestamp.now().timestamp(),
    }


@app.get("/api/v2v/nearby")
def get_nearby_vehicles(vehicle_id: str = Query(..., description="Your pseudonymous vehicle ID")):
    """Fetch latest nearby vehicles within radius for a registered vehicle."""
    nearby = v2v_registry.get_nearby_vehicles_for(vehicle_id)
    return {
        "vehicle_id": vehicle_id,
        "nearby_count": len(nearby),
        "nearby_vehicles": [v.model_dump() for v in nearby],
    }


@app.get("/api/v2v/status")
def get_v2v_status():
    """Diagnostic overview of all currently active participating vehicles."""
    vehicles = v2v_registry.list_active_vehicles()
    return {
        "status": "online",
        "active_vehicle_count": len(vehicles),
        "vehicles": vehicles,
    }


@app.post("/api/v2v/session/leave")
def leave_v2v_session(vehicle_id: str = Query(..., description="Vehicle ID leaving the session")):
    """Gracefully unregister a vehicle when location sharing is paused or app exits."""
    removed = v2v_registry.remove_vehicle(vehicle_id)
    return {
        "status": "unregistered" if removed else "not_found",
        "vehicle_id": vehicle_id,
        "active_vehicle_count": v2v_registry.active_vehicle_count(),
    }


@app.post("/api/evaluate/lateral")
def check_lateral_movement(req: LateralCheckRequest):
    """Evaluate whether vehicle motion indicates lane drift, swerving, or lateral instability."""
    is_unstable, warning = evaluate_lateral_instability(
        speed_kmh=req.speed_kmh,
        lateral_accel_g=req.lateral_accel_g,
        yaw_rate_rads=req.yaw_rate_rads,
        accuracy_m=req.accuracy_m
    )
    return {
        "is_unstable": is_unstable,
        "warning": warning,
        "speed_kmh": req.speed_kmh,
        "lateral_accel_g": req.lateral_accel_g
    }


# --------------------------------------------------------------------------- #
# Real-Time V2V WebSocket Bi-directional Streaming
# --------------------------------------------------------------------------- #
@app.websocket("/ws/v2v/{vehicle_id}")
async def websocket_v2v_endpoint(websocket: WebSocket, vehicle_id: str):
    """Real-time bi-directional WebSocket session for continuous V2V location sharing."""
    await websocket.accept()
    logger.info(f"V2V WebSocket connected for vehicle: {vehicle_id}")

    try:
        while True:
            data = await websocket.receive_json()

            # Inject vehicle_id if missing from inner payload
            if "vehicle_id" not in data:
                data["vehicle_id"] = vehicle_id

            try:
                update = LocationUpdate(**data)
            except Exception as e:
                await websocket.send_json({"status": "error", "message": f"Malformed payload: {e}"})
                continue

            accepted, msg = v2v_registry.register_update(update)
            if not accepted:
                await websocket.send_json({"status": "rejected", "message": msg})
                continue

            nearby = v2v_registry.get_nearby_vehicles_for(vehicle_id)
            alerts = [v.warning_message for v in nearby if v.warning_message is not None]

            await websocket.send_json({
                "status": "ok",
                "nearby_count": len(nearby),
                "nearby_vehicles": [v.model_dump() for v in nearby],
                "active_alerts": alerts
            })

    except WebSocketDisconnect:
        logger.info(f"V2V WebSocket disconnected: {vehicle_id}")
        v2v_registry.remove_vehicle(vehicle_id)
    except Exception as exc:
        logger.error(f"WebSocket error for {vehicle_id}: {exc}")
        v2v_registry.remove_vehicle(vehicle_id)


# --------------------------------------------------------------------------- #
# ML Inference Routes (Preserved with Resilient Fallback)
# --------------------------------------------------------------------------- #
@app.post("/predict")
def predict_risk(window: TelemetryWindow):
    """Score the newest sample in the buffer, using the rest as its history."""
    global MODEL_LOADED, model, FEATURE_ORDER
    if not MODEL_LOADED:
        try:
            model, _ = F.load_model()
            FEATURE_ORDER = F.model_feature_order(model)
            MODEL_LOADED = True
        except Exception:
            raise HTTPException(status_code=503, detail="Model is currently unavailable. Ensure the model file is present.")

    frame = window_to_frame(window)
    span = window_span(frame)

    scored, skipped = F.score_window(frame)
    if scored.empty:
        raise HTTPException(
            422,
            f"No sample in this window has enough history to score. Window spans "
            f"{span:.1f}s; at least {F.MIN_WINDOW_SECONDS * F.MIN_COVERAGE:.0f}s "
            f"is required.",
        )

    latest = scored.iloc[-1]
    return {
        "trip_id": window.trip_id,
        "window": {
            "samples_received": len(frame),
            "samples_scored": int(len(scored)),
            "warmup_samples_skipped": skipped,
            "span_seconds": round(span, 3),
        },
        "warning": coverage_warning(span),
        **row_payload(latest, index=int(len(frame) - 1)),
    }


@app.post("/predict/batch")
def predict_batch(window: TelemetryWindow):
    """Score every sample in the sequence, deriving rolling features dynamically."""
    global MODEL_LOADED, model, FEATURE_ORDER
    if not MODEL_LOADED:
        try:
            model, _ = F.load_model()
            FEATURE_ORDER = F.model_feature_order(model)
            MODEL_LOADED = True
        except Exception:
            raise HTTPException(status_code=503, detail="Model is currently unavailable. Ensure the model file is present.")

    frame = window_to_frame(window)
    span = window_span(frame)

    scored, skipped = F.score_window(frame)
    if scored.empty:
        raise HTTPException(
            422,
            f"Window spans {span:.1f}s -- too short to derive rolling features. "
            f"Send at least {F.MIN_WINDOW_SECONDS:.0f}s of history.",
        )

    position = {float(t): i for i, t in enumerate(frame["Timestamp"].to_numpy())}
    results = [
        row_payload(row, index=position.get(float(row["Timestamp"]), -1))
        for _, row in scored.iterrows()
    ]
    scored_positions = {r["index"] for r in results}

    predictions = scored["Predicted_Risk"].to_numpy()
    counts = {
        f"{name.lower()}_risk_count": int((predictions == i).sum())
        for i, name in enumerate(F.CLASS_NAMES)
    }
    peak_idx = int(np.argmax(scored["Proba_High"].to_numpy()))

    return {
        "trip_id": window.trip_id,
        "summary": {
            "samples_received": len(frame),
            "samples_scored": int(len(scored)),
            "warmup_samples_skipped": skipped,
            "span_seconds": round(span, 3),
            "rolling_windows": list(F.ROLL_WINDOWS),
            **counts,
            "dominant_risk_label": f"{F.CLASS_NAMES[int(pd.Series(predictions).mode()[0])]} Risk",
            "peak_high_risk_probability": float(scored["Proba_High"].max()),
            "peak_high_risk_timestamp": float(scored["Timestamp"].iloc[peak_idx]),
        },
        "warning": coverage_warning(span),
        "skipped_indices": [i for i in range(len(frame)) if i not in scored_positions],
        "detailed_predictions": results,
    }
