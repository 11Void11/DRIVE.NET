"""Drive.Net -- UAH-DriveSet Reproducible Ingestion, Semantic Parsing, Model Training & Benchmarking.

Ground-truth dataset: UAH-DriveSet (Romera et al., IEEE ITSC 2016).
Naturalistic driving data collected from 6 drivers performing Normal, Drowsy,
and Aggressive behaviors on motorways and secondary roads (~500 minutes total).

Implements:
1. SEMANTIC_ONLINE.txt and SEMANTIC_FINAL.txt format parsers adhering to official spec.
2. Robust feature engineering (jerk, acceleration magnitude, rolling stats: 5s, 15s, 60s).
3. Leak-free evaluation: Leave-One-Driver-Out CV and Stratified Group K-Fold on Trip_ID.
4. Baseline comparison: Logistic Regression, Random Forest, and XGBoost.
5. Class-wise precision, recall, F1, confusion matrices, and model export (JSON + PKL).
"""

from __future__ import annotations

import json
import os
import sys
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, f1_score
from sklearn.model_selection import LeaveOneGroupOut, StratifiedGroupKFold
import joblib
import xgboost as xgb

import features as F

# --------------------------------------------------------------------------- #
# UAH-DriveSet Semantic Schema Specifications
# --------------------------------------------------------------------------- #
SEMANTIC_ONLINE_COLUMNS = [
    "Timestamp",              # 1. TimeStamp since route start (seconds)
    "Latitude",               # 2. GPS Latitude (degrees)
    "Longitude",              # 3. GPS Longitude (degrees)
    "Score_Total_Window",     # 4. Score total WINDOW (base 100)
    "Score_Accel_Window",     # 5. Score accelerations WINDOW (base 100)
    "Score_Brake_Window",     # 6. Score brakings WINDOW (base 100)
    "Score_Turn_Window",      # 7. Score turnings WINDOW (base 100)
    "Score_Weave_Window",     # 8. Score weaving WINDOW (base 100)
    "Score_Drift_Window",     # 9. Score drifting WINDOW (base 100)
    "Score_Overspeed_Window", # 10. Score overspeeding WINDOW (base 100)
    "Score_Follow_Window",    # 11. Score car-following WINDOW (base 100)
    "Ratio_Normal_Window",    # 12. Ratio normal WINDOW (base 1)
    "Ratio_Drowsy_Window",    # 13. Ratio drowsy WINDOW (base 1)
    "Ratio_Aggressive_Window",# 14. Ratio aggressive WINDOW (base 1)
    "Ratio_Distracted_Window",# 15. Ratio distracted WINDOW (last 2s, 0 or 1)
    "Score_Total_Cumul",      # 16. Score total cumulative (base 100)
    "Score_Accel_Cumul",      # 17. Score accelerations cumulative (base 100)
    "Score_Brake_Cumul",      # 18. Score brakings cumulative (base 100)
    "Score_Turn_Cumul",       # 19. Score turnings cumulative (base 100)
    "Score_Weave_Cumul",      # 20. Score weaving cumulative (base 100)
    "Score_Drift_Cumul",      # 21. Score drifting cumulative (base 100)
    "Score_Overspeed_Cumul",  # 22. Score overspeeding cumulative (base 100)
    "Score_Follow_Cumul",     # 23. Score car-following cumulative (base 100)
    "Ratio_Normal_Cumul",     # 24. Ratio normal cumulative (base 1)
    "Ratio_Drowsy_Cumul",     # 25. Ratio drowsy cumulative (base 1)
    "Ratio_Aggressive_Cumul", # 26. Ratio aggressive cumulative (base 1)
    "Ratio_Distracted_Cumul", # 27. Ratio distracted cumulative (base 1)
]

SEMANTIC_FINAL_LINE_KEYS = [
    "Start_Hour", "Start_Minute", "Start_Second", "Avg_Speed_kmh", "Max_Speed_kmh",
    "Lanex_Score", "Driving_Time_Min", "End_Hour", "End_Minute", "End_Second",
    "Trip_Distance_km", "Score_Long_Dist", "Score_Tran_Dist", "Score_Speed_Dist",
    "Score_Global_Old", "Alerts_Long", "Alerts_Late", "Alerts_Lanex",
    "Num_Stops", "Speed_Variability", "Accel_Noise", "Kinetic_Energy",
    "Driving_Time_Sec", "Num_Curves", "Power_Exerted", "Accel_Events",
    "Braking_Events", "Turning_Events", "Long_Distract_Score", "Trans_Distract_Score",
    "Mean_Long_Distract", "Std_Long_Distract", "Avg_Trans_Distract", "Std_Trans_Distract",
    "Low_Accel_Count", "Med_Accel_Count", "High_Accel_Count",
    "Low_Brake_Count", "Med_Brake_Count", "High_Brake_Count",
    "Low_Turn_Count", "Med_Turn_Count", "High_Turn_Count",
    "Score_Total_Final", "Score_Accel_Final", "Score_Brake_Final", "Score_Turn_Final",
    "Score_Weave_Final", "Score_Drift_Final", "Score_Overspeed_Final", "Score_Follow_Final",
    "Ratio_Normal_Final", "Ratio_Drowsy_Final", "Ratio_Aggressive_Final"
]


def parse_semantic_online(file_path: str) -> pd.DataFrame:
    """Parse SEMANTIC_ONLINE.txt according to the 27 column specification."""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Missing {file_path}")
    df = pd.read_csv(file_path, sep=r"\s+", header=None, names=SEMANTIC_ONLINE_COLUMNS)
    df = df.replace([np.inf, -np.inf], np.nan).dropna(subset=["Timestamp", "Score_Total_Window"])
    return df


def parse_semantic_final(file_path: str) -> Dict[str, float]:
    """Parse SEMANTIC_FINAL.txt according to the 54 line structure."""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Missing {file_path}")
    with open(file_path, "r") as f:
        lines = [line.strip() for line in f if line.strip()]
    result = {}
    for i, key in enumerate(SEMANTIC_FINAL_LINE_KEYS):
        if i < len(lines):
            try:
                result[key] = float(lines[i])
            except ValueError:
                result[key] = np.nan
    return result


# --------------------------------------------------------------------------- #
# Training & Benchmarking Pipeline
# --------------------------------------------------------------------------- #
def train_and_benchmark(
    dataset_path: str = F.DATASET_CACHE,
    save_model_path: str = F.MODEL_PATH,
    save_meta_path: str = F.META_PATH,
    save_eval_path: str = F.EVAL_PATH
):
    print("=" * 80)
    print("Drive.Net Machine Learning Pipeline -- Training & Benchmarking")
    print("=" * 80)

    # 1. Load dataset
    print(f"Loading dataset from: {dataset_path}")
    raw_df = pd.read_csv(dataset_path)
    print(f"Loaded {len(raw_df):,} raw records from {raw_df['Trip_ID'].nunique()} trips across {raw_df['Driver'].nunique()} drivers.")

    # 2. Engineer temporal rolling features per trip
    print(f"Engineering temporal jerk and rolling-window features (windows: {F.ROLL_WINDOWS})...")
    engineered = F.engineer_dataset(raw_df, group_col="Trip_ID", progress=False)

    valid_mask = F.valid_feature_mask(engineered)
    engineered = engineered[valid_mask].reset_index(drop=True)
    print(f"Retained {len(engineered):,} valid samples after rolling warm-up filtering.")

    X = engineered[F.FEATURES].astype("float32")
    y = engineered["Risk_Level"].astype(int)
    groups_trip = engineered["Trip_ID"].astype(str)
    groups_driver = engineered["Driver"].astype(str)

    # 3. Model definitions for comparison
    models = {
        "LogisticRegression": LogisticRegression(max_iter=1000, random_state=42),
        "RandomForest": RandomForestClassifier(n_estimators=100, max_depth=10, random_state=42, n_jobs=-1),
        "XGBoost": xgb.XGBClassifier(
            objective="multi:softprob",
            n_estimators=300,
            max_depth=6,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            min_child_weight=8,
            reg_lambda=2.0,
            tree_method="hist",
            eval_metric="mlogloss",
            n_jobs=-1,
            random_state=42
        )
    }

    # 4. Benchmarking on Stratified Group K-Fold (Trip-disjoint)
    print("\n" + "-" * 80)
    print("Cross-Validation Benchmark (5-Fold StratifiedGroupKFold on Trip_ID):")
    print("-" * 80)

    sgkf = StratifiedGroupKFold(n_splits=5)
    bench_results = {}

    for name, model in models.items():
        fold_accs = []
        fold_f1s = []
        for fold, (train_idx, val_idx) in enumerate(sgkf.split(X, y, groups_trip)):
            X_tr, y_tr = X.iloc[train_idx], y.iloc[train_idx]
            X_val, y_val = X.iloc[val_idx], y.iloc[val_idx]
            model.fit(X_tr, y_tr)
            preds = model.predict(X_val)
            acc = accuracy_score(y_val, preds)
            f1 = f1_score(y_val, preds, average="macro", zero_division=0)
            fold_accs.append(acc)
            fold_f1s.append(f1)
        mean_acc = float(np.mean(fold_accs))
        std_acc = float(np.std(fold_accs))
        mean_f1 = float(np.mean(fold_f1s))
        bench_results[name] = {"accuracy": mean_acc, "acc_std": std_acc, "f1_macro": mean_f1}
        print(f"  {name:<20}: Accuracy = {mean_acc:.4f} (+/- {std_acc:.4f}), F1-Macro = {mean_f1:.4f}")

    # 5. Independent Driver-Level Validation (Leave-One-Driver-Out CV)
    print("\n" + "-" * 80)
    print("Strict Unseen Driver Validation (Leave-One-Driver-Out on Driver ID):")
    print("-" * 80)
    logo = LeaveOneGroupOut()
    driver_scores = {}
    best_xgb = models["XGBoost"]

    all_y_true = []
    all_y_pred = []

    for train_idx, test_idx in logo.split(X, y, groups_driver):
        test_driver = groups_driver.iloc[test_idx].iloc[0]
        X_tr, y_tr = X.iloc[train_idx], y.iloc[train_idx]
        X_te, y_te = X.iloc[test_idx], y.iloc[test_idx]

        best_xgb.fit(X_tr, y_tr)
        preds = best_xgb.predict(X_te)
        acc = accuracy_score(y_te, preds)
        driver_scores[test_driver] = float(acc)
        all_y_true.extend(y_te.tolist())
        all_y_pred.extend(preds.tolist())
        print(f"  Held-out Driver {test_driver:<5}: Accuracy = {acc:.4f} ({len(y_te):,} test rows)")

    lodo_acc = accuracy_score(all_y_true, all_y_pred)
    lodo_cm = confusion_matrix(all_y_true, all_y_pred, labels=[0, 1, 2])
    print(f"\nOverall Leave-One-Driver-Out Accuracy: {lodo_acc:.4f}")
    print("\nClassification Report (Unseen Drivers):")
    print(classification_report(all_y_true, all_y_pred, target_names=F.CLASS_NAMES, digits=4, zero_division=0))

    # 6. Fit final production model on full validated engineered dataset
    print("\nFitting final production model on full feature dataset...")
    final_model = xgb.XGBClassifier(
        objective="multi:softprob",
        n_estimators=350,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        min_child_weight=8,
        reg_lambda=2.0,
        tree_method="hist",
        eval_metric="mlogloss",
        n_jobs=-1,
        random_state=42
    )
    final_model.fit(X, y)

    # 7. Save model and metadata (Both JSON and PKL formats)
    json_model_path = save_model_path.replace(".pkl", ".json")
    final_model.save_model(json_model_path)
    joblib.dump(final_model, save_model_path)
    print(f"Saved model -> {save_model_path} and {json_model_path}")

    # Feature importances
    importances = sorted(
        zip(F.FEATURES, final_model.feature_importances_.tolist()),
        key=lambda kv: kv[1],
        reverse=True
    )

    eval_data = {
        "model_name": "Drive.Net XGBoost Risk Classifier",
        "benchmark_comparison": bench_results,
        "leave_one_driver_out": {
            "overall_accuracy": lodo_acc,
            "per_driver_accuracy": driver_scores,
            "confusion_matrix": lodo_cm.tolist()
        },
        "features": F.FEATURES,
        "class_names": F.CLASS_NAMES,
        "top_features": importances[:12]
    }

    with open(save_eval_path, "w") as f:
        json.dump(eval_data, f, indent=2)

    with open(save_meta_path, "w") as f:
        json.dump({
            "features": F.FEATURES,
            "instant_features": F.INSTANT_FEATURES,
            "jerk_features": F.JERK_FEATURES,
            "rolling_features": F.ROLLING_FEATURES,
            "raw_input_cols": F.RAW_INPUT_COLS,
            "roll_windows": list(F.ROLL_WINDOWS),
            "min_window_seconds": F.MIN_WINDOW_SECONDS,
            "class_names": F.CLASS_NAMES,
            "class_behaviours": F.CLASS_BEHAVIOURS,
            "n_training_rows": len(X),
            "json_model_path": json_model_path
        }, f, indent=2)

    print(f"Saved eval report -> {save_eval_path}")
    print(f"Saved model metadata -> {save_meta_path}")
    print("=" * 80)
    print("Training & Benchmarking Pipeline Complete.")
    print("=" * 80)


if __name__ == "__main__":
    train_and_benchmark()
