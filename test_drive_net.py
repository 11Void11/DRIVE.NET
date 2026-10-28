"""Drive.Net Comprehensive Test Suite.

Tests:
  1. Geodesic distance (Haversine), bearing, relative orientation, and uncertainty bounds.
  2. V2V multi-device session registry, coordinate validation, and stale vehicle eviction.
  3. Mountain road, winding road, and hairpin bend awareness logic.
  4. Lateral dynamics, normal cornering differentiation, and lateral instability warnings.
  5. API contracts, V2V endpoints, and ML inference serving.
"""

import math
import time
import pytest
from fastapi.testclient import TestClient

import v2v_engine as V
from main import app

client = TestClient(app)


# --------------------------------------------------------------------------- #
# 1. Geodesic & Spatial Mathematics Tests
# --------------------------------------------------------------------------- #
def test_haversine_distance_known_benchmarks():
    """Verify Haversine against known geographic coordinates."""
    # Paris (48.8566, 2.3522) to London (51.5074, -0.1278) ~ 343 km
    dist_paris_london = V.haversine_distance(48.8566, 2.3522, 51.5074, -0.1278)
    assert 340000.0 < dist_paris_london < 346000.0

    # Same point distance should be zero
    dist_zero = V.haversine_distance(37.7749, -122.4194, 37.7749, -122.4194)
    assert dist_zero == 0.0

    # 1 degree of latitude is roughly 111.19 km
    dist_1deg_lat = V.haversine_distance(0.0, 0.0, 1.0, 0.0)
    assert 111000.0 < dist_1deg_lat < 111500.0


def test_initial_bearing_cardinal_directions():
    """Verify forward bearing calculations for cardinal directions."""
    # North
    b_north = V.initial_bearing(0.0, 0.0, 1.0, 0.0)
    assert abs(b_north - 0.0) < 0.1

    # East
    b_east = V.initial_bearing(0.0, 0.0, 0.0, 1.0)
    assert abs(b_east - 90.0) < 0.1

    # South
    b_south = V.initial_bearing(1.0, 0.0, 0.0, 0.0)
    assert abs(b_south - 180.0) < 0.1

    # West
    b_west = V.initial_bearing(0.0, 1.0, 0.0, 0.0)
    assert abs(b_west - 270.0) < 0.1


def test_determine_relative_position():
    """Verify relative categorization (Ahead, Behind, Left, Right)."""
    # User heading North (0 deg)
    assert V.determine_relative_position(0.0, 0.0) == "AHEAD"
    assert V.determine_relative_position(0.0, 90.0) == "RIGHT"
    assert V.determine_relative_position(0.0, 180.0) == "BEHIND"
    assert V.determine_relative_position(0.0, 270.0) == "LEFT"

    # User heading East (90 deg)
    assert V.determine_relative_position(90.0, 90.0) == "AHEAD"
    assert V.determine_relative_position(90.0, 180.0) == "RIGHT"
    assert V.determine_relative_position(90.0, 270.0) == "BEHIND"
    assert V.determine_relative_position(90.0, 0.0) == "LEFT"


def test_heading_difference():
    assert V.heading_difference(10.0, 20.0) == 10.0
    assert V.heading_difference(355.0, 5.0) == 10.0
    assert V.heading_difference(0.0, 180.0) == 180.0
    assert V.heading_difference(90.0, 270.0) == 180.0


# --------------------------------------------------------------------------- #
# 2. V2V Multi-Device Registry & Validation Tests
# --------------------------------------------------------------------------- #
def test_v2v_registry_registration_and_validation():
    registry = V.V2VRegistry(session_timeout_seconds=5.0, max_radius_meters=1000.0)
    now = time.time()

    # Valid vehicle 1
    v1 = V.LocationUpdate(
        vehicle_id="v-device-1",
        display_name="Device 1",
        latitude=37.7749,
        longitude=-122.4194,
        accuracy_m=4.0,
        speed_kmh=45.0,
        heading_deg=90.0,
        timestamp_s=now
    )
    accepted, msg = registry.register_update(v1)
    assert accepted is True
    assert registry.active_vehicle_count() == 1

    # Invalid coordinates (latitude > 90)
    with pytest.raises(Exception):
        V.LocationUpdate(
            vehicle_id="v-bad-lat",
            latitude=95.0,
            longitude=0.0,
            timestamp_s=now
        )

    # Excessive inaccuracy (>100m) rejected
    v_inaccurate = V.LocationUpdate(
        vehicle_id="v-device-inaccurate",
        latitude=37.7750,
        longitude=-122.4190,
        accuracy_m=120.0,
        timestamp_s=now
    )
    accepted_bad, msg = registry.register_update(v_inaccurate)
    assert accepted_bad is False


def test_v2v_stale_vehicle_eviction():
    registry = V.V2VRegistry(session_timeout_seconds=2.0)
    t0 = 1000.0

    v1 = V.LocationUpdate(
        vehicle_id="v-stale",
        latitude=37.7749,
        longitude=-122.4194,
        timestamp_s=t0
    )
    registry.register_update(v1, current_time=t0)
    assert registry.active_vehicle_count(current_time=t0) == 1

    # Cleanup at t0 + 1s (still active)
    evicted = registry.cleanup_stale_vehicles(current_time=t0 + 1.0)
    assert evicted == 0
    assert registry.active_vehicle_count(current_time=t0 + 1.0) == 1

    # Cleanup at t0 + 3s (past 2s timeout)
    evicted = registry.cleanup_stale_vehicles(current_time=t0 + 3.0)
    assert evicted == 1
    assert registry.active_vehicle_count(current_time=t0 + 3.0) == 0


def test_v2v_nearby_vehicle_query_and_geo_filtering():
    registry = V.V2VRegistry(max_radius_meters=500.0)
    now = time.time()

    # Vehicle 1 at origin
    v1 = V.LocationUpdate(
        vehicle_id="v-user",
        latitude=37.7749,
        longitude=-122.4194,
        heading_deg=0.0,
        timestamp_s=now
    )
    registry.register_update(v1)

    # Vehicle 2 nearby (~110m North)
    v2 = V.LocationUpdate(
        vehicle_id="v-nearby",
        latitude=37.7759,
        longitude=-122.4194,
        speed_kmh=40.0,
        heading_deg=0.0,
        timestamp_s=now
    )
    registry.register_update(v2)

    # Vehicle 3 far away (~10 km)
    v3 = V.LocationUpdate(
        vehicle_id="v-far",
        latitude=37.8749,
        longitude=-122.4194,
        timestamp_s=now
    )
    registry.register_update(v3)

    nearby = registry.get_nearby_vehicles_for("v-user")
    assert len(nearby) == 1
    assert nearby[0].id == "v-nearby"
    assert 100.0 < nearby[0].distance_meters < 125.0
    assert nearby[0].relative_position == "AHEAD"


# --------------------------------------------------------------------------- #
# 3. Mountain Road & Hairpin Bend Awareness Tests
# --------------------------------------------------------------------------- #
def test_mountain_hairpin_switchback_detection():
    now = time.time()
    # User moving uphill at heading 90 deg (East), turning into switchback
    u_update = V.LocationUpdate(
        vehicle_id="user",
        latitude=46.5000,
        longitude=8.5000,
        speed_kmh=35.0,
        heading_deg=90.0,
        accuracy_m=4.0,
        yaw_rate_rads=0.35,
        timestamp_s=now
    )
    user_state = V.VehicleState(u_update, now)

    # Other vehicle 60m away heading 270 deg (West, opposing) on adjacent tier
    o_update = V.LocationUpdate(
        vehicle_id="other",
        latitude=46.5005,
        longitude=8.5000,
        speed_kmh=32.0,
        heading_deg=270.0,
        accuracy_m=4.0,
        yaw_rate_rads=-0.30,
        timestamp_s=now
    )
    other_state = V.VehicleState(o_update, now)

    dist = V.haversine_distance(user_state.latitude, user_state.longitude,
                                other_state.latitude, other_state.longitude)

    road_context, warning = V.evaluate_mountain_and_hairpin_context(user_state, other_state, dist)
    assert road_context == "HAIRPIN_BEND"
    assert warning == "Caution: Vehicle approaching on a winding road."


def test_mountain_low_accuracy_handling():
    now = time.time()
    u_update = V.LocationUpdate(
        vehicle_id="user",
        latitude=46.5000,
        longitude=8.5000,
        accuracy_m=35.0,  # degraded accuracy in mountain valley
        timestamp_s=now
    )
    user_state = V.VehicleState(u_update, now)

    o_update = V.LocationUpdate(
        vehicle_id="other",
        latitude=46.5005,
        longitude=8.5000,
        accuracy_m=5.0,
        timestamp_s=now
    )
    other_state = V.VehicleState(o_update, now)

    road_context, warning = V.evaluate_mountain_and_hairpin_context(user_state, other_state, 50.0)
    assert road_context == "LOW_ACCURACY"
    assert "Distance estimate may be unreliable" in warning


# --------------------------------------------------------------------------- #
# 4. Lateral Dynamics & Instability Evaluation Tests
# --------------------------------------------------------------------------- #
def test_lateral_stability_normal_turn():
    """A smooth highway turn: 60 km/h (16.7 m/s) with yaw rate 0.15 rad/s gives
    expected centripetal a_c ~ 2.5 m/s^2 (~0.25g). Residual is small -> Normal."""
    speed_kmh = 60.0
    lat_accel_g = 0.26  # ~2.55 m/s^2
    yaw_rate = 0.15
    is_unstable, warning = V.evaluate_lateral_instability(
        speed_kmh=speed_kmh,
        lateral_accel_g=lat_accel_g,
        yaw_rate_rads=yaw_rate,
        accuracy_m=4.0
    )
    assert is_unstable is False
    assert warning is None


def test_lateral_instability_sudden_slide_or_swerve():
    """High speed with large lateral force (0.65g ~ 6.37 m/s^2) or huge residual
    mismatch -> Potential unstable movement."""
    speed_kmh = 75.0
    lat_accel_g = 0.65
    yaw_rate = 0.05  # Vehicle body hasn't rotated yet; sliding laterally
    is_unstable, warning = V.evaluate_lateral_instability(
        speed_kmh=speed_kmh,
        lateral_accel_g=lat_accel_g,
        yaw_rate_rads=yaw_rate,
        accuracy_m=4.0
    )
    assert is_unstable is True
    assert "Possible unstable movement detected" in warning


# --------------------------------------------------------------------------- #
# 5. FastAPI Endpoints Integration Tests
# --------------------------------------------------------------------------- #
def test_api_home_and_schema():
    res = client.get("/")
    assert res.status_code == 200
    data = res.json()
    assert data["application"] == "Drive.Net"
    assert "Drive.Net Backend is Online!" in data["status"]
    assert "v2v_active_vehicles" in data

    schema_res = client.get("/schema")
    assert schema_res.status_code == 200
    schema = schema_res.json()
    assert "required_input_columns" in schema
    assert "model_features" in schema


def test_api_v2v_location_exchange():
    now = time.time()
    # Post location for vehicle A
    payload_a = {
        "vehicle_id": "car-A",
        "display_name": "Tesla Model 3",
        "latitude": 37.7749,
        "longitude": -122.4194,
        "accuracy_m": 4.5,
        "speed_kmh": 50.0,
        "heading_deg": 45.0,
        "timestamp_s": now
    }
    res_a = client.post("/api/v2v/location", json=payload_a)
    assert res_a.status_code == 200
    data_a = res_a.json()
    assert data_a["status"] == "ok"
    assert data_a["vehicle_id"] == "car-A"

    # Post location for vehicle B (80m away)
    payload_b = {
        "vehicle_id": "car-B",
        "display_name": "Honda Civic",
        "latitude": 37.7755,
        "longitude": -122.4194,
        "accuracy_m": 3.8,
        "speed_kmh": 48.0,
        "heading_deg": 45.0,
        "timestamp_s": now
    }
    res_b = client.post("/api/v2v/location", json=payload_b)
    assert res_b.status_code == 200
    data_b = res_b.json()
    assert data_b["nearby_count"] >= 1
    assert data_b["nearby_vehicles"][0]["id"] == "car-A"

    # Check status endpoint
    status_res = client.get("/api/v2v/status")
    assert status_res.status_code == 200
    assert status_res.json()["active_vehicle_count"] >= 2

    # Leave session
    leave_res = client.post("/api/v2v/session/leave?vehicle_id=car-A")
    assert leave_res.status_code == 200
    assert leave_res.json()["status"] == "unregistered"
