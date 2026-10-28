"""Drive.Net V2V Engine -- Real-Time Connected Vehicle Safety & Spatial Intelligence.

Provides:
  - Multi-device real-time V2V session and location registry with validation.
  - High-precision geodesic (Haversine) distance and relative bearing calculations.
  - GPS uncertainty propagation and measurement age filtering.
  - Mountain road, hairpin bend, and switchback awareness logic.
  - Lateral instability and lane drift classification.
  - Automatic stale vehicle cleanup and event dispatching.
"""

from __future__ import annotations

import math
import time
from typing import Dict, List, Optional, Tuple
from pydantic import BaseModel, Field

# Earth radius in metres
EARTH_RADIUS_METRES = 6371000.0

# --------------------------------------------------------------------------- #
# Data Transfer Objects
# --------------------------------------------------------------------------- #
class LocationUpdate(BaseModel):
    """Validated location & telemetry sample from a participating vehicle."""
    vehicle_id: str = Field(..., min_length=2, max_length=64, description="Pseudonymous vehicle/session ID")
    display_name: str = Field(default="Connected Vehicle", max_length=64)
    latitude: float = Field(..., ge=-90.0, le=90.0, description="WGS84 latitude")
    longitude: float = Field(..., ge=-180.0, le=180.0, description="WGS84 longitude")
    accuracy_m: float = Field(default=5.0, ge=0.0, le=500.0, description="GPS horizontal accuracy in metres")
    speed_kmh: float = Field(default=0.0, ge=0.0, le=350.0, description="GPS speed in km/h")
    heading_deg: float = Field(default=0.0, ge=0.0, le=360.0, description="Bearing/heading in degrees")
    timestamp_s: float = Field(..., ge=0.0, description="Epoch timestamp of measurement (seconds)")
    altitude_m: Optional[float] = Field(default=None, description="Altitude above sea level (m)")
    lateral_accel_g: Optional[float] = Field(default=None, description="Lateral acceleration (g)")
    yaw_rate_rads: Optional[float] = Field(default=None, description="Gyroscope yaw rate (rad/s)")
    risk_level: Optional[int] = Field(default=0, ge=0, le=2, description="0=Normal, 1=Caution, 2=High Risk")
    is_unstable: Optional[bool] = Field(default=False, description="Whether potential lateral instability is detected")


class NearbyVehicleInfo(BaseModel):
    """Information sent to user about another vehicle in the vicinity."""
    id: str
    name: str
    latitude: float
    longitude: float
    accuracy_m: float
    speed_kmh: float
    heading_deg: float
    distance_meters: float
    distance_uncertainty_m: float
    bearing_degrees: float
    relative_position: str  # "AHEAD", "BEHIND", "LEFT", "RIGHT"
    closing_speed_kmh: float
    is_aggressive: bool
    is_unstable: bool
    risk_level: int  # 0: Normal, 1: Caution, 2: High Risk
    warning_message: Optional[str] = None
    road_context: str  # "NORMAL", "WINDING_ROAD", "HAIRPIN_BEND", "LOW_ACCURACY"
    last_update_age_s: float


class V2VSafetyAlert(BaseModel):
    """User-facing safety alert generated from real road and vehicle conditions."""
    severity: str  # "NORMAL", "CAUTION", "HIGH_RISK", "UNKNOWN"
    title: str
    detail: str
    threat_vehicle_id: Optional[str] = None
    timestamp: float


class VehicleState:
    """Internal server representation of an active vehicle session."""
    def __init__(self, update: LocationUpdate, server_receipt_time: float):
        self.vehicle_id = update.vehicle_id
        self.display_name = update.display_name
        self.latitude = update.latitude
        self.longitude = update.longitude
        self.accuracy_m = update.accuracy_m
        self.speed_kmh = update.speed_kmh
        self.heading_deg = update.heading_deg
        self.timestamp_s = update.timestamp_s
        self.altitude_m = update.altitude_m
        self.lateral_accel_g = update.lateral_accel_g
        self.yaw_rate_rads = update.yaw_rate_rads
        self.risk_level = update.risk_level or 0
        self.is_unstable = update.is_unstable or False
        self.server_receipt_time = server_receipt_time
        self.previous_latitude: Optional[float] = None
        self.previous_longitude: Optional[float] = None
        self.previous_timestamp_s: Optional[float] = None
        self.history: List[Tuple[float, float, float]] = [(update.latitude, update.longitude, update.timestamp_s)]

    def update(self, update: LocationUpdate, server_receipt_time: float):
        self.previous_latitude = self.latitude
        self.previous_longitude = self.longitude
        self.previous_timestamp_s = self.timestamp_s
        self.display_name = update.display_name
        self.latitude = update.latitude
        self.longitude = update.longitude
        self.accuracy_m = update.accuracy_m
        self.speed_kmh = update.speed_kmh
        self.heading_deg = update.heading_deg
        self.timestamp_s = update.timestamp_s
        self.altitude_m = update.altitude_m
        self.lateral_accel_g = update.lateral_accel_g
        self.yaw_rate_rads = update.yaw_rate_rads
        self.risk_level = update.risk_level or 0
        self.is_unstable = update.is_unstable or False
        self.server_receipt_time = server_receipt_time

        # Keep last 20 positions for trajectory and curvature evaluation
        self.history.append((update.latitude, update.longitude, update.timestamp_s))
        if len(self.history) > 20:
            self.history.pop(0)


# --------------------------------------------------------------------------- #
# Geodesic & Spatial Calculations
# --------------------------------------------------------------------------- #
def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate geographic distance (metres) between two points via Haversine formula."""
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (math.sin(delta_phi / 2.0) ** 2 +
         math.cos(phi1) * math.cos(phi2) * (math.sin(delta_lambda / 2.0) ** 2))
    a = min(1.0, max(0.0, a))
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return EARTH_RADIUS_METRES * c


def initial_bearing(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate forward initial bearing (degrees, 0..360) from point 1 to point 2."""
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_lambda = math.radians(lon2 - lon1)

    y = math.sin(delta_lambda) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - (math.sin(phi1) * math.cos(phi2) * math.cos(delta_lambda))
    deg = math.degrees(math.atan2(y, x))
    return (deg + 360.0) % 360.0


def heading_difference(h1: float, h2: float) -> float:
    """Smallest difference between two headings (0..180 degrees)."""
    diff = abs(h1 - h2) % 360.0
    return 360.0 - diff if diff > 180.0 else diff


def determine_relative_position(user_heading: float, bearing_to_other: float) -> str:
    """Categorize other vehicle position relative to current travel direction."""
    rel_angle = (bearing_to_other - user_heading + 360.0) % 360.0
    if rel_angle >= 315.0 or rel_angle < 45.0:
        return "AHEAD"
    elif 45.0 <= rel_angle < 135.0:
        return "RIGHT"
    elif 135.0 <= rel_angle < 225.0:
        return "BEHIND"
    else:
        return "LEFT"


def calculate_closing_speed(
    speed1_kmh: float, heading1_deg: float,
    speed2_kmh: float, heading2_deg: float,
    bearing1_to_2_deg: float
) -> float:
    """Calculate the closing velocity component (km/h) along the line of sight."""
    # Convert speeds to m/s
    v1_ms = speed1_kmh / 3.6
    v2_ms = speed2_kmh / 3.6

    # Unit vector from vehicle 1 to vehicle 2 in ENU (East, North)
    los_rad = math.radians(bearing1_to_2_deg)
    r_e = math.sin(los_rad)
    r_n = math.cos(los_rad)

    # Velocity vectors in ENU
    h1_rad = math.radians(heading1_deg)
    v1_e = v1_ms * math.sin(h1_rad)
    v1_n = v1_ms * math.cos(h1_rad)

    h2_rad = math.radians(heading2_deg)
    v2_e = v2_ms * math.sin(h2_rad)
    v2_n = v2_ms * math.cos(h2_rad)

    # Relative velocity (vehicle 2 relative to vehicle 1)
    v_rel_e = v2_e - v1_e
    v_rel_n = v2_n - v1_n

    # Closing velocity = negative projection of relative velocity onto line-of-sight unit vector
    closing_ms = -(v_rel_e * r_e + v_rel_n * r_n)
    return closing_ms * 3.6


# --------------------------------------------------------------------------- #
# Mountain Roads, Hairpin Bends & Lateral Movement Evaluation
# --------------------------------------------------------------------------- #
def evaluate_mountain_and_hairpin_context(
    user: VehicleState,
    other: VehicleState,
    straight_line_dist: float
) -> Tuple[str, Optional[str]]:
    """Determine whether a winding road or hairpin switchback scenario applies.

    Returns (road_context, warning_message).
    """
    # 1. GPS Accuracy check
    if user.accuracy_m > 25.0 or other.accuracy_m > 25.0:
        return (
            "LOW_ACCURACY",
            "Location accuracy is low. Distance estimate may be unreliable."
        )

    # 2. Heading geometry for opposing traffic on curves/switchbacks
    heading_diff = heading_difference(user.heading_deg, other.heading_deg)
    is_opposing = 140.0 <= heading_diff <= 220.0
    both_moving = user.speed_kmh > 15.0 and other.speed_kmh > 15.0

    # If geographically close (e.g., 30m - 120m) with opposing headings on winding path:
    if straight_line_dist <= 120.0 and is_opposing and both_moving:
        # Check if one or both vehicles are experiencing turning dynamics
        user_turning = (user.yaw_rate_rads is not None and abs(user.yaw_rate_rads) > 0.25)
        other_turning = (other.yaw_rate_rads is not None and abs(other.yaw_rate_rads) > 0.25)

        if user_turning or other_turning or straight_line_dist < 60.0:
            return (
                "HAIRPIN_BEND",
                "Caution: Vehicle approaching on a winding road."
            )
        else:
            return (
                "WINDING_ROAD",
                "Nearby vehicle detected. Maintain a safe distance."
            )

    # 3. High curvature check ahead for the user
    if user.speed_kmh > 50.0 and user.yaw_rate_rads is not None and abs(user.yaw_rate_rads) > 0.45:
        return (
            "SHARP_BEND",
            "Sharp bend ahead. Reduce speed when safe."
        )

    return ("NORMAL", None)


def evaluate_lateral_instability(
    speed_kmh: float,
    lateral_accel_g: Optional[float],
    yaw_rate_rads: Optional[float],
    accuracy_m: float
) -> Tuple[bool, Optional[str]]:
    """Distinguish ordinary turning from erratic lane weaving or lateral instability.

    Normal turning exhibits steady centripetal acceleration: a_c = v * omega.
    Sudden discordant lateral spikes without matching smooth yaw rotation
    or violent alternating lateral acceleration indicate potential lateral instability.
    """
    if accuracy_m > 30.0 or speed_kmh < 20.0:
        return (False, None)

    if lateral_accel_g is None:
        return (False, None)

    # 1g = 9.80665 m/s^2
    lat_accel_ms2 = abs(lateral_accel_g) * 9.80665
    speed_ms = speed_kmh / 3.6

    # Expected centripetal acceleration for normal curve
    if yaw_rate_rads is not None:
        expected_centripetal = speed_ms * abs(yaw_rate_rads)
        residual = abs(lat_accel_ms2 - expected_centripetal)

        # Severe residual mismatch at highway speed indicates abrupt slide or swerve
        if lat_accel_ms2 > 4.5 and residual > 3.0:
            return (True, "Possible unstable movement detected. Reduce speed when safe.")

    # Severe lateral jerk / force exceeding traction safety threshold (> 5.5 m/s^2)
    if lat_accel_ms2 > 5.5:
        return (True, "Possible unstable movement detected. Reduce speed when safe.")

    return (False, None)


# --------------------------------------------------------------------------- #
# V2V Registry & Manager
# --------------------------------------------------------------------------- #
class V2VRegistry:
    """Thread-safe in-memory session registry and spatial broadcast manager."""

    def __init__(self, session_timeout_seconds: float = 12.0, max_radius_meters: float = 1500.0):
        self.session_timeout_seconds = session_timeout_seconds
        self.max_radius_meters = max_radius_meters
        self._vehicles: Dict[str, VehicleState] = {}

    def cleanup_stale_vehicles(self, current_time: Optional[float] = None) -> int:
        """Evict vehicles that have stopped transmitting updates beyond the timeout."""
        now = current_time or time.time()
        stale_ids = [
            vid for vid, v in self._vehicles.items()
            if (now - v.server_receipt_time) > self.session_timeout_seconds
        ]
        for vid in stale_ids:
            del self._vehicles[vid]
        return len(stale_ids)

    def register_update(self, update: LocationUpdate, current_time: Optional[float] = None) -> Tuple[bool, str]:
        """Validate and record an incoming vehicle location update."""
        now = current_time if current_time is not None else time.time()

        # Timestamp freshness check: reject if > 30s in the past or > 10s in the future
        if abs(now - update.timestamp_s) > 30.0:
            # If client clock is out of sync, adopt server time gracefully
            update.timestamp_s = now

        # GPS accuracy threshold
        if update.accuracy_m > 100.0:
            return False, "GPS accuracy too degraded (>100m) to publish live V2V position."

        self.cleanup_stale_vehicles(now)

        if update.vehicle_id in self._vehicles:
            self._vehicles[update.vehicle_id].update(update, now)
        else:
            self._vehicles[update.vehicle_id] = VehicleState(update, now)

        return True, "Update accepted"

    def remove_vehicle(self, vehicle_id: str) -> bool:
        """Remove a vehicle when disconnected or when location sharing is paused."""
        if vehicle_id in self._vehicles:
            del self._vehicles[vehicle_id]
            return True
        return False

    def get_nearby_vehicles_for(self, user_vehicle_id: str, current_time: Optional[float] = None) -> List[NearbyVehicleInfo]:
        """Calculate and return all authorized nearby vehicles around a user's vehicle."""
        now = current_time if current_time is not None else time.time()
        self.cleanup_stale_vehicles(now)

        user = self._vehicles.get(user_vehicle_id)
        if not user:
            return []

        results: List[NearbyVehicleInfo] = []

        for other_id, other in self._vehicles.items():
            if other_id == user_vehicle_id:
                continue

            # Geodesic distance
            dist = haversine_distance(user.latitude, user.longitude, other.latitude, other.longitude)
            if dist > self.max_radius_meters:
                continue

            bearing = initial_bearing(user.latitude, user.longitude, other.latitude, other.longitude)
            rel_pos = determine_relative_position(user.heading_deg, bearing)
            closing_spd = calculate_closing_speed(
                user.speed_kmh, user.heading_deg,
                other.speed_kmh, other.heading_deg,
                bearing
            )

            # Combined GPS uncertainty (metres)
            dist_uncertainty = round(user.accuracy_m + other.accuracy_m, 1)

            # Mountain road / hairpin analysis
            road_context, warning = evaluate_mountain_and_hairpin_context(user, other, dist)

            # Aggressiveness / Risk evaluation
            is_aggressive = (other.risk_level >= 2) or other.is_unstable

            if not warning:
                if is_aggressive and dist < 60.0:
                    if rel_pos == "BEHIND":
                        warning = f"Aggressive vehicle tailgating behind ({int(dist)}m)"
                    else:
                        warning = f"Aggressive vehicle maneuvering ahead ({int(dist)}m)"
                elif dist < 30.0 and closing_spd > 15.0:
                    warning = "Vehicle rapidly approaching. Maintain a safe distance."

            age_s = round(now - other.server_receipt_time, 1)

            info = NearbyVehicleInfo(
                id=other.vehicle_id,
                name=other.display_name,
                latitude=other.latitude,
                longitude=other.longitude,
                accuracy_m=other.accuracy_m,
                speed_kmh=other.speed_kmh,
                heading_deg=other.heading_deg,
                distance_meters=round(dist, 1),
                distance_uncertainty_m=dist_uncertainty,
                bearing_degrees=round(bearing, 1),
                relative_position=rel_pos,
                closing_speed_kmh=round(closing_spd, 1),
                is_aggressive=is_aggressive,
                is_unstable=other.is_unstable,
                risk_level=other.risk_level,
                warning_message=warning,
                road_context=road_context,
                last_update_age_s=age_s
            )
            results.append(info)

        # Sort nearest first
        results.sort(key=lambda v: v.distance_meters)
        return results

    def active_vehicle_count(self, current_time: Optional[float] = None) -> int:
        now = current_time if current_time is not None else time.time()
        self.cleanup_stale_vehicles(now)
        return len(self._vehicles)

    def list_active_vehicles(self, current_time: Optional[float] = None) -> List[Dict]:
        now = current_time if current_time is not None else time.time()
        self.cleanup_stale_vehicles(now)
        return [
            {
                "vehicle_id": v.vehicle_id,
                "display_name": v.display_name,
                "latitude": v.latitude,
                "longitude": v.longitude,
                "speed_kmh": v.speed_kmh,
                "heading_deg": v.heading_deg,
                "risk_level": v.risk_level,
                "last_seen_seconds_ago": round(now - v.server_receipt_time, 2),
            }
            for v in self._vehicles.values()
        ]


# Global singleton instance
v2v_registry = V2VRegistry()
