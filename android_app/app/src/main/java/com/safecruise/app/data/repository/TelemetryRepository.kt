package com.safecruise.app.data.repository

import com.mapbox.geojson.Point
import com.safecruise.app.data.model.IncidentLog
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.data.model.TripRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelemetryRepository {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentTelemetry = MutableStateFlow<TelemetryPoint?>(null)
    val currentTelemetry: StateFlow<TelemetryPoint?> = _currentTelemetry.asStateFlow()

    private val _latestRiskResponse = MutableStateFlow<PredictResponse?>(
        PredictResponse(
            riskLevel = 0,
            riskLabel = "Ready",
            behaviour = "NORMAL",
            confidenceScores = mapOf("low_risk" to 1.0f, "medium_risk" to 0.0f, "high_risk" to 0.0f),
            topFeature = "Speed_kmh",
            shapImpacts = emptyMap(),
            calculatedScore = 0
        )
    )
    val latestRiskResponse: StateFlow<PredictResponse?> = _latestRiskResponse.asStateFlow()

    private val _bufferSize = MutableStateFlow(0)
    val bufferSize: StateFlow<Int> = _bufferSize.asStateFlow()

    private val _bufferSpanSeconds = MutableStateFlow(0f)
    val bufferSpanSeconds: StateFlow<Float> = _bufferSpanSeconds.asStateFlow()

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    private val _mlEngineStatus = MutableStateFlow("ONLINE")
    val mlEngineStatus: StateFlow<String> = _mlEngineStatus.asStateFlow()

    // Trip Aggregates
    private val _tripDistanceKm = MutableStateFlow(0.0f)
    val tripDistanceKm: StateFlow<Float> = _tripDistanceKm.asStateFlow()

    private val _tripDurationSeconds = MutableStateFlow(0L)
    val tripDurationSeconds: StateFlow<Long> = _tripDurationSeconds.asStateFlow()

    private val _tripAvgSpeedKph = MutableStateFlow(0.0f)
    val tripAvgSpeedKph: StateFlow<Float> = _tripAvgSpeedKph.asStateFlow()

    private val _tripMaxSpeedKph = MutableStateFlow(0.0f)
    val tripMaxSpeedKph: StateFlow<Float> = _tripMaxSpeedKph.asStateFlow()

    private val _satellitesCount = MutableStateFlow(0)
    val satellitesCount: StateFlow<Int> = _satellitesCount.asStateFlow()

    private val _hdop = MutableStateFlow(1.0f)
    val hdop: StateFlow<Float> = _hdop.asStateFlow()

    private val _locationLabel = MutableStateFlow("ACQUIRING GPS...")
    val locationLabel: StateFlow<String> = _locationLabel.asStateFlow()

    // Map Breadcrumb Route Points
    private val _routePoints = MutableStateFlow<List<Point>>(emptyList())
    val routePoints: StateFlow<List<Point>> = _routePoints.asStateFlow()

    // Waypoints for flagged risk events
    private val _riskEventPoints = MutableStateFlow<List<Point>>(emptyList())
    val riskEventPoints: StateFlow<List<Point>> = _riskEventPoints.asStateFlow()

    // Developer Tunables & Simulation Controls
    private val _harshBrakingThreshold = MutableStateFlow(-3.4f)
    val harshBrakingThreshold: StateFlow<Float> = _harshBrakingThreshold.asStateFlow()

    private val _harshAccelThreshold = MutableStateFlow(3.4f)
    val harshAccelThreshold: StateFlow<Float> = _harshAccelThreshold.asStateFlow()

    private val _harshSwerveThreshold = MutableStateFlow(3.4f)
    val harshSwerveThreshold: StateFlow<Float> = _harshSwerveThreshold.asStateFlow()

    private val _mockSpeedKmh = MutableStateFlow(0f)
    val mockSpeedKmh: StateFlow<Float> = _mockSpeedKmh.asStateFlow()

    fun setHarshBrakingThreshold(v: Float) {
        _harshBrakingThreshold.value = v
    }

    fun setHarshAccelThreshold(v: Float) {
        _harshAccelThreshold.value = v
    }

    fun setHarshSwerveThreshold(v: Float) {
        _harshSwerveThreshold.value = v
    }

    // Nearby Vehicles & Proximity Telemetry
    private val _nearbyVehicles = MutableStateFlow<List<com.safecruise.app.data.model.NearbyVehicle>>(emptyList())
    val nearbyVehicles: StateFlow<List<com.safecruise.app.data.model.NearbyVehicle>> = _nearbyVehicles.asStateFlow()

    // Temporary Pop-up Alert (Dismisses after 3 seconds)
    private val _proximityAlert = MutableStateFlow<String?>(null)
    val proximityAlert: StateFlow<String?> = _proximityAlert.asStateFlow()

    // V2V Live Communication & Safety States
    private val _v2vConnectionStatus = MutableStateFlow("OFFLINE")
    val v2vConnectionStatus: StateFlow<String> = _v2vConnectionStatus.asStateFlow()

    private val _v2vActiveCount = MutableStateFlow(0)
    val v2vActiveCount: StateFlow<Int> = _v2vActiveCount.asStateFlow()

    private val _isLocationSharingEnabled = MutableStateFlow(true)
    val isLocationSharingEnabled: StateFlow<Boolean> = _isLocationSharingEnabled.asStateFlow()

    private val _vehicleSessionId = MutableStateFlow("dn-" + java.util.UUID.randomUUID().toString().substring(0, 8))
    val vehicleSessionId: StateFlow<String> = _vehicleSessionId.asStateFlow()

    private val _roadHazardWarning = MutableStateFlow<String?>(null)
    val roadHazardWarning: StateFlow<String?> = _roadHazardWarning.asStateFlow()

    private val _isLateralUnstable = MutableStateFlow(false)
    val isLateralUnstable: StateFlow<Boolean> = _isLateralUnstable.asStateFlow()

    fun setV2VConnectionStatus(status: String) {
        _v2vConnectionStatus.value = status
    }

    fun setV2VActiveCount(count: Int) {
        _v2vActiveCount.value = count
    }

    fun setLocationSharingEnabled(enabled: Boolean) {
        _isLocationSharingEnabled.value = enabled
    }

    fun setRoadHazardWarning(warning: String?) {
        _roadHazardWarning.value = warning
    }

    fun setLateralUnstable(unstable: Boolean) {
        _isLateralUnstable.value = unstable
    }

    private val _activeScenario = MutableStateFlow("NONE")
    val activeScenario: StateFlow<String> = _activeScenario.asStateFlow()

    fun setNearbyVehicles(vehicles: List<com.safecruise.app.data.model.NearbyVehicle>) {
        _nearbyVehicles.value = vehicles
    }

    fun setProximityAlert(alert: String?) {
        _proximityAlert.value = alert
    }

    fun setActiveScenario(scenario: String) {
        _activeScenario.value = scenario
    }

    // Live Incident & Event Logs
    private val _incidentLogs = MutableStateFlow<List<IncidentLog>>(
        listOf(
            IncidentLog(
                title = "Drive.Net Initialized",
                severity = 0,
                detail = "Sensors calibrated at 10Hz, GNSS & V2V ready",
                speedKmh = 0f,
                locationLabel = "System",
                timestamp = System.currentTimeMillis() - 180000L
            ),
            IncidentLog(
                title = "GPS Satellite Fix Acquired",
                severity = 0,
                detail = "High precision lock • 1.1M HDOP",
                speedKmh = 0f,
                locationLabel = "GPS Receiver",
                timestamp = System.currentTimeMillis() - 120000L
            )
        )
    )
    val incidentLogs: StateFlow<List<IncidentLog>> = _incidentLogs.asStateFlow()

    // Trip History (Real recorded drives)
    private val _tripHistory = MutableStateFlow<List<TripRecord>>(emptyList())
    val tripHistory: StateFlow<List<TripRecord>> = _tripHistory.asStateFlow()

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
        if (tracking) {
            _apiError.value = null
            addIncidentLog(
                IncidentLog(
                    title = "Trip Recording Started",
                    severity = 0,
                    detail = "Continuous telemetry & risk scoring active",
                    speedKmh = _currentTelemetry.value?.speedKmh ?: 0f,
                    locationLabel = _locationLabel.value
                )
            )
        } else {
            // Save completed trip if distance > 0.05 km
            if (_tripDistanceKm.value > 0.05f) {
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val record = TripRecord(
                    dateStr = "Today, ${timeFormat.format(Date())}",
                    distanceKm = _tripDistanceKm.value,
                    durationSec = _tripDurationSeconds.value,
                    avgSpeedKph = _tripAvgSpeedKph.value,
                    maxSpeedKph = _tripMaxSpeedKph.value,
                    riskScore = _latestRiskResponse.value?.riskScore ?: 10,
                    status = when (_latestRiskResponse.value?.riskLevel) {
                        2 -> "High Risk"
                        1 -> "Moderate"
                        else -> "Optimal"
                    },
                    startLocation = _locationLabel.value,
                    endLocation = _locationLabel.value
                )
                saveTripRecord(record)
            }
        }
    }

    fun updateCurrentTelemetry(point: TelemetryPoint, currentBufferSize: Int, spanSeconds: Float) {
        _currentTelemetry.value = point
        _bufferSize.value = currentBufferSize
        _bufferSpanSeconds.value = spanSeconds
        if (point.speedKmh > _tripMaxSpeedKph.value) {
            _tripMaxSpeedKph.value = point.speedKmh
        }
    }

    fun updateRiskResponse(response: PredictResponse) {
        _latestRiskResponse.value = response
        _mlEngineStatus.value = "ONLINE"
        _apiError.value = null

        // If sudden warning / high risk, log incident
        if (response.riskLevel >= 2) {
            addIncidentLog(
                IncidentLog(
                    title = response.detailDescription,
                    severity = 2,
                    detail = "Risk Score: ${response.riskScore} • High lateral/longitudinal jerk",
                    speedKmh = _currentTelemetry.value?.speedKmh ?: 0f,
                    locationLabel = _locationLabel.value
                )
            )
        } else if (response.riskLevel == 1) {
            addIncidentLog(
                IncidentLog(
                    title = response.detailDescription,
                    severity = 1,
                    detail = "Risk Score: ${response.riskScore}",
                    speedKmh = _currentTelemetry.value?.speedKmh ?: 0f,
                    locationLabel = _locationLabel.value
                )
            )
        }
    }

    fun setError(message: String?) {
        _apiError.value = message
    }

    fun setMlEngineStatus(status: String) {
        _mlEngineStatus.value = status
    }

    fun updateTripMetrics(distanceKm: Float, durationSec: Long, avgSpeedKph: Float) {
        _tripDistanceKm.value = distanceKm
        _tripDurationSeconds.value = durationSec
        _tripAvgSpeedKph.value = avgSpeedKph
    }

    fun updateGpsStatus(sats: Int, hdopVal: Float, label: String?) {
        _satellitesCount.value = sats
        _hdop.value = hdopVal
        if (!label.isNullOrBlank()) {
            _locationLabel.value = label
        }
    }

    fun setRoutePoints(points: List<Point>) {
        _routePoints.value = points
    }

    fun addRoutePoint(point: Point) {
        val current = _routePoints.value.toMutableList()
        current.add(point)
        _routePoints.value = current
    }

    fun addRiskEventPoint(point: Point) {
        val current = _riskEventPoints.value.toMutableList()
        current.add(point)
        _riskEventPoints.value = current
    }

    fun addIncidentLog(log: IncidentLog) {
        val list = _incidentLogs.value.toMutableList()
        list.add(0, log) // Newest first
        if (list.size > 100) list.removeAt(list.lastIndex)
        _incidentLogs.value = list
    }

    fun clearLogs() {
        _incidentLogs.value = emptyList()
    }

    fun saveTripRecord(record: TripRecord) {
        val list = _tripHistory.value.toMutableList()
        list.add(0, record)
        _tripHistory.value = list
    }

    fun resetTrip() {
        _tripDistanceKm.value = 0.0f
        _tripDurationSeconds.value = 0L
        _tripAvgSpeedKph.value = 0.0f
        _tripMaxSpeedKph.value = 0.0f
        _routePoints.value = emptyList()
        _riskEventPoints.value = emptyList()
        _latestRiskResponse.value = PredictResponse(
            riskLevel = 0,
            riskLabel = "Ready",
            behaviour = "NORMAL",
            confidenceScores = mapOf("low_risk" to 1.0f, "medium_risk" to 0.0f, "high_risk" to 0.0f),
            topFeature = "Speed_kmh",
            shapImpacts = emptyMap(),
            calculatedScore = 0
        )
        addIncidentLog(
            IncidentLog(
                title = "Trip Reset",
                severity = 0,
                detail = "Trip statistics cleared",
                speedKmh = 0f,
                locationLabel = _locationLabel.value
            )
        )
    }
}
