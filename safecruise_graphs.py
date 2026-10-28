"""
SafeCruise — Model Evaluation Graphs
=====================================
Run this script to generate 8 publication-ready graphs from the
safe_cruise_eval.json and safe_cruise_model_meta.json files.

  python safecruise_graphs.py

Each graph is saved as a PNG in the current directory.
Convert to notebook with:  jupytext --to notebook safecruise_graphs.py
Or open in Jupyter directly as a % percent-format notebook.
"""

# %% [markdown]
# # SafeCruise — Model Evaluation & Visualization
# All graphs generated from `safe_cruise_eval.json` and `safe_cruise_model_meta.json`.

# %% Setup & Data Loading
import matplotlib
matplotlib.use('Agg')   # headless – no display needed
import json
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import os

# ── Style ──────────────────────────────────────────────────────────────────────
plt.rcParams.update({
    'figure.facecolor': '#1a1a2e',
    'axes.facecolor':   '#16213e',
    'axes.edgecolor':   '#0f3460',
    'axes.labelcolor':  '#e0e0e0',
    'xtick.color':      '#a0a0a0',
    'ytick.color':      '#a0a0a0',
    'text.color':       '#e0e0e0',
    'grid.color':       '#0f3460',
    'grid.linestyle':   '--',
    'grid.alpha':       0.5,
    'font.family':      'DejaVu Sans',
    'axes.titlesize':   13,
    'axes.titleweight': 'bold',
})

COLORS  = {'Low': '#22c55e', 'Medium': '#f59e0b', 'High': '#ef4444'}
PALETTE = ['#22c55e', '#f59e0b', '#ef4444']
ACCENT  = '#e94560'

# ── Load data ──────────────────────────────────────────────────────────────────
with open('safe_cruise_eval.json') as f:
    ev = json.load(f)

with open('safe_cruise_model_meta.json') as f:
    meta = json.load(f)

print('Data loaded.')
print(f"  Total rows : {ev['n_rows_total']:,}")
print(f"  Total trips: {ev['n_trips']}")
print(f"  Holdout acc: {ev['holdout']['accuracy']:.2%}")
print(f"  CV acc     : {ev['grouped_cv']['accuracy']:.2%}")


# %% [markdown]
# ## 1. Confusion Matrices — Holdout & Grouped CV

# %% Graph 1: Confusion Matrix
fig, axes = plt.subplots(1, 2, figsize=(14, 5.5))
class_names = ['Low\n(Normal)', 'Medium\n(Drowsy)', 'High\n(Aggressive)']

for ax, key, title in zip(axes,
                           ['holdout', 'grouped_cv'],
                           ['Holdout Set Confusion Matrix', 'Grouped CV Confusion Matrix']):
    cm = np.array(ev[key]['confusion_matrix'])
    cm_pct = cm.astype(float) / cm.sum(axis=1, keepdims=True) * 100

    im = ax.imshow(cm_pct, cmap='hot', aspect='auto', vmin=0, vmax=100)
    cbar = fig.colorbar(im, ax=ax, shrink=0.85)
    cbar.set_label('% of True Class', color='#e0e0e0')
    cbar.ax.yaxis.set_tick_params(color='#a0a0a0')
    plt.setp(cbar.ax.yaxis.get_ticklabels(), color='#a0a0a0')
    ax.set_xticks(range(3))
    ax.set_yticks(range(3))
    ax.set_xticklabels(class_names)
    ax.set_yticklabels(class_names)

    for i in range(3):
        for j in range(3):
            count = cm[i, j]
            pct   = cm_pct[i, j]
            color = 'white' if pct < 55 else '#1a1a2e'
            ax.text(j + 0.5, i + 0.42, f'{count:,}', ha='center', va='center',
                    fontsize=11, fontweight='bold', color=color)
            ax.text(j + 0.5, i + 0.62, f'({pct:.1f}%)', ha='center', va='center',
                    fontsize=8.5, color=color, alpha=0.85)

    ax.set_xlabel('Predicted Label', fontsize=11)
    ax.set_ylabel('True Label', fontsize=11)
    ax.set_title(title, pad=12)
    ax.tick_params(axis='both', which='both', length=0)

plt.suptitle('SafeCruise · XGBoost Confusion Matrices', fontsize=15, fontweight='bold',
             color='white', y=1.02)
plt.tight_layout()
plt.savefig('graph_confusion_matrix.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_confusion_matrix.png')


# %% [markdown]
# ## 2. Per-Class Precision / Recall / F1

# %% Graph 2: Per-class metrics
metrics   = ['precision', 'recall', 'f1-score']
classes   = ['Low', 'Medium', 'High']
pc        = ev['holdout']['per_class']
data      = {m: [pc[c][m] for c in classes] for m in metrics}

x          = np.arange(len(classes))
width      = 0.25
bar_colors = ['#3b82f6', '#a855f7', '#f59e0b']

fig, ax = plt.subplots(figsize=(10, 5.5))
for i, (metric, color) in enumerate(zip(metrics, bar_colors)):
    bars = ax.bar(x + i * width, data[metric], width,
                  label=metric.capitalize(), color=color,
                  alpha=0.88, edgecolor='#1a1a2e', linewidth=0.5)
    for bar in bars:
        h = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2, h + 0.008,
                f'{h:.2f}', ha='center', va='bottom', fontsize=8.5, color='#e0e0e0')

for i, (cls, c) in enumerate(zip(classes, PALETTE)):
    ax.axvspan(i - 0.35, i + 0.65, ymin=0, ymax=0.03, color=c, alpha=0.35)

ax.set_xticks(x + width)
ax.set_xticklabels([f'{c}\n(Risk)' for c in classes], fontsize=11)
ax.set_ylim(0, 0.85)
ax.set_ylabel('Score', fontsize=11)
ax.set_title('SafeCruise · Per-Class Precision / Recall / F1  (Holdout Set)', pad=12)
ax.legend(fontsize=10, loc='lower right', framealpha=0.3)
ax.yaxis.grid(True, alpha=0.4)
ax.set_axisbelow(True)

plt.tight_layout()
plt.savefig('graph_per_class_metrics.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_per_class_metrics.png')


# %% [markdown]
# ## 3. Top-20 Feature Importance by Gain

# %% Graph 3: Feature importance
fi = pd.DataFrame(ev['feature_importances']).sort_values('gain', ascending=True).tail(20)

def fi_color(name):
    if '60s' in name: return '#ef4444'
    if '15s' in name: return '#f59e0b'
    if '5s'  in name: return '#3b82f6'
    return '#a855f7'

bar_colors = [fi_color(n) for n in fi['feature']]

fig, ax = plt.subplots(figsize=(11, 8))
bars = ax.barh(fi['feature'], fi['gain'], color=bar_colors,
               edgecolor='#1a1a2e', linewidth=0.5, height=0.7)

for bar in bars:
    w = bar.get_width()
    ax.text(w + 0.0005, bar.get_y() + bar.get_height() / 2,
            f'{w:.4f}', va='center', fontsize=8, color='#e0e0e0')

legend_handles = [
    mpatches.Patch(color='#ef4444', label='60s window'),
    mpatches.Patch(color='#f59e0b', label='15s window'),
    mpatches.Patch(color='#3b82f6', label='5s window'),
    mpatches.Patch(color='#a855f7', label='Instantaneous'),
]
ax.legend(handles=legend_handles, fontsize=9, loc='lower right', framealpha=0.25)
ax.set_xlabel('Feature Importance (Gain)', fontsize=11)
ax.set_title('SafeCruise · Top-20 XGBoost Feature Importances by Gain', pad=12)
ax.xaxis.grid(True, alpha=0.4)
ax.set_axisbelow(True)

plt.tight_layout()
plt.savefig('graph_feature_importance.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_feature_importance.png')


# %% [markdown]
# ## 4. Cross-Validation Fold Accuracy

# %% Graph 4: CV fold accuracy
fold_accs = ev['grouped_cv']['fold_accuracies']
mean_acc  = ev['grouped_cv']['mean_fold_accuracy']
std_acc   = ev['grouped_cv']['std_fold_accuracy']
folds     = [f'Fold {i+1}' for i in range(len(fold_accs))]

fig, ax = plt.subplots(figsize=(9, 5))
bar_c = ['#22c55e' if a >= mean_acc else '#ef4444' for a in fold_accs]
bars  = ax.bar(folds, fold_accs, color=bar_c, alpha=0.85,
               edgecolor='#1a1a2e', linewidth=0.6, width=0.55)

for bar, acc in zip(bars, fold_accs):
    ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.003,
            f'{acc:.3f}', ha='center', va='bottom', fontsize=10, color='#e0e0e0')

ax.axhline(mean_acc, color='#f59e0b', linewidth=2, linestyle='--',
           label=f'Mean = {mean_acc:.3f}')
ax.fill_between(range(len(folds)),
                [mean_acc - std_acc] * len(folds),
                [mean_acc + std_acc] * len(folds),
                color='#f59e0b', alpha=0.12, label=f'±1 Std ({std_acc:.3f})')

ax.set_ylim(0.35, 0.70)
ax.set_ylabel('Accuracy', fontsize=11)
ax.set_title('SafeCruise · 5-Fold Grouped CV Accuracy (Grouped by Trip)', pad=12)
ax.legend(fontsize=10, framealpha=0.25)
ax.yaxis.grid(True, alpha=0.4)
ax.set_axisbelow(True)

plt.tight_layout()
plt.savefig('graph_cv_fold_accuracy.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_cv_fold_accuracy.png')


# %% [markdown]
# ## 5. Per-Trip Prediction Vote Share

# %% Graph 5: Per-trip votes
label_map = {0: 'Low', 1: 'Medium', 2: 'High'}
rows = []
for t in ev['trip_level']['per_trip']:
    parts  = t['trip'].split('-')
    short  = parts[3] if len(parts) > 3 else '?'
    driver = next((p for p in parts if p.startswith('D') and p[1:].isdigit()), '?')
    rows.append({
        'label':   f"{driver}/{short[:4]}",
        'true':    label_map[t['true']],
        'pred':    label_map[t['predicted']],
        'low':     t['mix'].get('Low', 0),
        'med':     t['mix'].get('Medium', 0),
        'high':    t['mix'].get('High', 0),
        'correct': t['true'] == t['predicted'],
    })

df = pd.DataFrame(rows)
x  = np.arange(len(df))

fig, ax = plt.subplots(figsize=(16, 6))
ax.bar(x, df['low'],  label='Low (Normal)',      color='#22c55e', alpha=0.85)
ax.bar(x, df['med'],  label='Medium (Drowsy)',   color='#f59e0b', alpha=0.85, bottom=df['low'])
ax.bar(x, df['high'], label='High (Aggressive)', color='#ef4444', alpha=0.85,
       bottom=df['low'] + df['med'])

for i, row in df.iterrows():
    if not row['correct']:
        ax.axvline(i, color='white', linewidth=1.5, linestyle=':', alpha=0.6)

ax.set_xticks(x)
ax.set_xticklabels(df['label'], rotation=55, ha='right', fontsize=7.5)
ax.set_ylabel('Prediction Vote Share', fontsize=11)
ax.set_title('SafeCruise · Per-Trip Risk Vote Distribution  (dotted = misclassified)', pad=12)
ax.legend(fontsize=9, loc='upper right', framealpha=0.3)
ax.yaxis.grid(True, alpha=0.3)
ax.set_axisbelow(True)
ax.set_ylim(0, 1)

plt.tight_layout()
plt.savefig('graph_per_trip_votes.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_per_trip_votes.png')


# %% [markdown]
# ## 6. Holdout vs CV Metrics Comparison

# %% Graph 6: Metrics comparison
metric_labels = ['Accuracy', 'Precision\n(Macro)', 'Recall\n(Macro)', 'F1\n(Macro)']
holdout_vals  = [ev['holdout']['accuracy'], ev['holdout']['precision_macro'],
                 ev['holdout']['recall_macro'], ev['holdout']['f1_macro']]
cv_vals       = [ev['grouped_cv']['accuracy'], ev['grouped_cv']['precision_macro'],
                 ev['grouped_cv']['recall_macro'], ev['grouped_cv']['f1_macro']]

x     = np.arange(len(metric_labels))
width = 0.32

fig, ax = plt.subplots(figsize=(10, 5.5))
b1 = ax.bar(x - width/2, holdout_vals, width, label='Holdout Set',
            color='#3b82f6', alpha=0.88, edgecolor='#1a1a2e')
b2 = ax.bar(x + width/2, cv_vals,      width, label='Grouped 5-Fold CV',
            color='#a855f7', alpha=0.88, edgecolor='#1a1a2e')

for group in [b1, b2]:
    for bar in group:
        h = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2, h + 0.004,
                f'{h:.3f}', ha='center', va='bottom', fontsize=9.5, color='#e0e0e0')

ax.set_xticks(x)
ax.set_xticklabels(metric_labels, fontsize=11)
ax.set_ylim(0, 0.75)
ax.set_ylabel('Score', fontsize=11)
ax.set_title('SafeCruise · Holdout vs Grouped CV Metrics Comparison', pad=12)
ax.legend(fontsize=10, framealpha=0.3)
ax.yaxis.grid(True, alpha=0.4)
ax.set_axisbelow(True)

plt.tight_layout()
plt.savefig('graph_metrics_comparison.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_metrics_comparison.png')


# %% [markdown]
# ## 7. Dataset Overview: Class Balance & Trips per Driver

# %% Graph 7: Dataset overview
support = {
    'Low (Normal)':      ev['grouped_cv']['per_class']['Low']['support'],
    'Medium (Drowsy)':   ev['grouped_cv']['per_class']['Medium']['support'],
    'High (Aggressive)': ev['grouped_cv']['per_class']['High']['support'],
}
total = sum(support.values())

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5.5))

wedges, texts, autotexts = ax1.pie(
    support.values(),
    labels=support.keys(),
    colors=['#22c55e', '#f59e0b', '#ef4444'],
    autopct='%1.1f%%',
    startangle=140,
    wedgeprops={'width': 0.55, 'edgecolor': '#1a1a2e', 'linewidth': 2},
    pctdistance=0.78,
)
for t in texts:     t.set_color('#e0e0e0')
for a in autotexts: a.set(color='white', fontweight='bold', fontsize=10)
ax1.text(0, 0, f'{total:,}\nsamples', ha='center', va='center',
         color='white', fontsize=11, fontweight='bold')
ax1.set_title('Class Distribution (Full Dataset)', pad=12)

drivers          = ['D1', 'D2', 'D3', 'D4', 'D5', 'D6']
trips_per_driver = [len([t for t in ev['trip_level']['per_trip']
                         if f'-{d}-' in t['trip']]) for d in drivers]

ax2.bar(drivers, trips_per_driver, color='#3b82f6', alpha=0.85,
        edgecolor='#1a1a2e', linewidth=0.5)
for i, n in enumerate(trips_per_driver):
    ax2.text(i, n + 0.05, str(n), ha='center', va='bottom',
             fontsize=11, color='#e0e0e0', fontweight='bold')
ax2.set_ylabel('Number of Trips', fontsize=11)
ax2.set_xlabel('Driver', fontsize=11)
ax2.set_title('Trip Count per Driver', pad=12)
ax2.yaxis.grid(True, alpha=0.4)
ax2.set_axisbelow(True)
ax2.set_ylim(0, max(trips_per_driver) + 2)

plt.suptitle('SafeCruise · Dataset Overview', fontsize=14, fontweight='bold',
             color='white', y=1.02)
plt.tight_layout()
plt.savefig('graph_dataset_overview.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_dataset_overview.png')


# %% [markdown]
# ## 8. XGBoost Hyperparameters Table

# %% Graph 8: Hyperparameters table
params      = meta['xgboost_params']
show_params = {
    'learning_rate':    params['learning_rate'],
    'max_depth':        params['max_depth'],
    'n_estimators':     params['n_estimators'],
    'colsample_bytree': params['colsample_bytree'],
    'subsample':        params['subsample'],
    'min_child_weight': params['min_child_weight'],
    'reg_lambda':       params['reg_lambda'],
    'objective':        params['objective'],
    'eval_metric':      params['eval_metric'],
    'tree_method':      params['tree_method'],
}

table_data = [[k, str(v)] for k, v in show_params.items()]

fig, ax = plt.subplots(figsize=(10, 4.5))
ax.axis('off')

the_table = ax.table(
    cellText=table_data,
    colLabels=['Hyperparameter', 'Value'],
    loc='center',
    cellLoc='center',
)
the_table.auto_set_font_size(False)
the_table.set_fontsize(11)
the_table.scale(1.4, 1.9)

for (row, col), cell in the_table.get_celld().items():
    cell.set_edgecolor('#0f3460')
    if row == 0:
        cell.set_facecolor('#e94560')
        cell.set_text_props(color='white', fontweight='bold')
    elif row % 2 == 0:
        cell.set_facecolor('#16213e')
        cell.set_text_props(color='#e0e0e0')
    else:
        cell.set_facecolor('#0f3460')
        cell.set_text_props(color='#e0e0e0')

ax.set_title('SafeCruise · XGBoost Hyperparameters', pad=16, fontsize=14, fontweight='bold')
plt.tight_layout()
plt.savefig('graph_hyperparameters.png', dpi=150, bbox_inches='tight', facecolor='#1a1a2e')
# plt.show()  # headless
print('Saved -> graph_hyperparameters.png')


# %% [markdown]
# ## Summary

# %% Summary
saved = sorted([f for f in os.listdir('.') if f.startswith('graph_') and f.endswith('.png')])
print(f"\n{'='*55}")
print(f"  SafeCruise · {len(saved)} Graphs Generated Successfully")
print(f"{'='*55}")
for s in saved:
    size_kb = os.path.getsize(s) // 1024
    print(f"  ✅  {s:<48} ({size_kb} KB)")
