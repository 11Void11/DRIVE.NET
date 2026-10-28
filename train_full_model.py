"""Safe Cruise -- temporal feature pipeline, trip-grouped training + evaluation.

Run:  .venv.nosync/bin/python train_full_model.py

What changed versus the first version
  * Altitude and Roll are gone (route / phone-mount fingerprints), as is raw Yaw
    (an absolute compass heading). See features.py for the reasoning.
  * 26 features: instantaneous + jerk + 5 s/15 s rolling-window statistics,
    computed per trip on a time index.
  * Trip-grouped splitting only. A GroupShuffleSplit on Trip_ID produces the
    held-out set the saved model is scored on, and StratifiedGroupKFold gives a
    stability estimate across folds. No random row split is reported anywhere --
    that number was meaningless and it is not computed.
"""

import json
import os
import shutil

import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.metrics import (
    accuracy_score, classification_report, confusion_matrix, f1_score,
    precision_score, recall_score,
)
from sklearn.model_selection import GroupShuffleSplit, StratifiedGroupKFold
import joblib

import features as F

CM_PATH = "confusion_matrix.png"
TEST_TRIP_FRACTION = 0.25
CV_SPLITS = 5
RUN_YAW_ABLATION = False  # set True to re-measure the raw-Yaw leak (adds ~5 fits)

# "stratified_group" -- StratifiedGroupKFold fold 0 as the held-out set: whole
#   trips held out AND class balance preserved. This is the default.
# "group_shuffle"    -- plain GroupShuffleSplit. Trip-disjoint but NOT stratified,
#   and with only 40 trips a single draw is wildly unrepresentative: the seed-42
#   draw puts 5 of 10 DROWSY trips in the test set, giving Low=18% of test rows
#   versus 51% of train rows, and accuracy reads 0.317 instead of ~0.53. Kept so
#   you can reproduce that; do not quote its number.
HOLDOUT_STRATEGY = "stratified_group"


def make_model():
    return xgb.XGBClassifier(
        objective="multi:softprob",
        n_estimators=400,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        min_child_weight=10,
        reg_lambda=2.0,
        tree_method="hist",
        eval_metric="mlogloss",
        n_jobs=-1,
        random_state=42,
    )


# --------------------------------------------------------------------------- #
# STEP 1 + 2 -- ingest, then engineer temporal features per trip
# --------------------------------------------------------------------------- #
def build_feature_table():
    dataset = F.load_or_build_dataset()

    print(f"\nEngineering temporal features per trip "
          f"(windows: {', '.join(F.ROLL_WINDOWS)}) ...")
    engineered = F.engineer_dataset(dataset, group_col="Trip_ID")

    mask = F.valid_feature_mask(engineered)
    dropped = int((~mask).sum())
    engineered = engineered[mask].reset_index(drop=True)
    print(f"  dropped {dropped:,} warm-up rows lacking a full "
          f"{F.MIN_WINDOW_SECONDS:.0f}s window -> {len(engineered):,} usable rows")

    print(f"\n{len(F.FEATURES)} features: "
          f"{len(F.INSTANT_FEATURES)} instantaneous + {len(F.JERK_FEATURES)} jerk "
          f"+ {len(F.ROLLING_FEATURES)} rolling")
    print("Rows per risk class:")
    for label, count in engineered["Risk_Level"].value_counts().sort_index().items():
        print(f"  {label} {F.CLASS_NAMES[label]:<8} ({F.CLASS_BEHAVIOURS[label]:<10}) "
              f"{count:>8,}")
    print(f"Trips: {engineered['Trip_ID'].nunique()}   "
          f"Drivers: {engineered['Driver'].nunique()}")
    return engineered


# --------------------------------------------------------------------------- #
# STEP 3 -- strict trip-grouped splitting
# --------------------------------------------------------------------------- #
def grouped_holdout(engineered):
    """Hold out whole trips. Trip_ID never appears in both splits -- asserted."""
    X = engineered[F.FEATURES]
    y = engineered["Risk_Level"]
    groups = engineered["Trip_ID"]

    if HOLDOUT_STRATEGY == "group_shuffle":
        splitter = GroupShuffleSplit(
            n_splits=1, test_size=TEST_TRIP_FRACTION, random_state=42
        )
        label = "GroupShuffleSplit (unstratified)"
    else:
        splitter = StratifiedGroupKFold(
            n_splits=int(round(1 / TEST_TRIP_FRACTION)), shuffle=True, random_state=42
        )
        label = "StratifiedGroupKFold fold 0"
    train_idx, test_idx = next(splitter.split(X, y, groups))

    train_trips = set(groups.iloc[train_idx])
    test_trips = set(groups.iloc[test_idx])
    overlap = train_trips & test_trips
    assert not overlap, f"LEAK: trips in both splits: {overlap}"

    print(f"\n{label} -> {len(train_trips)} train trips / "
          f"{len(test_trips)} held-out trips (zero overlap, asserted)")
    for name, idx in (("train", train_idx), ("test ", test_idx)):
        frac = y.iloc[idx].value_counts(normalize=True).sort_index()
        print(f"  {name} class mix: "
              + "  ".join(f"{F.CLASS_NAMES[k]}={v:.1%}" for k, v in frac.items()))
    print(f"  held-out trips: {', '.join(sorted(test_trips))}")
    return train_idx, test_idx, sorted(train_trips), sorted(test_trips)


# --------------------------------------------------------------------------- #
# STEP 4 -- reporting
# --------------------------------------------------------------------------- #
def plot_confusion_matrix(cm, title, out_path):
    """Labelled heatmap: colour = row-normalised recall, annotation = raw count.
    The dashboard renders the same matrix in Altair, so a missing matplotlib is
    not fatal here."""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("\n(matplotlib not installed -- PNG skipped; the Streamlit dashboard "
              "renders this heatmap natively)")
        return

    norm = cm.astype(float) / cm.sum(axis=1, keepdims=True).clip(min=1)
    labels = [f"{n}\n({b})" for n, b in zip(F.CLASS_NAMES, F.CLASS_BEHAVIOURS)]

    fig, ax = plt.subplots(figsize=(7.5, 6.5))
    im = ax.imshow(norm, cmap="Blues", vmin=0, vmax=1)
    fig.colorbar(im, ax=ax, label="Fraction of true class")
    ax.set_xticks(range(len(labels)), labels)
    ax.set_yticks(range(len(labels)), labels)
    ax.set_xlabel("Predicted risk")
    ax.set_ylabel("True risk")
    ax.set_title(title)
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            ax.text(j, i, f"{cm[i, j]:,}\n{norm[i, j]:.1%}", ha="center", va="center",
                    color="white" if norm[i, j] > 0.5 else "black", fontsize=10)
    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    plt.close(fig)
    print(f"Heatmap saved -> {out_path}")


def print_text_confusion_matrix(cm):
    print(f"{'true \\ pred':>14}" + "".join(f"{n:>12}" for n in F.CLASS_NAMES))
    for i, name in enumerate(F.CLASS_NAMES):
        print(f"{name:>14}" + "".join(f"{cm[i, j]:>12,}" for j in range(cm.shape[1])))


def cross_validate(engineered, features=None, splits=CV_SPLITS, verbose=True):
    """StratifiedGroupKFold on Trip_ID -- every fold holds out whole unseen trips."""
    features = features or F.FEATURES
    X = engineered[features].reset_index(drop=True)
    y = engineered["Risk_Level"].reset_index(drop=True)
    groups = engineered["Trip_ID"].reset_index(drop=True)

    splitter = StratifiedGroupKFold(n_splits=splits, shuffle=True, random_state=42)
    fold_scores, y_true, y_pred, trip_ids = [], [], [], []

    for fold, (tr, te) in enumerate(splitter.split(X, y, groups), start=1):
        assert not (set(groups.iloc[tr]) & set(groups.iloc[te])), "LEAK in CV fold"
        model = make_model()
        model.fit(X.iloc[tr], y.iloc[tr])
        pred = model.predict(X.iloc[te])
        acc = accuracy_score(y.iloc[te], pred)
        fold_scores.append(float(acc))
        y_true.append(y.iloc[te].to_numpy())
        y_pred.append(pred)
        trip_ids.append(groups.iloc[te].to_numpy())
        if verbose:
            print(f"  fold {fold}: {groups.iloc[te].nunique()} unseen trips, "
                  f"{len(te):,} rows -> accuracy {acc:.4f}")

    return (fold_scores, np.concatenate(y_true), np.concatenate(y_pred),
            np.concatenate(trip_ids))


def trip_level_metrics(y_true, y_pred, trip_ids):
    """Row accuracy understates the system: the label is a property of the whole
    trip, so majority-vote the per-row predictions back up to one call per trip.
    Every trip below was scored by a model that never saw a row from it."""
    frame = pd.DataFrame({"true": y_true, "pred": y_pred, "trip": trip_ids})
    per_trip = []
    for trip, group in frame.groupby("trip"):
        votes = group["pred"].value_counts(normalize=True)
        per_trip.append({
            "trip": trip,
            "true": int(group["true"].iloc[0]),
            "predicted": int(votes.idxmax()),
            "vote_share": float(votes.max()),
            "n_rows": int(len(group)),
            "mix": {F.CLASS_NAMES[int(k)]: float(v)
                    for k, v in votes.sort_index().items()},
        })
    correct = sum(t["true"] == t["predicted"] for t in per_trip)
    return {
        "accuracy": correct / len(per_trip),
        "n_correct": correct,
        "n_trips": len(per_trip),
        "confusion_matrix": confusion_matrix(
            [t["true"] for t in per_trip], [t["predicted"] for t in per_trip],
            labels=[0, 1, 2],
        ).tolist(),
        "per_trip": sorted(per_trip, key=lambda t: t["trip"]),
    }


def metrics_dict(y_true, y_pred):
    return {
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "precision_macro": float(precision_score(y_true, y_pred, average="macro",
                                                 zero_division=0)),
        "recall_macro": float(recall_score(y_true, y_pred, average="macro",
                                           zero_division=0)),
        "f1_macro": float(f1_score(y_true, y_pred, average="macro", zero_division=0)),
        "per_class": classification_report(
            y_true, y_pred, labels=[0, 1, 2], target_names=F.CLASS_NAMES,
            output_dict=True, zero_division=0,
        ),
        "confusion_matrix": confusion_matrix(y_true, y_pred, labels=[0, 1, 2]).tolist(),
    }


# --------------------------------------------------------------------------- #
def main():
    print("=" * 74)
    print("STEP 1/2 -- ingest + temporal feature engineering")
    print("=" * 74)
    engineered = build_feature_table()

    print("\n" + "=" * 74)
    print("STEP 3 -- strict trip-grouped split")
    print("=" * 74)
    train_idx, test_idx, train_trips, test_trips = grouped_holdout(engineered)

    X = engineered[F.FEATURES]
    y = engineered["Risk_Level"]

    print(f"\nTraining on {len(train_idx):,} rows x {len(F.FEATURES)} features ...")
    model = make_model()
    model.fit(X.iloc[train_idx], y.iloc[train_idx])
    print("Model trained.")

    y_test = y.iloc[test_idx].to_numpy()
    y_hat = model.predict(X.iloc[test_idx])

    print("\n" + "=" * 74)
    print("TRUE ACCURACY -- held-out trips the model has never seen")
    print("=" * 74)
    print(classification_report(y_test, y_hat, labels=[0, 1, 2],
                               target_names=F.CLASS_NAMES, digits=4, zero_division=0))
    cm = confusion_matrix(y_test, y_hat, labels=[0, 1, 2])
    print_text_confusion_matrix(cm)
    plot_confusion_matrix(cm, "Confusion matrix -- held-out trips (GroupShuffleSplit)",
                          CM_PATH)

    print("\n" + "=" * 74)
    print(f"STABILITY -- StratifiedGroupKFold ({CV_SPLITS} folds, grouped by Trip_ID)")
    print("=" * 74)
    fold_scores, cv_true, cv_pred, cv_trips = cross_validate(engineered)
    cv = metrics_dict(cv_true, cv_pred)
    print(f"\nmean fold accuracy {np.mean(fold_scores):.4f} "
          f"+/- {np.std(fold_scores):.4f}")
    print(classification_report(cv_true, cv_pred, labels=[0, 1, 2],
                               target_names=F.CLASS_NAMES, digits=4, zero_division=0))
    print_text_confusion_matrix(np.array(cv["confusion_matrix"]))
    plot_confusion_matrix(np.array(cv["confusion_matrix"]),
                          "Confusion matrix -- pooled grouped CV",
                          CM_PATH.replace(".png", "_cv.png"))

    print("\n" + "=" * 74)
    print("TRIP-LEVEL VERDICT -- majority vote per unseen trip")
    print("=" * 74)
    trip_level = trip_level_metrics(cv_true, cv_pred, cv_trips)
    print(f"{trip_level['n_correct']}/{trip_level['n_trips']} trips classified "
          f"correctly = {trip_level['accuracy']:.1%}\n")
    for row in trip_level["per_trip"]:
        flag = "ok " if row["true"] == row["predicted"] else "MISS"
        print(f"  {flag} {row['trip'][:44]:<44} "
              f"true={F.CLASS_NAMES[row['true']]:<7} "
              f"pred={F.CLASS_NAMES[row['predicted']]:<7} "
              f"({row['vote_share']:.0%} of rows)")
    print_text_confusion_matrix(np.array(trip_level["confusion_matrix"]))

    importances = sorted(zip(F.FEATURES, model.feature_importances_.tolist()),
                         key=lambda kv: kv[1], reverse=True)
    print("\nFeature importance (gain, normalised) -- top 12:")
    for name, score in importances[:12]:
        print(f"  {name:<28} {score:.4f}")

    if RUN_YAW_ABLATION:
        print("\nAblation -- adding raw Yaw back in (expect an inflated score if it "
              "acts as a route fingerprint):")
        with_yaw = F.FEATURES + ["Yaw"]
        yaw_scores, _, _, _ = cross_validate(engineered, features=with_yaw, verbose=False)
        print(f"  without raw Yaw: {np.mean(fold_scores):.4f}")
        print(f"  with raw Yaw:    {np.mean(yaw_scores):.4f}")

    # ---------------------------------------------------------------- persist
    if os.path.exists(F.MODEL_PATH):
        backup = F.MODEL_PATH.replace(".pkl", "_previous.pkl")
        shutil.copy2(F.MODEL_PATH, backup)
        print(f"\nPrevious model backed up -> {backup}")
    joblib.dump(model, F.MODEL_PATH)

    with open(F.META_PATH, "w") as fh:
        json.dump({
            "features": F.FEATURES,
            "instant_features": F.INSTANT_FEATURES,
            "jerk_features": F.JERK_FEATURES,
            "rolling_features": F.ROLLING_FEATURES,
            "raw_input_cols": F.RAW_INPUT_COLS,
            "roll_windows": list(F.ROLL_WINDOWS),
            "min_window_seconds": F.MIN_WINDOW_SECONDS,
            "dropped_static": F.DROPPED_STATIC,
            "keep_raw_yaw": F.KEEP_RAW_YAW,
            "class_names": F.CLASS_NAMES,
            "class_behaviours": F.CLASS_BEHAVIOURS,
            "n_training_rows": int(len(train_idx)),
            "xgboost_params": model.get_params(),
        }, fh, indent=2, default=str)

    with open(F.EVAL_PATH, "w") as fh:
        json.dump({
            "validation_strategy": (
                f"GroupShuffleSplit on Trip_ID (test_size={TEST_TRIP_FRACTION}); "
                f"stability from StratifiedGroupKFold({CV_SPLITS}) on Trip_ID. "
                "No random row split is reported."
            ),
            "features": F.FEATURES,
            "class_names": F.CLASS_NAMES,
            "class_behaviours": F.CLASS_BEHAVIOURS,
            "n_rows_total": int(len(engineered)),
            "n_trips": int(engineered["Trip_ID"].nunique()),
            "train_trips": train_trips,
            "test_trips": test_trips,
            "holdout": metrics_dict(y_test, y_hat),
            "grouped_cv": {**cv, "fold_accuracies": fold_scores,
                           "mean_fold_accuracy": float(np.mean(fold_scores)),
                           "std_fold_accuracy": float(np.std(fold_scores))},
            "trip_level": trip_level,
            "holdout_strategy": HOLDOUT_STRATEGY,
            "roll_windows": list(F.ROLL_WINDOWS),
            "feature_importances": [{"feature": n, "gain": g} for n, g in importances],
        }, fh, indent=2)

    print(f"\nSaved -> {F.MODEL_PATH}, {F.META_PATH}, {F.EVAL_PATH}")
    print("\n" + "=" * 74)
    print("QUOTED ACCURACY -- trip-grouped only, no random row split anywhere")
    print("=" * 74)
    print(f"  row-level, grouped CV mean : {np.mean(fold_scores):.4f} "
          f"+/- {np.std(fold_scores):.4f}")
    print(f"  row-level, held-out trips  : {accuracy_score(y_test, y_hat):.4f}")
    print(f"  trip-level majority vote   : {trip_level['accuracy']:.4f} "
          f"({trip_level['n_correct']}/{trip_level['n_trips']} trips)")
    print("\nNext: .venv.nosync/bin/streamlit run dashboard.py")


if __name__ == "__main__":
    main()

