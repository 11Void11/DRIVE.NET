
<!-- ================================================================ -->
<!--                         DRIVE.NET                                -->
<!-- ================================================================ -->

<a href="#">
<img src="https://capsule-render.vercel.app/api?type=waving&color=0:000000,55:2A2A2A,100:FF5A1F&height=240&section=header&text=SAFECRUISE&fontSize=68&fontColor=F2F2F2&fontAlignY=38&desc=Predictive%20Vehicle%20Safety%20%7C%20Explainable%20AI%20%7C%20Connected%20Vehicles&descAlignY=63&descSize=17&descColor=B8B8B8" width="100%"/>
</a>

<br>

<img src="https://readme-typing-svg.demolab.com?font=VT323&size=30&duration=2500&pause=900&color=FF5A1F&center=true&vCenter=true&width=950&height=80&lines=%3E+INITIALIZING+SAFECRUISE...;%3E+ANALYZING+DRIVER+BEHAVIOUR...;%3E+PREDICTING+VEHICLE+RISK...;%3E+EXPLAINING+AI+DECISIONS...;%3E+BUILDING+A+SAFER+JOURNEY..." />

<br>

<p align="center">

<img src="https://img.shields.io/badge/STATUS-RESEARCH%20PROTOTYPE-FF5A1F?style=for-the-badge&labelColor=000000"/>

<img src="https://img.shields.io/badge/ML-XGBOOST-F2F2F2?style=for-the-badge&labelColor=2A2A2A"/>

<img src="https://img.shields.io/badge/XAI-SHAP-FF5A1F?style=for-the-badge&labelColor=000000"/>

<img src="https://img.shields.io/badge/BACKEND-FASTAPI-F2F2F2?style=for-the-badge&labelColor=2A2A2A"/>

</p>

<br>

```text
╔════════════════════════ ⓘ ABOUT ════════════════════════════╗
║                                                              ║
║  DRIVE.NET is an AI-powered vehicle safety system that       ║
║  predicts driving and collision risks in real time. It uses  ║
║  ML, vehicle data, GPS, and smartphone sensors to generate   ║
║  risk scores, explain the causes using Explainable AI, and   ║
║  provide safety alerts through the phone and car infotainmen ║
║  system.                                                     ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝

┌───────────────── DRIVE.NET  ARCHITECTURE ────────────────────┐
│                                                              │
│  🚗 VEHICLE             📱 DRIVER          🌐 ENVIRONMEN    │
│  TELEMETRY              SENSORS               CONTEXT        │
│      │                     │                     │           │
│      └─────────────────────┼─────────────────────┘           │
│                            ▼                                 │
│                       DATA FUSION                            │
│                            │                                 │
│                            ▼                                 │
│                        ML ENGINE                             │
│                            │                                 │
│              ┌─────────────┼─────────────┐                   │
│              ▼             ▼             ▼                   │
│         DRIVER RISK   COLLISION RISK     XAI                 │
│              │             │             │                   │
│              └─────────────┼─────────────┘                   │
│                            ▼                                 │
│                      SAFETY ENGINE                           │
│                            │                                 │
│             ┌──────────────┼──────────────┐                  │
│             ▼              ▼              ▼                  │
│          📱 APP      🗺️ infotanement    ⚠️ ALERT            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```
<hr>



<h1> DRIVE.NET Aims for </h1>

```text 
┌───────────────────────────────────────────┐
│            WHY IS RISK HIGH?              │
├───────────────────────────────────────────┤
│                                           │
│ SPEED ABOVE LIMIT     ███████████  +24%   │
│ HARSH BRAKING         █████████    +19%   │
│ ACCELERATION          ████████     +17%   │
│ CORNERING             █████        +12%   │
│ TRAFFIC CONTEXT       ████          +9%   │
│                                           │
└───────────────────────────────────────────┘

AND TAKES REQUIRED ACTIONS
```
<h3>Works on both Andriod Auto & Andriod Phones</h3>
<img width="400" alt="image" align="center" src="https://github.com/user-attachments/assets/666cd467-5e17-4c87-92dd-d79bd4cb5c27" />

<hr>

<h2 align="left">> TECH.STACK</h2>

<div align="left">

<img src="https://skillicons.dev/icons?i=python,sklearn,pytorch,xgboost,pandas,numpy,matplotlib,kotlin,androidstudio,fastapi,sqlite,mysql,bluetooth&perline=7" />
<img src="https://img.shields.io/badge/SHAP-FF5A1F?style=for-the-badge&logoColor=white" />
<img src="https://img.shields.io/badge/OBD--II-0D0D0D?style=for-the-badge&logoColor=FF5A1F" />
<img src="https://img.shields.io/badge/REST-2A2A2A?style=for-the-badge&logoColor=FF5A1F" />
<img src="https://img.shields.io/badge/WebSocket-0D0D0D?style=for-the-badge&logoColor=FF5A1F" />
<img src="https://img.shields.io/badge/V2V-FF5A1F?style=for-the-badge&logoColor=white" />
<img src="https://img.shields.io/badge/Maps%20SDK-2A2A2A?style=for-the-badge&logo=googlemaps&logoColor=FF5A1F" />

</div>

<hr>

## 🌐 Drive.Net — Real-Time Connected Vehicle Safety & V2V System

Drive.Net upgrades SafeCruise with real-time multi-device Vehicle-to-Vehicle (V2V) spatial awareness, road geometry intelligence, and reproducible driving behaviour analysis.

### Key Capabilities:
- **Real-Time Multi-Device V2V Telemetry**:
  - Exchange real-time GNSS telemetry (latitude, longitude, altitude, speed, heading, accuracy) over REST and high-frequency WebSockets.
  - Geodesic spatial calculations using the Haversine formula and initial forward bearings.
  - GPS uncertainty tracking: distance tolerance reported as $(accuracy_{user} + accuracy_{peer})$.
  - Vehicle heading rotation on map overlays with stale device eviction (12-second heartbeat threshold).

- **Road Geometry & Mountain Switchback Awareness**:
  - Detects narrow mountain passes, winding switchbacks, and hairpin bends where opposing vehicles have low straight-line Euclidean distance but opposite headings ($\Delta\theta \approx 180^\circ$).
  - Emits cautious alerts: `"Caution: Vehicle approaching on a winding road. Maintain safe speed and stay in lane."`

- **Lateral Instability & Cornering Dynamics**:
  - Differentiates normal centripetal turning ($a_c = v \cdot \omega$) from abrupt lateral slide/swerve anomalies.
  - Calibrated safety notification: `"Possible unstable movement detected. Reduce speed when safe."`

- **UAH-DriveSet Model Benchmarking**:
  - Clean, leak-free Leave-One-Driver-Out Cross Validation across 6 drivers and 40 trips (294,073 sensor frames).
  - Outperforms baseline models with calibrated XGBoost (58.6% LODO CV accuracy on unseen drivers).
  - Dual format persistence: native XGBoost JSON (`safe_cruise_xgboost_model.json`) and scikit-learn pipeline pickle.

---

### 📊 Model Benchmark (UAH-DriveSet Evaluation)

| Model Architecture | Cross-Validation Accuracy | Macro F1-Score | Leakage Prevention |
| :--- | :--- | :--- | :--- |
| **Logistic Regression** | 50.33% | 0.4724 | Temporal split |
| **Random Forest (100 trees)** | 54.07% | 0.5299 | Temporal split |
| **XGBoost Classifier** | 54.82% | 0.5392 | Temporal split |
| **XGBoost (Leave-One-Driver-Out)** | **58.60%** | **0.5879** | **Strict Unseen Driver Holdout** |

---

### 🚀 Running the Project

#### 1. Backend Server & V2V Broker
```bash
# Install dependencies
pip install fastapi uvicorn websockets pydantic xgboost shap pytest scikit-learn

# Launch Drive.Net API
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

#### 2. Run Python & V2V Test Suite
```bash
python -m pytest test_drive_net.py -v
```

#### 3. Android Application (Drive.Net)
```bash
cd android_app
./gradlew compileDebugKotlin
```

---

<h1>DATASETS</h1>
<p><strong>UAH-DriveSet</strong>: Naturalistic driving dataset capturing smartphone telemetry, IMU acceleration, GPS, and lane camera tracking across highway and secondary routes.</p>
http://www.robesafe.uah.es/personal/eduardo.romera/uah-driveset/
<br><br>
<p><strong>NGSIM</strong>: Vehicle trajectory data collected on US-101 and I-80 for micro-level traffic behaviour analysis.</p>
https://www.kaggle.com/datasets/nigelwilliams/ngsim-vehicle-trajectory-data-us-101
<hr>
<p><strong>Version:</strong> Drive.Net v1.0.0 (DRIVE.NET Connected Vehicle Release)</p>





