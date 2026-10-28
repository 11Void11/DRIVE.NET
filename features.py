"""Safe Cruise -- shared data + feature layer.

Imported by train_full_model.py, main.py and dashboard.py so that training and
serving compute *identical* features. Rolling-window features are the classic
source of train/serve skew, so there is exactly one implementation of them and
it lives here.

Design decisions worth knowing:
  * `Altitude` and `Roll` are removed: altitude fingerprints the route and Roll
    is essentially the constant phone-mount angle (std 0.037 deg dataset-wide).
  * Raw `Yaw` is also removed -- it is an absolute compass heading, which is
    another route fingerprint. Its *variability* (Yaw_std) is behavioural and is
    kept. Flip KEEP_RAW_YAW to compare.
  * Rolling windows are TIME based (`rolling('5s')`), not row based, because the
    stream has real gaps (max observed inter-sample gap: 8.6 s at a nominal
    10 Hz). A row-count window would silently span minutes across a gap.
  * A window must cover at least MIN_COVERAGE of its nominal sample count or the
    feature is NaN. Those "warm-up" rows are dropped in training and reported as
    skipped by the API -- never quietly filled with a bad value.
"""

import glob
import os
import re

import numpy as np
import pandas as pd

# --------------------------------------------------------------------------- #
# Config
# --------------------------------------------------------------------------- #
BASE_PATH = "/Users/akash/Downloads/UAH-DRIVESET-v1"

MODEL_PATH = "safe_cruise_xgboost_model.pkl"
META_PATH = "safe_cruise_model_meta.json"
EVAL_PATH = "safe_cruise_eval.json"
DATASET_CACHE = "safe_cruise_full_dataset.csv.gz"

CLASS_NAMES = ["Low", "Medium", "High"]
CLASS_BEHAVIOURS = ["NORMAL", "DROWSY", "AGGRESSIVE"]
CLASS_COLORS = ["#22c55e", "#f59e0b", "#ef4444"]  # green / orange / red
BEHAVIOR_TO_LABEL = {"NORMAL": 0, "DROWSY": 1, "AGGRESSIVE": 2}

# Static route/device identifiers -- physically removed from the dataset so they
# cannot be fed to the model by accident. Latitude/Longitude/Course are KEPT in
# the dataframe (the dashboard and the Leaflet map need them) but are absent from
# FEATURES, so the model never sees them either.
DROPPED_STATIC = ["Altitude", "Roll"]
KEEP_RAW_YAW = False

NOMINAL_HZ = 10.0
# Measured by grouped CV over all 40 trips (see train_full_model.py output):
#   ("5s","15s")        -> 0.5280 accuracy, 26 features, needs 15 s of history
#   ("5s","15s","60s")  -> 0.5495 accuracy, 34 features, needs 60 s of history
# The 60 s window wins, and matches the 60-second scoring window the UAH paper
# itself uses. Cost: clients must buffer a full minute before the first
# prediction. Drop "60s" from this tuple to go back to a 15 s buffer -- every
# other file derives its window requirement from here automatically.
ROLL_WINDOWS = ("5s", "15s", "60s")
MIN_COVERAGE = 0.5  # fraction of a window's nominal samples required

# Raw columns the client must supply (the API schema is generated from this).
RAW_INPUT_COLS = [
    "Timestamp",
    "Speed_kmh",
    "Accel_X_Filtered",
    "Accel_Y_Filtered",
    "Accel_Z_Filtered",
    "Pitch",
    "Yaw",
]

# Instantaneous features fed to the model.
INSTANT_FEATURES = [
    "Speed_kmh",
    "Accel_X_Filtered",
    "Accel_Y_Filtered",
    "Accel_Z_Filtered",
    "Accel_Magnitude",
    "Pitch",
] + (["Yaw"] if KEEP_RAW_YAW else [])

# Rate of change of acceleration -- captures sudden, snappy inputs.
JERK_FEATURES = ["Jerk_X", "Jerk_Y", "Jerk_Z", "Jerk_Magnitude"]

# (source column, aggregation) applied over every window in ROLL_WINDOWS.
ROLLING_SPECS = [
    ("Speed_kmh", "std"),          # speed variability
    ("Speed_kmh", "mean"),         # sustained pace
    ("Accel_X_Filtered", "std"),   # lateral weaving
    ("Accel_Y_Filtered", "std"),   # accel/brake variability
    ("Yaw", "std"),                # turn-rate variability
    ("Jerk_Magnitude", "mean"),    # sustained snappiness
    ("Jerk_Magnitude", "max"),     # harshest jerk in the window
    ("Accel_Magnitude", "max"),    # harshest combined g in the window
]


def rolling_name(col, agg, window):
    return f"{col}_{agg}_{window}"


ROLLING_FEATURES = [
    rolling_name(col, agg, w) for w in ROLL_WINDOWS for col, agg in ROLLING_SPECS
]

FEATURES = INSTANT_FEATURES + JERK_FEATURES + ROLLING_FEATURES

# Longest window the model relies on -- clients need at least this much history.
MIN_WINDOW_SECONDS = max(float(w.rstrip("s")) for w in ROLL_WINDOWS)

# Column layouts per the DriveSet reader tool (uah_driveset_reader.py:132-175).
ACCEL_COLS = [
    "Timestamp", "Valid_State",
    "Accel_X", "Accel_Y", "Accel_Z",
    "Accel_X_Filtered", "Accel_Y_Filtered", "Accel_Z_Filtered",
    "Roll", "Pitch", "Yaw",
]
GPS_COLS = [
    "Timestamp", "Speed_kmh", "Latitude", "Longitude", "Altitude",
    "Vertical_Accuracy", "Horizontal_Accuracy", "Course", "Difcourse",
    "Position_State", "Lanex_Dist_State", "Lanex_History",
]
MERGE_TOLERANCE_S = 2.0


# --------------------------------------------------------------------------- #
# Feature engineering -- ONE implementation, used by training and serving
# --------------------------------------------------------------------------- #
def engineer_trip(frame):
    """Add jerk + rolling-window features to a single trip / telemetry window.

    Input must contain RAW_INPUT_COLS. Rows are sorted by Timestamp and rows
    with a duplicate timestamp are dropped (a strictly increasing clock is
    required for time-based rolling and for a finite jerk).
    """
    df = frame.sort_values("Timestamp")
    df = df[~df["Timestamp"].duplicated(keep="first")].reset_index(drop=True)

    dt = df["Timestamp"].diff()

    # Jerk = d(acceleration)/dt, per axis.
    for axis in ("X", "Y", "Z"):
        df[f"Jerk_{axis}"] = df[f"Accel_{axis}_Filtered"].diff() / dt
    df["Jerk_Magnitude"] = np.sqrt(
        df["Jerk_X"] ** 2 + df["Jerk_Y"] ** 2 + df["Jerk_Z"] ** 2
    )

    # Combined planar g-force actually felt by the occupants.
    df["Accel_Magnitude"] = np.sqrt(
        df["Accel_X_Filtered"] ** 2 + df["Accel_Y_Filtered"] ** 2
    )

    # Time-indexed rolling windows.
    df.index = pd.to_timedelta(df["Timestamp"], unit="s")
    for window in ROLL_WINDOWS:
        seconds = float(window.rstrip("s"))
        min_periods = max(2, int(seconds * NOMINAL_HZ * MIN_COVERAGE))
        for col, agg in ROLLING_SPECS:
            roller = df[col].rolling(window, min_periods=min_periods)
            df[rolling_name(col, agg, window)] = getattr(roller, agg)()

    df = df.reset_index(drop=True)
    return df.replace([np.inf, -np.inf], np.nan)


def valid_feature_mask(frame):
    """True where every model feature is present and finite."""
    return frame[FEATURES].notna().all(axis=1)


def engineer_dataset(dataset, group_col="Trip_ID", progress=False):
    """Apply engineer_trip() independently per trip -- no cross-trip bleed."""
    out = []
    groups = list(dataset.groupby(group_col, sort=False))
    for i, (trip_id, trip) in enumerate(groups, start=1):
        engineered = engineer_trip(trip)
        out.append(engineered)
        if progress:
            print(f"  [{i:>2}/{len(groups)}] {trip_id}  {len(engineered):,} rows")
    return pd.concat(out, ignore_index=True)


# --------------------------------------------------------------------------- #
# Ingestion
# --------------------------------------------------------------------------- #
def parse_trip_metadata(trip_dir):
    """Folder format: Date-Distance-Driver-Behavior-Road, e.g.
    20151111125233-24km-D1-AGGRESSIVE-MOTORWAY."""
    name = os.path.basename(trip_dir)
    parts = name.split("-")
    upper = name.upper()
    behavior = next((b for b in BEHAVIOR_TO_LABEL if b in upper), None)
    driver = next((p.upper() for p in parts if re.fullmatch(r"[Dd]\d+", p)), "UNKNOWN")
    road = next((r for r in ("MOTORWAY", "SECONDARY") if r in upper), "UNKNOWN")
    return {
        "Trip_ID": name,          # unique per trip: timestamped folder name
        "Driver": driver,
        "Behavior": behavior,
        "Road": road,
    }


def load_trip(accel_path, gps_path, meta):
    """Read one trip and time-align the 10 Hz inertial stream to the 1 Hz GPS."""
    df_accel = pd.read_csv(accel_path, sep=r"\s+", header=None, names=ACCEL_COLS)
    df_gps = pd.read_csv(gps_path, sep=r"\s+", header=None, names=GPS_COLS)

    df_accel = df_accel.sort_values("Timestamp").reset_index(drop=True)
    df_gps = df_gps.sort_values("Timestamp").reset_index(drop=True)

    merged = pd.merge_asof(
        df_accel, df_gps,
        on="Timestamp", direction="backward",
        tolerance=MERGE_TOLERANCE_S, suffixes=("", "_gps"),
    )

    needed = ["Speed_kmh", "Accel_X_Filtered", "Accel_Y_Filtered",
              "Accel_Z_Filtered", "Pitch", "Yaw"]
    merged = merged.dropna(subset=needed)
    merged = merged[merged["Speed_kmh"] >= 0]

    merged["Risk_Level"] = BEHAVIOR_TO_LABEL[meta["Behavior"]]
    for key, value in meta.items():
        merged[key] = value
    return merged.drop(columns=DROPPED_STATIC, errors="ignore")


def build_dataset(base_path=BASE_PATH, verbose=True):
    """Walk the whole DriveSet and concatenate every trip into one frame."""
    pattern = os.path.join(base_path, "**", "RAW_ACCELEROMETERS.txt")
    accel_files = sorted(glob.glob(pattern, recursive=True))
    if not accel_files:
        raise FileNotFoundError(f"No RAW_ACCELEROMETERS.txt found under {base_path}")

    frames, skipped = [], []
    for accel_path in accel_files:
        trip_dir = os.path.dirname(accel_path)
        gps_path = os.path.join(trip_dir, "RAW_GPS.txt")
        meta = parse_trip_metadata(trip_dir)

        if meta["Behavior"] is None:
            skipped.append((meta["Trip_ID"], "no behaviour in folder name"))
            continue
        if not os.path.exists(gps_path):
            skipped.append((meta["Trip_ID"], "missing RAW_GPS.txt"))
            continue
        try:
            frames.append(load_trip(accel_path, gps_path, meta))
        except Exception as exc:
            skipped.append((meta["Trip_ID"], f"{type(exc).__name__}: {exc}"))

    dataset = pd.concat(frames, ignore_index=True)
    if verbose:
        print(f"Ingested {len(frames)}/{len(accel_files)} trips -> {len(dataset):,} rows")
        for trip, why in skipped:
            print(f"  SKIPPED {trip}: {why}")
    return dataset


def load_or_build_dataset(cache=DATASET_CACHE, verbose=True):
    """Read the cached merged dataset if present, otherwise rebuild it."""
    if os.path.exists(cache):
        if verbose:
            print(f"Loading cached dataset {cache}")
        dataset = pd.read_csv(cache)
        if "Trip_ID" in dataset.columns:
            return dataset
        if verbose:
            print("  cache predates Trip_ID -- rebuilding")
    dataset = build_dataset(verbose=verbose)
    dataset.to_csv(cache, index=False, compression="gzip")
    if verbose:
        print(f"Cached -> {cache}")
    return dataset


# --------------------------------------------------------------------------- #
# Inference helpers -- shared by the API and the dashboard
# --------------------------------------------------------------------------- #
_MODEL = None
_EXPLAINER = None


def load_model(model_path=MODEL_PATH):
    """Load the model + SHAP explainer once and reuse them."""
    global _MODEL, _EXPLAINER
    if _MODEL is None:
        import joblib
        import shap
        import xgboost as xgb

        # Check for JSON model first or fallback if pickle fails across environments
        json_path = model_path.replace(".pkl", ".json")
        loaded_model = None
        if os.path.exists(json_path):
            try:
                m = xgb.XGBClassifier()
                m.load_model(json_path)
                loaded_model = m
            except Exception:
                pass

        if loaded_model is None and os.path.exists(model_path):
            try:
                loaded_model = joblib.load(model_path)
            except Exception as e:
                if os.path.exists(json_path):
                    m = xgb.XGBClassifier()
                    m.load_model(json_path)
                    loaded_model = m
                else:
                    raise e

        if loaded_model is not None:
            _MODEL = loaded_model
            try:
                _EXPLAINER = shap.Explainer(_MODEL)
            except Exception:
                _EXPLAINER = None

    return _MODEL, _EXPLAINER


def model_feature_order(model):
    """Trust the fitted model's own column order over the constant above."""
    names = getattr(model, "feature_names_in_", None)
    return [str(n) for n in names] if names is not None else list(FEATURES)


def score_window(frame, with_shap=True, model_path=MODEL_PATH):
    """Engineer -> predict -> explain one telemetry window or trip.

    Returns (scored, skipped_count) where `scored` is the subset of rows that had
    a fully-populated feature vector, with these columns added:
        Predicted_Risk, Risk_Label, Proba_Low/Medium/High, and (optionally)
        SHAP_<feature> for the predicted class.

    SHAP over a whole trip costs a few seconds; pass with_shap=False and call
    explain_rows() on the handful of rows you actually want to explain.
    """
    model, _ = load_model(model_path)
    order = model_feature_order(model)

    engineered = engineer_trip(frame)
    mask = valid_feature_mask(engineered)
    skipped = int((~mask).sum())
    scored = engineered[mask].copy()
    if scored.empty:
        return scored, skipped

    X = scored[order].astype("float64")
    predictions = model.predict(X).astype(int)
    probabilities = model.predict_proba(X)

    scored["Predicted_Risk"] = predictions
    scored["Risk_Label"] = [CLASS_NAMES[p] for p in predictions]
    for i, cls in enumerate(model.classes_):
        scored[f"Proba_{CLASS_NAMES[int(cls)]}"] = probabilities[:, i]

    if with_shap:
        scored = explain_rows(scored, model_path=model_path)
    return scored, skipped


def explain_rows(scored, model_path=MODEL_PATH):
    """Attach SHAP_<feature> columns for each row's predicted class.

    Input rows must already carry the engineered FEATURES and Predicted_Risk --
    never re-engineer a subset of a trip, because rolling features depend on the
    surrounding samples and would come out different.
    """
    model, explainer = load_model(model_path)
    order = model_feature_order(model)
    out = scored.copy()

    X = out[order].astype("float64")
    values = explainer(X, check_additivity=False).values
    class_order = [int(c) for c in model.classes_]
    picked = np.array([class_order.index(int(p)) for p in out["Predicted_Risk"]])
    per_row = values[np.arange(len(picked)), :, picked]
    for j, name in enumerate(order):
        out[f"SHAP_{name}"] = per_row[:, j]
    return out


def risk_segments(scored, time_col="Timestamp", risk_col="Predicted_Risk"):
    """Run-length encode consecutive equal predictions into coloured spans.

    Turns ~7,000 per-sample predictions into a few hundred (start, end, risk)
    bands -- what a readable risk timeline actually needs.
    """
    if scored.empty:
        return pd.DataFrame(columns=["start", "end", "risk", "label", "duration"])

    frame = scored[[time_col, risk_col]].reset_index(drop=True)
    change = frame[risk_col].ne(frame[risk_col].shift()).cumsum()
    rows = []
    for _, block in frame.groupby(change, sort=True):
        start = float(block[time_col].iloc[0])
        end = float(block[time_col].iloc[-1])
        risk = int(block[risk_col].iloc[0])
        rows.append({
            "start": start,
            # give a single-sample block a visible width
            "end": end if end > start else start + 1.0 / NOMINAL_HZ,
            "risk": risk,
            "label": CLASS_NAMES[risk],
            "duration": max(end - start, 1.0 / NOMINAL_HZ),
        })
    return pd.DataFrame(rows)
