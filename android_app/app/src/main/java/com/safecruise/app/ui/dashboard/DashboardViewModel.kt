package com.safecruise.app.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.safecruise.app.data.api.RetrofitClient
import com.safecruise.app.data.model.IncidentLog
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.data.model.TripRecord
import com.safecruise.app.data.repository.DriverProfileRepository
import com.safecruise.app.data.repository.TelemetryRepository
import com.safecruise.app.service.TelemetryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel : ViewModel() {

    val driverName: StateFlow<String> = DriverProfileRepository.driverName
    val isTracking: StateFlow<Boolean> = TelemetryRepository.isTracking
    val currentTelemetry: StateFlow<TelemetryPoint?> = TelemetryRepository.currentTelemetry
    val latestRiskResponse: StateFlow<PredictResponse?> = TelemetryRepository.latestRiskResponse
    val bufferSize: StateFlow<Int> = TelemetryRepository.bufferSize
    val bufferSpanSeconds: StateFlow<Float> = TelemetryRepository.bufferSpanSeconds
    val apiError: StateFlow<String?> = TelemetryRepository.apiError
    val mlEngineStatus: StateFlow<String> = TelemetryRepository.mlEngineStatus

    val tripDistanceKm: StateFlow<Float> = TelemetryRepository.tripDistanceKm
    val tripDurationSeconds: StateFlow<Long> = TelemetryRepository.tripDurationSeconds
    val tripAvgSpeedKph: StateFlow<Float> = TelemetryRepository.tripAvgSpeedKph
    val tripMaxSpeedKph: StateFlow<Float> = TelemetryRepository.tripMaxSpeedKph
    val satellitesCount: StateFlow<Int> = TelemetryRepository.satellitesCount
    val hdop: StateFlow<Float> = TelemetryRepository.hdop
    val locationLabel: StateFlow<String> = TelemetryRepository.locationLabel

    val routePoints: StateFlow<List<Point>> = TelemetryRepository.routePoints
    val riskEventPoints: StateFlow<List<Point>> = TelemetryRepository.riskEventPoints

    val incidentLogs: StateFlow<List<IncidentLog>> = TelemetryRepository.incidentLogs
    val tripHistory: StateFlow<List<TripRecord>> = TelemetryRepository.tripHistory

    val harshBrakingThreshold: StateFlow<Float> = TelemetryRepository.harshBrakingThreshold
    val harshAccelThreshold: StateFlow<Float> = TelemetryRepository.harshAccelThreshold
    val harshSwerveThreshold: StateFlow<Float> = TelemetryRepository.harshSwerveThreshold

    val nearbyVehicles: StateFlow<List<com.safecruise.app.data.model.NearbyVehicle>> = TelemetryRepository.nearbyVehicles
    val proximityAlert: StateFlow<String?> = TelemetryRepository.proximityAlert
    val activeScenario: StateFlow<String> = TelemetryRepository.activeScenario
    val v2vConnectionStatus: StateFlow<String> = TelemetryRepository.v2vConnectionStatus
    val v2vActiveCount: StateFlow<Int> = TelemetryRepository.v2vActiveCount
    val isLocationSharingEnabled: StateFlow<Boolean> = TelemetryRepository.isLocationSharingEnabled
    val roadHazardWarning: StateFlow<String?> = TelemetryRepository.roadHazardWarning

    fun setLocationSharingEnabled(enabled: Boolean) {
        TelemetryRepository.setLocationSharingEnabled(enabled)
    }

    fun setHarshBrakingThreshold(v: Float) = TelemetryRepository.setHarshBrakingThreshold(v)
    fun setHarshAccelThreshold(v: Float) = TelemetryRepository.setHarshAccelThreshold(v)
    fun setHarshSwerveThreshold(v: Float) = TelemetryRepository.setHarshSwerveThreshold(v)

    fun updateDriverName(context: Context, newName: String): Result<String> {
        return DriverProfileRepository.updateDriverName(context, newName)
    }

    fun setScenario(scenario: String) {
        val currentLoc = currentTelemetry.value
        val lat = if (currentLoc != null && currentLoc.latitude != 0.0) currentLoc.latitude else 17.7231
        val lng = if (currentLoc != null && currentLoc.longitude != 0.0) currentLoc.longitude else 83.3082
        val heading = currentLoc?.heading ?: 0f

        when (scenario) {
            "AGGRESSIVE_TAILGATER" -> {
                // Vehicle 45m behind moving fast (+15 km/h faster than us)
                // Offset latitude slightly backward based on heading
                val rad = Math.toRadians(heading.toDouble())
                val dLat = -0.00040 * Math.cos(rad)
                val dLng = -0.00040 * Math.sin(rad)
                val v = com.safecruise.app.data.model.NearbyVehicle(
                    id = "v-tailgater-1",
                    name = "Black Sedan",
                    latitude = lat + dLat,
                    longitude = lng + dLng,
                    speedKmh = (currentLoc?.speedKmh ?: 35f) + 18f,
                    distanceMeters = 42f,
                    bearingDegrees = heading,
                    relativePosition = "BEHIND",
                    isAggressive = true,
                    aggressiveReason = "High-speed tailgater approaching rapidly"
                )
                TelemetryRepository.setNearbyVehicles(listOf(v))
                TelemetryRepository.setActiveScenario(scenario)
                triggerAlert("⚠️ Vehicle behind is approaching aggressively (42m). Prepare to yield lane.")
            }
            "AGGRESSIVE_SWERVE_AHEAD" -> {
                // Vehicle 65m ahead swerving / high risk
                val rad = Math.toRadians(heading.toDouble())
                val dLat = 0.00058 * Math.cos(rad)
                val dLng = 0.00058 * Math.sin(rad)
                val v = com.safecruise.app.data.model.NearbyVehicle(
                    id = "v-swerve-2",
                    name = "White SUV",
                    latitude = lat + dLat,
                    longitude = lng + dLng,
                    speedKmh = (currentLoc?.speedKmh ?: 30f) - 8f,
                    distanceMeters = 65f,
                    bearingDegrees = heading,
                    relativePosition = "AHEAD",
                    isAggressive = true,
                    aggressiveReason = "Erratic swerving & heavy deceleration detected"
                )
                TelemetryRepository.setNearbyVehicles(listOf(v))
                TelemetryRepository.setActiveScenario(scenario)
                triggerAlert("⚠️ Vehicle ahead swerving erratically (65m). Increase following distance.")
            }
            "PEACEFUL_CONVOY" -> {
                // Two safe vehicles around within 80m
                val rad = Math.toRadians(heading.toDouble())
                val v1 = com.safecruise.app.data.model.NearbyVehicle(
                    id = "v-safe-1",
                    name = "Blue Hatchback",
                    latitude = lat + (0.00050 * Math.cos(rad)),
                    longitude = lng + (0.00050 * Math.sin(rad)),
                    speedKmh = currentLoc?.speedKmh ?: 32f,
                    distanceMeters = 55f,
                    bearingDegrees = heading,
                    relativePosition = "AHEAD",
                    isAggressive = false
                )
                val v2 = com.safecruise.app.data.model.NearbyVehicle(
                    id = "v-safe-2",
                    name = "Silver Compact",
                    latitude = lat - (0.00065 * Math.cos(rad)),
                    longitude = lng - (0.00065 * Math.sin(rad)),
                    speedKmh = currentLoc?.speedKmh ?: 30f,
                    distanceMeters = 72f,
                    bearingDegrees = heading,
                    relativePosition = "BEHIND",
                    isAggressive = false
                )
                TelemetryRepository.setNearbyVehicles(listOf(v1, v2))
                TelemetryRepository.setActiveScenario(scenario)
                triggerAlert("ℹ️ 2 nearby vehicles detected within 80m. Traffic flowing normally.")
            }
            "CLEAR", "NONE" -> {
                TelemetryRepository.setNearbyVehicles(emptyList())
                TelemetryRepository.setActiveScenario("NONE")
                TelemetryRepository.setProximityAlert(null)
            }
        }
    }

    private var alertJob: kotlinx.coroutines.Job? = null
    fun triggerAlert(message: String) {
        alertJob?.cancel()
        TelemetryRepository.setProximityAlert(message)
        alertJob = viewModelScope.launch {
            kotlinx.coroutines.delay(3000) // Auto-dismiss after 3 seconds as requested
            TelemetryRepository.setProximityAlert(null)
        }
    }

    fun toggleTracking(context: Context) {
        val intent = Intent(context, TelemetryService::class.java)
        if (isTracking.value) {
            intent.action = TelemetryService.ACTION_STOP
            context.startService(intent)
        } else {
            intent.action = TelemetryService.ACTION_START
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun resetTrip(context: Context) {
        val intent = Intent(context, TelemetryService::class.java).apply {
            action = TelemetryService.ACTION_RESET
        }
        context.startService(intent)
    }

    fun clearLogs() {
        TelemetryRepository.clearLogs()
    }

    fun setServerUrl(url: String) {
        RetrofitClient.baseUrl = url
    }

    fun getServerUrl(): String = RetrofitClient.baseUrl

    fun pingServer(url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.baseUrl = url
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val target = if (url.endsWith("/")) url else "$url/"
                val request = okhttp3.Request.Builder()
                    .url(target)
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        TelemetryRepository.setMlEngineStatus("ONLINE")
                        onResult(true, "Connected: HTTP 200 OK • $body")
                    } else {
                        onResult(false, "Server responded with HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to connect: ${e.localizedMessage ?: "Timeout"}")
                }
            }
        }
    }
}
