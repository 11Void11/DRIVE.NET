"""Safe Cruise -- temporal risk dashboard.

Run:  .venv.nosync/bin/streamlit run dashboard.py

Requires the artefacts written by train_full_model.py:
    safe_cruise_xgboost_model.pkl
    safe_cruise_model_meta.json
    safe_cruise_eval.json
    safe_cruise_full_dataset.csv.gz

Every number shown here comes from trip-grouped validation. The trip simulator
defaults to trips that were held out of training, and labels any trip the model
was trained on so a demo can never accidentally show memorised data.
"""

import inspect
import json
import os

import altair as alt
import numpy as np
import pandas as pd
import streamlit as st

import features as F

# Trips are ~7,000 samples at 10 Hz; Altair chokes past 5,000 rows and a chart is
# only ~1,000 px wide, so plot a downsample. Predictions are always computed on
# the full-rate signal -- only the drawing is thinned.
PLOT_MAX_POINTS = 1800

BG = "#0a0b0d"
PANEL = "#12141a"
BORDER = "#23262e"
TEXT = "#e9ebee"
MUTED = "#868d99"
GRID = "#1c1f26"
ACCENT = "#60a5fa"
RISK_COLORS = ["#22c55e", "#f59e0b", "#ef4444"]  # Low / Medium / High

st.set_page_config(
    page_title="Safe Cruise",
    page_icon="◉",
    layout="wide",
    initial_sidebar_state="expanded",
)

# --------------------------------------------------------------------------- #
# Chrome removal + dark minimalist skin
# --------------------------------------------------------------------------- #
st.markdown(
    f"""
    <style>
      #MainMenu, header[data-testid="stHeader"], footer,
      [data-testid="stToolbar"], [data-testid="stDecoration"],
      [data-testid="stStatusWidget"] {{ display: none !important; }}

      .stApp, [data-testid="stAppViewContainer"] {{ background: {BG}; }}
      [data-testid="stSidebar"] {{
          background: {PANEL}; border-right: 1px solid {BORDER};
      }}
      .block-container {{ padding: 2.1rem 2.4rem 4rem; max-width: 1500px; }}

      html, body, [class*="css"], .stApp {{
          font-family: "Inter", "SF Pro Display", -apple-system, "Segoe UI",
                       Helvetica, Arial, sans-serif;
          color: {TEXT};
      }}
      h1, h2, h3, h4 {{ font-weight: 700; letter-spacing: -0.02em; color: {TEXT}; }}

      /* section rule */
      .sc-rule {{
          display: flex; align-items: baseline; gap: .8rem;
          margin: 2.4rem 0 1.1rem; padding-bottom: .55rem;
          border-bottom: 1px solid {BORDER};
      }}
      .sc-rule .n {{
          font-size: .70rem; font-weight: 700; letter-spacing: .16em;
          color: {MUTED}; font-variant-numeric: tabular-nums;
      }}
      .sc-rule .t {{ font-size: 1.06rem; font-weight: 700; letter-spacing: -.01em; }}
      .sc-rule .s {{ font-size: .78rem; color: {MUTED}; margin-left: auto; }}

      /* metric tiles */
      .sc-grid {{ display: grid; gap: 12px; }}
      .sc-tile {{
          background: {PANEL}; border: 1px solid {BORDER}; border-radius: 10px;
          padding: 1.05rem 1.15rem 1.15rem;
      }}
      .sc-tile .k {{
          font-size: .655rem; font-weight: 700; letter-spacing: .14em;
          text-transform: uppercase; color: {MUTED};
      }}
      .sc-tile .v {{
          font-size: 2.05rem; font-weight: 800; line-height: 1.15;
          letter-spacing: -.035em; margin-top: .42rem;
          font-variant-numeric: tabular-nums;
      }}
      .sc-tile .d {{ font-size: .745rem; color: {MUTED}; margin-top: .28rem; }}

      .sc-note {{
          background: {PANEL}; border: 1px solid {BORDER};
          border-left: 2px solid {ACCENT}; border-radius: 8px;
          padding: .75rem .95rem; font-size: .8rem; color: {MUTED};
          line-height: 1.55;
      }}
      .sc-pill {{
          display: inline-block; padding: .18rem .55rem; border-radius: 999px;
          font-size: .655rem; font-weight: 700; letter-spacing: .1em;
      }}
      code, .sc-mono {{ font-family: "JetBrains Mono", ui-monospace, Menlo, monospace; }}
      [data-testid="stDataFrame"] {{ border: 1px solid {BORDER}; border-radius: 8px; }}
      div[data-baseweb="select"] > div {{
          background: {PANEL}; border-color: {BORDER};
      }}
    </style>
    """,
    unsafe_allow_html=True,
)


def rule(number, title, sub=""):
    st.markdown(
        f'<div class="sc-rule"><span class="n">{number}</span>'
        f'<span class="t">{title}</span><span class="s">{sub}</span></div>',
        unsafe_allow_html=True,
    )


def tiles(items, columns=None):
    """items: list of (label, value, detail, colour|None)."""
    columns = columns or len(items)
    cells = "".join(
        f'<div class="sc-tile"><div class="k">{k}</div>'
        f'<div class="v" style="color:{c or TEXT}">{v}</div>'
        f'<div class="d">{d}</div></div>'
        for k, v, d, c in items
    )
    st.markdown(
        f'<div class="sc-grid" style="grid-template-columns:repeat({columns},1fr)">'
        f"{cells}</div>",
        unsafe_allow_html=True,
    )


# --------------------------------------------------------------------------- #
# Altair plumbing
# --------------------------------------------------------------------------- #
# Altair refuses to render more than 5,000 rows by default. We downsample for
# drawing anyway, but disable the cap so a wide window never errors mid-demo.
try:
    alt.data_transformers.disable_max_rows()
except Exception:  # pragma: no cover - older/newer Altair internals
    pass

# Streamlit 1.4x renamed use_container_width -> width="stretch" on the display
# widgets. Resolve per widget so the file runs on either generation.
def _width_kw(widget):
    if "width" in inspect.signature(widget).parameters:
        return {"width": "stretch"}
    return {"use_container_width": True}


_ALTAIR_KW = _width_kw(st.altair_chart)
_DF_KW = _width_kw(st.dataframe)


def show(chart):
    st.altair_chart(chart, **_ALTAIR_KW)


def dark(chart):
    """Apply the dark skin. Only ever call this on a TOP-LEVEL chart -- Altair
    rejects configure_* on a sub-chart of a concat/layer spec."""
    return (
        chart.configure(background=BG)
        .configure_view(stroke=None, fill=BG)
        .configure_axis(
            domain=False,
            grid=True,
            gridColor=GRID,
            gridWidth=1,
            tickColor=BORDER,
            labelColor=MUTED,
            labelFontSize=10,
            titleColor=MUTED,
            titleFontSize=10.5,
            titleFontWeight=600,
            labelFont="Inter, sans-serif",
            titleFont="Inter, sans-serif",
        )
        .configure_legend(
            labelColor=MUTED, titleColor=MUTED, labelFontSize=10, titleFontSize=10,
            symbolType="square", orient="top", direction="horizontal",
            titleFontWeight=600, offset=6,
        )
        .configure_title(color=TEXT, fontSize=12, fontWeight=700, anchor="start",
                         font="Inter, sans-serif")
        .configure_header(labelColor=MUTED, titleColor=MUTED)
    )


def linked_x(chart):
    """Pan/zoom bound to the x scale. Shared across a vconcat via resolve_scale,
    so dragging one panel moves every panel."""
    try:
        return chart.add_params(
            alt.selection_interval(bind="scales", encodings=["x"])
        )
    except AttributeError:  # Altair 4
        return chart.interactive()


def downsample(frame, max_points=PLOT_MAX_POINTS):
    if len(frame) <= max_points:
        return frame
    step = int(np.ceil(len(frame) / max_points))
    return frame.iloc[::step].copy()


# --------------------------------------------------------------------------- #
# Cached data layer
# --------------------------------------------------------------------------- #
@st.cache_data(show_spinner=False)
def load_eval():
    if not os.path.exists(F.EVAL_PATH):
        return None
    with open(F.EVAL_PATH) as fh:
        return json.load(fh)


@st.cache_data(show_spinner=False)
def load_meta():
    if not os.path.exists(F.META_PATH):
        return None
    with open(F.META_PATH) as fh:
        return json.load(fh)


@st.cache_data(show_spinner="Loading trips ...")
def load_dataset():
    """Raw merged telemetry -- NOT engineered. Rolling features are derived per
    trip on demand so the dashboard runs the exact serving path."""
    frame = F.load_or_build_dataset(verbose=False)
    keep = [c for c in (
        F.RAW_INPUT_COLS
        + ["Trip_ID", "Driver", "Behavior", "Road", "Risk_Level",
           "Latitude", "Longitude"]
    ) if c in frame.columns]
    return frame[keep]


@st.cache_data(show_spinner=False)
def trip_index():
    dataset = load_dataset()
    rows = (
        dataset.groupby("Trip_ID")
        .agg(Driver=("Driver", "first"), Behavior=("Behavior", "first"),
             Road=("Road", "first"), Risk_Level=("Risk_Level", "first"),
             n_samples=("Timestamp", "size"),
             duration_s=("Timestamp", lambda s: float(s.max() - s.min())))
        .reset_index()
    )
    return rows.sort_values("Trip_ID").reset_index(drop=True)


@st.cache_resource(show_spinner="Loading model ...")
def get_model():
    model, _ = F.load_model()
    return model


@st.cache_data(show_spinner="Scoring trip ...", max_entries=6)
def score_trip(trip_id: str):
    """Engineer + predict a whole trip at full 10 Hz rate, WITHOUT SHAP.

    SHAP over 7,000 rows costs seconds; it is computed later for the handful of
    peak-risk rows only, via F.explain_rows on rows that are already engineered.
    """
    dataset = load_dataset()
    trip = dataset[dataset["Trip_ID"] == trip_id]
    scored, skipped = F.score_window(trip, with_shap=False)
    return scored, skipped, len(trip)


# --------------------------------------------------------------------------- #
# Preflight
# --------------------------------------------------------------------------- #
evaluation = load_eval()
meta = load_meta()

missing = [p for p in (F.MODEL_PATH, F.EVAL_PATH) if not os.path.exists(p)]
if missing:
    st.markdown("# Safe Cruise")
    st.error(
        "Missing artefacts: " + ", ".join(f"`{m}`" for m in missing)
        + "\n\nRun the training pass first:\n\n```\n.venv.nosync/bin/python "
        "train_full_model.py\n```"
    )
    st.stop()

st.markdown(
    f"""
    <div style="display:flex;align-items:flex-end;gap:1rem;flex-wrap:wrap">
      <div>
        <div style="font-size:2.35rem;font-weight:800;letter-spacing:-.045em;
                    line-height:1">Safe&nbsp;Cruise</div>
        <div style="color:{MUTED};font-size:.85rem;margin-top:.4rem">
          Temporal driver-risk engine &mdash; jerk and rolling-window behaviour,
          validated by held-out trip
        </div>
      </div>
      <div style="margin-left:auto;text-align:right;color:{MUTED};
                  font-size:.72rem;letter-spacing:.09em;font-weight:600">
        <div>{len(evaluation['features'])} FEATURES &middot;
             {'/'.join(evaluation['roll_windows'])} WINDOWS</div>
        <div style="margin-top:.25rem">{evaluation['n_trips']} TRIPS &middot;
             {evaluation['n_rows_total']:,} SCORED ROWS</div>
      </div>
    </div>
    """,
    unsafe_allow_html=True,
)


# --------------------------------------------------------------------------- #
# 01 -- Model metrics, trip-grouped only
# --------------------------------------------------------------------------- #
def confusion_chart(matrix, title, height=250):
    cm = np.asarray(matrix, dtype=float)
    norm = cm / cm.sum(axis=1, keepdims=True).clip(min=1)
    rows = [
        {
            "true": F.CLASS_NAMES[i],
            "pred": F.CLASS_NAMES[j],
            "count": int(cm[i, j]),
            "share": float(norm[i, j]),
            "text": f"{int(cm[i, j]):,}\n{norm[i, j]:.0%}",
        }
        for i in range(cm.shape[0])
        for j in range(cm.shape[1])
    ]
    frame = pd.DataFrame(rows)
    base = alt.Chart(frame).encode(
        x=alt.X("pred:N", sort=F.CLASS_NAMES, title="PREDICTED",
                axis=alt.Axis(orient="top", labelAngle=0)),
        y=alt.Y("true:N", sort=F.CLASS_NAMES, title="ACTUAL"),
    )
    heat = base.mark_rect(stroke=BG, strokeWidth=3, cornerRadius=3).encode(
        color=alt.Color(
            "share:Q",
            scale=alt.Scale(domain=[0, 1], range=["#12141a", ACCENT, "#dbeafe"]),
            legend=None,
        ),
        tooltip=[alt.Tooltip("true:N", title="Actual"),
                 alt.Tooltip("pred:N", title="Predicted"),
                 alt.Tooltip("count:Q", title="Rows", format=","),
                 alt.Tooltip("share:Q", title="Of actual class", format=".1%")],
    )
    text = base.mark_text(
        fontSize=11.5, fontWeight=600, lineBreak="\n", font="Inter, sans-serif"
    ).encode(
        text="text:N",
        color=alt.condition(alt.datum.share > 0.45, alt.value("#0a1020"),
                            alt.value(MUTED)),
    )
    return dark((heat + text).properties(height=height, title=title))


rule("01", "Model metrics",
     "trip-grouped validation only &mdash; no random row split is reported")

cv = evaluation["grouped_cv"]
holdout = evaluation["holdout"]
trip_level = evaluation["trip_level"]

n_folds = len(cv["fold_accuracies"])
tiles([
    ("Grouped CV accuracy", f"{cv['mean_fold_accuracy']:.1%}",
     f"±{cv['std_fold_accuracy']:.1%} over {n_folds} folds &middot; chance 33.3%",
     ACCENT),
    ("Held-out trips", f"{holdout['accuracy']:.1%}",
     f"{len(evaluation['test_trips'])} trips never trained on", None),
    ("Macro precision", f"{cv['precision_macro']:.1%}",
     f"recall {cv['recall_macro']:.1%} &middot; F1 {cv['f1_macro']:.1%}", None),
    ("Trip-level verdict", f"{trip_level['accuracy']:.1%}",
     f"{trip_level['n_correct']}/{trip_level['n_trips']} trips, majority vote",
     None),
])

left, right = st.columns([1.15, 1], gap="large")
with left:
    show(confusion_chart(cv["confusion_matrix"],
                         f"Per-sample confusion — pooled {n_folds}-fold "
                         f"grouped CV", height=252))
with right:
    show(confusion_chart(trip_level["confusion_matrix"],
                         "Per-trip confusion — majority vote", height=252))

per_class = pd.DataFrame([
    {
        "Class": f"{name} ({F.CLASS_BEHAVIOURS[i]})",
        "Precision": f"{cv['per_class'][name]['precision']:.1%}",
        "Recall": f"{cv['per_class'][name]['recall']:.1%}",
        "F1": f"{cv['per_class'][name]['f1-score']:.1%}",
        "Rows": f"{int(cv['per_class'][name]['support']):,}",
    }
    for i, name in enumerate(F.CLASS_NAMES)
])

lo, ro = st.columns([1.15, 1], gap="large")
with lo:
    st.markdown(
        f'<div style="font-size:.66rem;font-weight:700;letter-spacing:.14em;'
        f'color:{MUTED};text-transform:uppercase;margin:.4rem 0 .5rem">'
        f"Per-class performance</div>", unsafe_allow_html=True)
    # Values are pre-formatted strings rather than a pandas Styler: .style
    # requires jinja2, and there is no reason to add a dependency for 3 rows.
    st.dataframe(per_class, hide_index=True, **_DF_KW)
with ro:
    folds = pd.DataFrame({
        "fold": [f"F{i}" for i in range(1, n_folds + 1)],
        "accuracy": cv["fold_accuracies"],
    })
    bars = alt.Chart(folds).mark_bar(size=26, cornerRadius=3, color=ACCENT).encode(
        x=alt.X("fold:N", title=None, axis=alt.Axis(labelAngle=0)),
        y=alt.Y("accuracy:Q", title="ACCURACY",
                scale=alt.Scale(domain=[0, max(cv["fold_accuracies"]) * 1.25]),
                axis=alt.Axis(format="%")),
        tooltip=[alt.Tooltip("fold:N"), alt.Tooltip("accuracy:Q", format=".2%")],
    )
    chance = alt.Chart(pd.DataFrame({"y": [1 / 3]})).mark_rule(
        color="#ef4444", strokeDash=[4, 4], strokeWidth=1
    ).encode(y="y:Q")
    show(dark((bars + chance).properties(
        height=210, title="Fold stability — dashed line is chance")))


def feature_family(name):
    for window in F.ROLL_WINDOWS:
        if name.endswith(f"_{window}"):
            return f"rolling {window}"
    if name.startswith("Jerk"):
        return "jerk"
    return "instantaneous"


TOP_N_IMPORTANCE = 14
importance = pd.DataFrame(evaluation["feature_importances"][:TOP_N_IMPORTANCE])
importance["family"] = importance["feature"].map(feature_family)
family_order = ["instantaneous", "jerk"] + [f"rolling {w}" for w in F.ROLL_WINDOWS]
family_colors = ["#64748b", "#a78bfa", "#38bdf8", "#22d3ee", ACCENT][
    : len(family_order)
]

imp_chart = alt.Chart(importance).mark_bar(cornerRadius=3, height=13).encode(
    x=alt.X("gain:Q", title="GAIN (normalised)", axis=alt.Axis(format=".2f")),
    y=alt.Y("feature:N", sort="-x", title=None),
    color=alt.Color("family:N", title=None,
                    scale=alt.Scale(domain=family_order, range=family_colors)),
    tooltip=[alt.Tooltip("feature:N", title="Feature"),
             alt.Tooltip("family:N", title="Family"),
             alt.Tooltip("gain:Q", title="Gain", format=".4f")],
)
show(dark(imp_chart.properties(
    height=26 * len(importance),
    title=f"Top {TOP_N_IMPORTANCE} features by gain — every one is "
          f"behavioural, none identifies a route")))

st.markdown(
    f'<div class="sc-note"><b style="color:{TEXT}">Why these numbers are lower '
    "than a random split.</b> The risk label is a property of the whole trip, so "
    "a random row split lets the model recognise which trip a row came from and "
    "read the label off it &mdash; that scored 99.2%. <code>Altitude</code> "
    "(route shape) and <code>Roll</code> (constant phone-mount angle, std 0.037°) "
    "are removed, as is raw <code>Yaw</code> (absolute compass heading); only its "
    "variability <code>Yaw_std</code> survives. Latitude and Longitude are kept "
    "in the dataframe for the map but are absent from the feature list. Every "
    "figure above is measured on trips the model never saw."
    "</div>",
    unsafe_allow_html=True,
)


# --------------------------------------------------------------------------- #
# 02 -- Trip simulator (sidebar controls)
# --------------------------------------------------------------------------- #
index = trip_index()
held_out = set(evaluation["test_trips"])
index["Split"] = np.where(index["Trip_ID"].isin(held_out), "HELD-OUT", "TRAIN")

with st.sidebar:
    st.markdown(
        f'<div style="font-size:1.02rem;font-weight:800;letter-spacing:-.02em;'
        f'margin:.2rem 0 .1rem">Trip simulator</div>'
        f'<div style="color:{MUTED};font-size:.74rem;line-height:1.5;'
        f'margin-bottom:1rem">Replays a trip through the live serving path: the '
        f"same <code>features.py</code> that trained the model derives jerk and "
        f"rolling statistics, then XGBoost scores every sample.</div>",
        unsafe_allow_html=True,
    )

    scope = st.radio(
        "Trip pool",
        ["Held-out only", "All trips"],
        help="Held-out trips were never seen during training. 'All trips' "
             "includes trips the model memorised — useful for comparison, "
             "not for judging accuracy.",
    )
    pool = index if scope == "All trips" else index[index["Split"] == "HELD-OUT"]
    pool = pool.reset_index(drop=True)

    labels = {
        row.Trip_ID: (
            f"{'●' if row.Split == 'HELD-OUT' else '○'} {row.Behavior} · "
            f"{row.Driver} · {row.Road.title()} · {row.duration_s / 60:.0f} min"
        )
        for row in pool.itertuples()
    }
    order = pool.sort_values(["Behavior", "Trip_ID"])["Trip_ID"].tolist()
    trip_id = st.selectbox(
        f"Trip ({len(order)} available)",
        order,
        format_func=lambda t: labels[t],
    )
    meta_row = index[index["Trip_ID"] == trip_id].iloc[0]

    st.markdown(
        f'<div style="margin-top:.35rem"><span class="sc-pill" style="background:'
        f'{"#052e16" if meta_row.Split == "HELD-OUT" else "#3f1d1d"};color:'
        f'{"#4ade80" if meta_row.Split == "HELD-OUT" else "#fca5a5"}">'
        f"{meta_row.Split}</span></div>"
        f'<div class="sc-mono" style="color:{MUTED};font-size:.66rem;'
        f'margin-top:.6rem;word-break:break-all">{trip_id}</div>',
        unsafe_allow_html=True,
    )
    if meta_row.Split == "TRAIN":
        st.warning(
            "This trip was in the training set. Its predictions are memorised, "
            "not evidence of generalisation."
        )

    st.divider()
    peak_count = st.slider("Peak moments to explain", 1, 6, 3,
                           help="SHAP is computed only for these rows — "
                                "explaining all ~7,000 would cost seconds.")
    show_raw_table = st.checkbox("Show scored sample table", value=False)


rule("02", "Trip simulator",
     f"{meta_row.Behavior} &middot; driver {meta_row.Driver} &middot; "
     f"{meta_row.Road.title()} &middot; {meta_row.Split}")

scored, skipped, n_raw = score_trip(trip_id)
if scored.empty:
    st.error(
        f"No sample in `{trip_id}` has a full {F.MIN_WINDOW_SECONDS:.0f}s window "
        f"— the trip is too short to score."
    )
    st.stop()

true_label = int(meta_row.Risk_Level)
votes = scored["Predicted_Risk"].value_counts(normalize=True)
verdict = int(votes.idxmax())
mix = {F.CLASS_NAMES[i]: float(votes.get(i, 0.0)) for i in range(3)}
peak_row = scored.loc[scored["Proba_High"].idxmax()]
span = float(scored["Timestamp"].max() - scored["Timestamp"].min())

tiles([
    ("Ground truth", F.CLASS_NAMES[true_label],
     f"{F.CLASS_BEHAVIOURS[true_label]} — from the trip folder name",
     RISK_COLORS[true_label]),
    ("Model verdict", F.CLASS_NAMES[verdict],
     f"majority of {len(scored):,} samples ({votes.max():.0%} agree)"
     + ("" if verdict == true_label else " — MISS"),
     RISK_COLORS[verdict]),
    ("High-risk share", f"{mix['High']:.1%}",
     f"Medium {mix['Medium']:.0%} &middot; Low {mix['Low']:.0%}",
     RISK_COLORS[2] if mix["High"] > 0.2 else None),
    ("Peak P(High)", f"{float(peak_row['Proba_High']):.1%}",
     f"at t = {float(peak_row['Timestamp']):.1f}s", None),
    ("Samples", f"{len(scored):,}",
     f"{skipped} warm-up rows skipped &middot; {span / 60:.1f} min", None),
])

mix_frame = pd.DataFrame({
    "label": F.CLASS_NAMES,
    "share": [mix[n] for n in F.CLASS_NAMES],
    "row": ["mix"] * 3,
})
mix_bar = alt.Chart(mix_frame).mark_bar(height=14, cornerRadius=2).encode(
    x=alt.X("share:Q", stack="normalize", title=None,
            axis=alt.Axis(format="%", grid=False)),
    y=alt.Y("row:N", title=None, axis=None),
    color=alt.Color("label:N", title=None,
                    scale=alt.Scale(domain=F.CLASS_NAMES, range=RISK_COLORS),
                    legend=alt.Legend(orient="top")),
    order=alt.Order("label:N"),
    tooltip=[alt.Tooltip("label:N", title="Risk"),
             alt.Tooltip("share:Q", title="Share of samples", format=".1%")],
)
show(dark(mix_bar.properties(height=54, title="Per-sample risk mix")))


# --------------------------------------------------------------------------- #
# 03 -- Temporal visualisation
# --------------------------------------------------------------------------- #
rule("03", "Temporal features",
     "raw signal above, the rolling statistics the model actually reads below")

t_min = float(scored["Timestamp"].min())
t_max = float(scored["Timestamp"].max())
window = st.slider(
    "Time window (seconds since trip start) — every chart below, including the "
    "risk timeline, is bound to this range",
    min_value=float(round(t_min)), max_value=float(round(t_max)),
    value=(float(round(t_min)), float(round(t_max))),
    step=1.0,
)
view = scored[(scored["Timestamp"] >= window[0]) &
              (scored["Timestamp"] <= window[1])]
if view.empty:
    st.warning("No scored samples in that range.")
    st.stop()
plot = downsample(view)
x_domain = [float(window[0]), float(window[1])]

st.markdown(
    f'<div style="color:{MUTED};font-size:.74rem;margin:-.3rem 0 .9rem">'
    f"Showing {len(view):,} scored samples "
    f"({len(plot):,} drawn) over {window[1] - window[0]:.0f}s. Predictions are "
    f"always computed at the full {F.NOMINAL_HZ:.0f} Hz — only the drawing is "
    f"thinned.</div>",
    unsafe_allow_html=True,
)


def to_long(frame, mapping):
    parts = []
    for label, col in mapping.items():
        if col not in frame.columns:
            continue
        part = frame[["Timestamp", col]].rename(columns={col: "value"}).copy()
        part["series"] = label
        parts.append(part)
    return pd.concat(parts, ignore_index=True)


def line_panel(frame, mapping, title, y_title, colors, height=145,
               stroke=1.25, area=False):
    """One synchronised panel. x domain is pinned to the slider on every panel,
    which is what keeps them aligned across separate st.altair_chart calls."""
    long = to_long(frame, mapping)
    labels = [k for k in mapping if k in set(long["series"])]
    mark = dict(strokeWidth=stroke, clip=True, interpolate="monotone")
    chart = alt.Chart(long)
    chart = chart.mark_area(**mark, opacity=0.16, line=True) if area \
        else chart.mark_line(**mark)
    return dark(
        chart.encode(
            x=alt.X("Timestamp:Q", title=None,
                    scale=alt.Scale(domain=x_domain, nice=False, zero=False)),
            y=alt.Y("value:Q", title=y_title,
                    scale=alt.Scale(zero=False, nice=True)),
            color=alt.Color("series:N", title=None, sort=labels,
                            scale=alt.Scale(domain=labels,
                                            range=colors[: len(labels)]),
                            legend=alt.Legend(orient="top", offset=2)),
            tooltip=[alt.Tooltip("Timestamp:Q", title="t (s)", format=".1f"),
                     alt.Tooltip("series:N", title="Signal"),
                     alt.Tooltip("value:Q", title="Value", format=".3f")],
        ).properties(height=height, title=title)
    )


R = F.rolling_name
LONGEST = F.ROLL_WINDOWS[-1]

show(line_panel(
    plot,
    {"Speed (raw)": "Speed_kmh", f"Mean {LONGEST}": R("Speed_kmh", "mean", LONGEST)},
    "Speed — raw GPS against its sustained pace", "KM/H",
    [ACCENT, "#a78bfa"], height=155, area=False,
))

show(line_panel(
    plot,
    {f"Std {w}": R("Speed_kmh", "std", w) for w in F.ROLL_WINDOWS},
    "Speed variability — rolling standard deviation "
    f"({', '.join(F.ROLL_WINDOWS)})", "STD (KM/H)",
    ["#38bdf8", "#818cf8", "#c084fc"],
))

show(line_panel(
    plot,
    {"Lateral (X)": "Accel_X_Filtered", "Longitudinal (Y)": "Accel_Y_Filtered",
     "Combined |a|": "Accel_Magnitude"},
    "Filtered acceleration — raw inertial signal", "g",
    ["#22d3ee", "#f472b6", "#94a3b8"],
))

show(line_panel(
    plot,
    {"Lateral weave": R("Accel_X_Filtered", "std", LONGEST),
     "Accel/brake": R("Accel_Y_Filtered", "std", LONGEST),
     "Turn-rate": R("Yaw", "std", LONGEST)},
    f"Behavioural variability over {LONGEST} — the three strongest "
    "aggression signals", "STD",
    ["#22d3ee", "#f472b6", "#fbbf24"],
))

show(line_panel(
    plot,
    {"Jerk |j| (raw)": "Jerk_Magnitude",
     f"Mean {LONGEST}": R("Jerk_Magnitude", "mean", LONGEST),
     f"Max {F.ROLL_WINDOWS[0]}": R("Jerk_Magnitude", "max", F.ROLL_WINDOWS[0])},
    "Jerk — rate of change of acceleration, the sudden-input detector",
    "g/s",
    ["#334155", "#f97316", "#fbbf24"], height=165,
))


# --------------------------------------------------------------------------- #
# 04 -- Live risk timeline + SHAP at the peak moments
# --------------------------------------------------------------------------- #
rule("04", "Live risk timeline",
     "green = Low &middot; orange = Medium &middot; red = High")

segments = F.risk_segments(view)

timeline = alt.Chart(segments).mark_rect(cornerRadius=1).encode(
    x=alt.X("start:Q", title=None,
            scale=alt.Scale(domain=x_domain, nice=False, zero=False),
            axis=alt.Axis(labels=False, ticks=False, grid=False)),
    x2="end:Q",
    color=alt.Color("label:N", title=None,
                    scale=alt.Scale(domain=F.CLASS_NAMES, range=RISK_COLORS),
                    legend=alt.Legend(orient="top", offset=2)),
    tooltip=[alt.Tooltip("label:N", title="Risk"),
             alt.Tooltip("start:Q", title="From (s)", format=".1f"),
             alt.Tooltip("end:Q", title="To (s)", format=".1f"),
             alt.Tooltip("duration:Q", title="Held for (s)", format=".1f")],
)


def spaced_peaks(frame, count, min_gap=20.0):
    """Highest P(High) rows, forced at least min_gap seconds apart so the
    'peak moments' are distinct events and not three neighbouring samples."""
    candidates = frame.sort_values("Proba_High", ascending=False)
    chosen = []
    for _, row in candidates.iterrows():
        t = float(row["Timestamp"])
        if all(abs(t - float(c["Timestamp"])) >= min_gap for c in chosen):
            chosen.append(row)
        if len(chosen) == count:
            break
    return pd.DataFrame(chosen)


peaks = spaced_peaks(view, peak_count)

probability = to_long(plot, {
    "P(High)": "Proba_High", "P(Medium)": "Proba_Medium", "P(Low)": "Proba_Low",
})
prob_chart = alt.Chart(probability).mark_line(
    strokeWidth=1.2, clip=True, interpolate="monotone"
).encode(
    x=alt.X("Timestamp:Q", title="TRIP TIME (S)",
            scale=alt.Scale(domain=x_domain, nice=False, zero=False)),
    y=alt.Y("value:Q", title="CONFIDENCE",
            scale=alt.Scale(domain=[0, 1]), axis=alt.Axis(format="%")),
    color=alt.Color("series:N", title=None,
                    scale=alt.Scale(domain=["P(Low)", "P(Medium)", "P(High)"],
                                    range=RISK_COLORS),
                    legend=alt.Legend(orient="top", offset=2)),
    tooltip=[alt.Tooltip("Timestamp:Q", title="t (s)", format=".1f"),
             alt.Tooltip("series:N", title="Class"),
             alt.Tooltip("value:Q", title="Probability", format=".1%")],
)
peak_rules = alt.Chart(peaks[["Timestamp"]]).mark_rule(
    color=TEXT, strokeDash=[3, 3], strokeWidth=1, opacity=0.55
).encode(x=alt.X("Timestamp:Q", scale=alt.Scale(domain=x_domain, nice=False)))

show(dark(timeline.properties(
    height=46,
    title=f"Predicted risk band — {len(segments)} state changes in this window")))
show(dark((prob_chart + peak_rules).properties(
    height=185,
    title="Class probability with the explained peak moments marked")))

FEATURE_ORDER = F.model_feature_order(get_model())
explained = F.explain_rows(peaks)

st.markdown(
    f'<div style="font-size:.66rem;font-weight:700;letter-spacing:.14em;'
    f'color:{MUTED};text-transform:uppercase;margin:1.6rem 0 .2rem">'
    f"SHAP attribution at the peak risk moments</div>"
    f'<div style="color:{MUTED};font-size:.76rem;margin-bottom:.7rem">'
    f"Bars are log-odds contributions toward the class the model actually "
    f"predicted for that sample. SHAP is computed for these "
    f"{len(explained)} rows only — the rolling features come from the full-rate "
    f"trip, never re-derived from a slice.</div>",
    unsafe_allow_html=True,
)

SHAP_TOP_N = 9


def shap_chart(row, top_n=SHAP_TOP_N):
    predicted = int(row["Predicted_Risk"])
    target = F.CLASS_NAMES[predicted]
    impacts = {name: float(row[f"SHAP_{name}"]) for name in FEATURE_ORDER}
    top = sorted(impacts.items(), key=lambda kv: abs(kv[1]), reverse=True)[:top_n]
    frame = pd.DataFrame(
        [{"feature": name,
          "impact": value,
          "value": float(row[name]),
          "direction": f"toward {target}" if value > 0 else f"away from {target}"}
         for name, value in top]
    )
    chart = alt.Chart(frame).mark_bar(cornerRadius=2, height=14).encode(
        x=alt.X("impact:Q", title="SHAP (log-odds)",
                axis=alt.Axis(format="+.2f")),
        y=alt.Y("feature:N", title=None,
                sort=alt.EncodingSortField(field="impact", op="max",
                                           order="descending")),
        color=alt.Color(
            "direction:N", title=None,
            scale=alt.Scale(domain=[f"toward {target}", f"away from {target}"],
                            range=[RISK_COLORS[predicted], "#475569"]),
            legend=alt.Legend(orient="top", offset=2),
        ),
        tooltip=[alt.Tooltip("feature:N", title="Feature"),
                 alt.Tooltip("value:Q", title="Feature value", format=".4f"),
                 alt.Tooltip("impact:Q", title="SHAP", format="+.4f")],
    )
    zero = alt.Chart(pd.DataFrame({"x": [0.0]})).mark_rule(
        color=BORDER, strokeWidth=1
    ).encode(x="x:Q")
    return dark((chart + zero).properties(height=26 * len(frame) + 10))


tab_labels = [
    f"t={float(r['Timestamp']):.0f}s · {F.CLASS_NAMES[int(r['Predicted_Risk'])]}"
    f" · P(High) {float(r['Proba_High']):.0%}"
    for _, r in explained.iterrows()
]
for tab, (_, row) in zip(st.tabs(tab_labels), explained.iterrows()):
    with tab:
        predicted = int(row["Predicted_Risk"])
        tiles([
            ("Verdict", F.CLASS_NAMES[predicted],
             F.CLASS_BEHAVIOURS[predicted], RISK_COLORS[predicted]),
            ("Confidence", f"{float(row[f'Proba_{F.CLASS_NAMES[predicted]}']):.1%}",
             f"P(High) {float(row['Proba_High']):.1%}", None),
            ("Speed", f"{float(row['Speed_kmh']):.0f}",
             f"km/h &middot; {LONGEST} mean "
             f"{float(row[R('Speed_kmh', 'mean', LONGEST)]):.0f}", None),
            ("Jerk |j|", f"{float(row['Jerk_Magnitude']):.2f}",
             f"g/s &middot; {LONGEST} mean "
             f"{float(row[R('Jerk_Magnitude', 'mean', LONGEST)]):.2f}", None),
        ])
        show(shap_chart(row))


if show_raw_table:
    rule("05", "Scored samples",
         "the exact rows the charts above are drawn from")
    columns = ["Timestamp", "Risk_Label", "Proba_Low", "Proba_Medium",
               "Proba_High", "Speed_kmh", "Accel_Magnitude", "Jerk_Magnitude",
               R("Speed_kmh", "std", LONGEST), R("Jerk_Magnitude", "mean", LONGEST)]
    table = downsample(view, 600)[[c for c in columns if c in view.columns]]
    st.dataframe(table, hide_index=True, height=380, **_DF_KW)

st.markdown(
    f'<div class="sc-note" style="margin-top:2.2rem">'
    f'<b style="color:{TEXT}">Known limitation.</b> A majority vote over samples '
    f"dilutes aggression, which is bursty: several AGGRESSIVE trips spend most of "
    f"their minutes driving normally and get voted Low. The band timeline above "
    f"exists to make those bursts visible — for an operational alarm, threshold "
    f"on the High-risk share rather than taking the modal class. "
    f'<span style="color:{MUTED}">Model: {len(FEATURE_ORDER)} features, windows '
    f"{', '.join(F.ROLL_WINDOWS)}, needs {F.MIN_WINDOW_SECONDS:.0f}s of history "
    f"before the first prediction. The companion API at "
    f"<code>localhost:8000</code> serves the same feature code and has no "
    f"authentication — keep it on localhost.</span></div>",
    unsafe_allow_html=True,
)
