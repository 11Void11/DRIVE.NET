package com.safecruise.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import com.safecruise.app.MainActivity
import com.safecruise.app.data.api.RetrofitClient
import com.safecruise.app.data.model.PredictRequest
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.data.repository.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class TelemetryService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var linearAccelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // Raw sensor values (m/s^2)
    @Volatile private var currentAccelX = 0.0f
    @Volatile private var currentAccelY = 0.0f
    @Volatile private var currentAccelZ = 9.81f

    // Linear dynamic acceleration (gravity removed, m/s^2)
    @Volatile private var currentLinearX = 0.0f
    @Volatile private var currentLinearY = 0.0f
    @Volatile private var currentLinearZ = 0.0f

    // High-pass gravity filter components (used if linear accelerometer sensor is unavailable)
    private var gravityX = 0.0f
    private var gravityY = 0.0f
    private var gravityZ = 9.81f

    @Volatile private var currentPitch = 0.0f
    @Volatile private var currentYaw = 0.0f
    @Volatile private var currentGyroRate = 0.0f

    // Real GPS telemetry
    @Volatile private var currentSpeedKmh = 0.0f
    @Volatile private var currentLatitude = 0.0
    @Volatile private var currentLongitude = 0.0
    @Volatile private var currentAltitude = 0.0
    @Volatile private var currentHeading = 0.0f

    private var previousLocation: android.location.Location? = null
    private var tripStartTimeMs = 0L
    private var totalDistanceMeters = 0.0
    private var isCurrentlyTracking = false

    // 60-second rolling buffer at 10Hz (max 600 items)
    private val bufferCapacity = 600
    private val rollingBuffer = ArrayDeque<TelemetryPoint>(bufferCapacity)

    private var samplingJob: Job? = null
    private var apiJob: Job? = null
    private var statsJob: Job? = null
    private var v2vJob: Job? = null
    @Volatile private var currentAccuracyMeters = 5.0f

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            processLocationUpdate(location)
        }
    }

    private fun processLocationUpdate(location: android.location.Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        if (location.hasAltitude()) {
            currentAltitude = location.altitude
        }

        var calculatedSpeed = 0.0f

        // 1. Direct hardware GPS speed
        if (location.hasSpeed()) {
            calculatedSpeed = location.speed * 3.6f
            // Filter stationary GPS jitter (< 1.5 km/h)
            if (calculatedSpeed < 1.5f) {
                calculatedSpeed = 0.0f
            }
        } else {
            // 2. Fallback: point-to-point calculation
            previousLocation?.let { prev ->
                val distanceMeters = prev.distanceTo(location)
                val timeDeltaSeconds = (location.time - prev.time) / 1000.0f

                if (timeDeltaSeconds in 0.2f..10.0f && distanceMeters > 1.2f) {
                    val calc = (distanceMeters / timeDeltaSeconds) * 3.6f
                    if (calc in 1.5f..220.0f) {
                        calculatedSpeed = calc
                    }
                }
            }
        }

        currentSpeedKmh = calculatedSpeed

        // Heading / Bearing calculation
        if (location.hasBearing() && location.bearing != 0.0f) {
            currentHeading = location.bearing
        } else previousLocation?.let { prev ->
            val bearingTo = prev.bearingTo(location)
            if (bearingTo != 0.0f) {
                currentHeading = bearingTo
            }
        }

        // Only accumulate distance when actively tracking AND moving
        if (isCurrentlyTracking && currentSpeedKmh > 1.5f) {
            previousLocation?.let { prev ->
                val dist = prev.distanceTo(location)
                if (dist in 0.8f..350.0f) {
                    totalDistanceMeters += dist
                }
            }
            // Add point to route line
            TelemetryRepository.addRoutePoint(Point.fromLngLat(location.longitude, location.latitude))
        }

        // GPS Accuracy and Satellites count
        val accuracy = if (location.hasAccuracy()) location.accuracy else 1.0f
        currentAccuracyMeters = accuracy
        val sats = if (location.extras?.containsKey("satellites") == true) {
            location.extras?.getInt("satellites") ?: 9
        } else 9

        // Reverse geocoding for real street address
        resolveAddress(location.latitude, location.longitude, sats, accuracy)

        previousLocation = location
    }

    private fun resolveAddress(lat: Double, lng: Double, sats: Int, accuracy: Float) {
        serviceScope.launch {
            var label: String? = null
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(this@TelemetryService, Locale.getDefault())
                    val list = geocoder.getFromLocation(lat, lng, 1)
                    if (!list.isNullOrEmpty()) {
                        val addr = list[0]
                        val thoroughfare = addr.thoroughfare
                        val subLoc = addr.subLocality ?: addr.locality
                        label = when {
                            !thoroughfare.isNullOrBlank() && !subLoc.isNullOrBlank() -> "$thoroughfare / $subLoc"
                            !thoroughfare.isNullOrBlank() -> thoroughfare
                            !subLoc.isNullOrBlank() -> subLoc
                            else -> addr.getAddressLine(0)?.split(",")?.firstOrNull()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore geocoding errors
            }
            TelemetryRepository.updateGpsStatus(sats, accuracy, label)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        // Acquire initial location immediately
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    processLocationUpdate(loc)
                }
            }
        } catch (e: SecurityException) {
            // Permission check handled in UI
        }

        startLocationUpdates()
        startSensors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_STICKY
            }
            ACTION_RESET -> {
                resetTracking()
                return START_STICKY
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startTracking()
                return START_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startTracking()
                return START_STICKY
            }
        }
    }

    private fun startSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun startTracking() {
        isCurrentlyTracking = true
        tripStartTimeMs = System.currentTimeMillis()
        totalDistanceMeters = 0.0
        tripStartLocationName = TelemetryRepository.locationLabel.value.takeIf { it.isNotBlank() && it != "ACQUIRING GPS..." } ?: "Start Location"
        TelemetryRepository.setTracking(true)

        // 10Hz Telemetry Sampling Loop (100ms)
        samplingJob?.cancel()
        samplingJob = serviceScope.launch {
            while (isActive) {
                val elapsedSeconds = if (isCurrentlyTracking && tripStartTimeMs > 0) {
                    ((System.currentTimeMillis() - tripStartTimeMs) / 1000.0f).coerceAtLeast(0.0f)
                } else 0.0f

                // Convert linear dynamic acceleration to g units for ML model (9.80665 m/s^2 = 1g)
                val accelX_g = currentLinearX / 9.80665f
                val accelY_g = currentLinearY / 9.80665f
                val accelZ_g = currentLinearZ / 9.80665f
                val pitchDeg = currentPitch * 57.2958f
                val yawRad = currentYaw

                val point = TelemetryPoint(
                    timestamp = elapsedSeconds,
                    speedKmh = currentSpeedKmh,
                    accelXFiltered = accelX_g,
                    accelYFiltered = accelY_g,
                    accelZFiltered = accelZ_g,
                    pitch = pitchDeg,
                    yaw = yawRad,
                    latitude = currentLatitude,
                    longitude = currentLongitude,
                    altitude = currentAltitude,
                    heading = currentHeading,
                    rawAccelX = currentAccelX,
                    rawAccelY = currentAccelY,
                    rawAccelZ = currentAccelZ,
                    gyroRate = currentGyroRate,
                    wallClockTime = System.currentTimeMillis()
                )

                var currentSize: Int
                var spanSeconds = 0f
                synchronized(rollingBuffer) {
                    if (rollingBuffer.size >= bufferCapacity) {
                        rollingBuffer.removeFirst()
                    }
                    rollingBuffer.addLast(point)
                    currentSize = rollingBuffer.size
                    if (rollingBuffer.size > 1) {
                        spanSeconds = rollingBuffer.last.timestamp - rollingBuffer.first.timestamp
                    }
                }

                TelemetryRepository.updateCurrentTelemetry(point, currentSize, spanSeconds)
                delay(100) // 10Hz
            }
        }

        // 1-second Backend API & Evaluation Loop
        apiJob?.cancel()
        apiJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (!isCurrentlyTracking) continue

                val snapshot: List<TelemetryPoint>
                synchronized(rollingBuffer) {
                    snapshot = rollingBuffer.toList()
                }

                if (snapshot.size >= 2) {
                    val span = snapshot.last().timestamp - snapshot.first().timestamp
                    if (span >= 30.0f) {
                        try {
                            val response = RetrofitClient.apiService.predictRisk(PredictRequest(snapshot))
                            if (response.isSuccessful && response.body() != null) {
                                val body = response.body()!!
                                TelemetryRepository.updateRiskResponse(body)
                                if (body.riskLevel >= 2) {
                                    val lat = snapshot.last().latitude
                                    val lng = snapshot.last().longitude
                                    if (lat != 0.0 && lng != 0.0) {
                                        TelemetryRepository.addRiskEventPoint(Point.fromLngLat(lng, lat))
                                    }
                                }
                            } else {
                                performLocalEvaluation(snapshot)
                            }
                        } catch (e: Exception) {
                            performLocalEvaluation(snapshot)
                        }
                    } else {
                        performLocalEvaluation(snapshot)
                    }
                }
            }
        }

        // Trip Stats Loop (1 Hz)
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (isCurrentlyTracking && tripStartTimeMs > 0) {
                    val durationSec = ((System.currentTimeMillis() - tripStartTimeMs) / 1000L).coerceAtLeast(0L)
                    val distKm = (totalDistanceMeters / 1000.0).toFloat()
                    val avgSpeed = if (durationSec > 0 && distKm > 0.01f) {
                        (distKm / (durationSec / 3600.0f))
                    } else 0.0f

                    TelemetryRepository.updateTripMetrics(distKm, durationSec, avgSpeed)
                }
            }
        }

        // Live V2V Vehicle-to-Vehicle Sync Loop (1 Hz)
        v2vJob?.cancel()
        v2vJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (!isCurrentlyTracking) continue
                if (!TelemetryRepository.isLocationSharingEnabled.value) {
                    TelemetryRepository.setV2VConnectionStatus("SHARING_PAUSED")
                    continue
                }
                if (currentLatitude == 0.0 && currentLongitude == 0.0) {
                    TelemetryRepository.setV2VConnectionStatus("ACQUIRING_GPS")
                    continue
                }

                val vId = TelemetryRepository.vehicleSessionId.value
                val lateralG = currentLinearX / 9.80665f
                val isUnstable = TelemetryRepository.isLateralUnstable.value
                val riskLvl = TelemetryRepository.latestRiskResponse.value?.riskLevel ?: 0

                val req = com.safecruise.app.data.model.V2VLocationRequest(
                    vehicleId = vId,
                    displayName = "Vehicle " + vId.takeLast(4),
                    latitude = currentLatitude,
                    longitude = currentLongitude,
                    accuracyM = currentAccuracyMeters,
                    speedKmh = currentSpeedKmh,
                    headingDeg = currentHeading,
                    timestampS = System.currentTimeMillis() / 1000.0,
                    altitudeM = currentAltitude,
                    lateralAccelG = lateralG,
                    yawRateRads = currentGyroRate,
                    riskLevel = riskLvl,
                    isUnstable = isUnstable
                )

                try {
                    val resp = RetrofitClient.apiService.updateV2VLocation(req)
                    if (resp.isSuccessful && resp.body() != null) {
                        val body = resp.body()!!
                        TelemetryRepository.setV2VConnectionStatus("CONNECTED")
                        TelemetryRepository.setV2VActiveCount(body.nearbyCount)

                        if (TelemetryRepository.activeScenario.value == "NONE") {
                            val vehicles = body.nearbyVehicles.map { it.toNearbyVehicle() }
                            TelemetryRepository.setNearbyVehicles(vehicles)
                        }

                        if (body.activeAlerts.isNotEmpty()) {
                            val alert = body.activeAlerts.first()
                            TelemetryRepository.setRoadHazardWarning(alert)
                            TelemetryRepository.setProximityAlert(alert)
                        } else {
                            TelemetryRepository.setRoadHazardWarning(null)
                        }
                    } else {
                        TelemetryRepository.setV2VConnectionStatus("OFFLINE")
                    }
                } catch (e: Exception) {
                    TelemetryRepository.setV2VConnectionStatus("OFFLINE")
                }
            }
        }
    }

    /**
     * Accurate Driver Risk Analysis based strictly on vehicle dynamics:
     * - Speed alone NEVER triggers warnings (cruising at 30, 50, 80 km/h is optimal).
     * - Warnings trigger only on real risky maneuvers: harsh braking, sudden acceleration, sharp swerving.
     */
    private fun performLocalEvaluation(samples: List<TelemetryPoint>) {
        if (samples.isEmpty()) return

        // 1. Check recent instantaneous forces (last 1.5 seconds / 15 samples)
        val recent = samples.takeLast(15)
        val minAccelY_g = recent.minOfOrNull { it.accelYFiltered } ?: 0.0f
        val maxAccelY_g = recent.maxOfOrNull { it.accelYFiltered } ?: 0.0f
        val maxAbsAccelX_g = recent.maxOfOrNull { abs(it.accelXFiltered) } ?: 0.0f
        val maxGyro = recent.maxOfOrNull { it.gyroRate } ?: 0.0f

        // Convert to m/s^2
        val minAccelY_ms2 = minAccelY_g * 9.80665f // Longitudinal deceleration (braking)
        val maxAccelY_ms2 = maxAccelY_g * 9.80665f // Longitudinal forward acceleration
        val maxAccelX_ms2 = maxAbsAccelX_g * 9.80665f // Lateral centripetal force

        // 2. Check rolling speed variability over sustained window (15s)
        val speeds = samples.takeLast(150).map { it.speedKmh }
        val avgSpeed = speeds.average()
        val speedStd = sqrt(speeds.map { (it - avgSpeed) * (it - avgSpeed) }.average()).toFloat()

        // Risk detection logic (Uses dynamic tunables from Dev menu):
        val harshBrakeThresh = TelemetryRepository.harshBrakingThreshold.value
        val harshAccelThresh = TelemetryRepository.harshAccelThreshold.value
        val harshSwerveThresh = TelemetryRepository.harshSwerveThreshold.value

        // - Harsh braking
        val isHarshBraking = minAccelY_ms2 < harshBrakeThresh
        val isFirmBraking = minAccelY_ms2 < (harshBrakeThresh * 0.7f)

        // - Harsh acceleration
        val isHarshAccel = maxAccelY_ms2 > harshAccelThresh
        val isBriskAccel = maxAccelY_ms2 > (harshAccelThresh * 0.73f)

        // - Sharp swerve / hard cornering
        val isMoving = currentSpeedKmh > 10.0f
        val isSharpSwerve = isMoving && (maxAccelX_ms2 > harshSwerveThresh || maxGyro > 0.55f)
        val isBriskCornering = isMoving && (maxAccelX_ms2 > (harshSwerveThresh * 0.68f) || maxGyro > 0.38f)

        // - Erratic speed standard deviation
        val isErraticSpeed = currentSpeedKmh > 20.0f && speedStd > 12.0f && speeds.size >= 50

        // - Check nearby proximity threats (V2V context)
        val nearbyAggressive = TelemetryRepository.nearbyVehicles.value.firstOrNull { it.isAggressive }

        // Lateral instability / lane drift check:
        val speedMs = currentSpeedKmh / 3.6f
        val expectedCentripetal = speedMs * maxGyro
        val lateralResidual = abs(maxAccelX_ms2 - expectedCentripetal)
        val isLateralUnstable = currentSpeedKmh > 20.0f && (
            (maxAccelX_ms2 > 4.5f && lateralResidual > 3.0f) || maxAccelX_ms2 > 5.5f
        )
        TelemetryRepository.setLateralUnstable(isLateralUnstable)

        val roadHazardAlert = TelemetryRepository.roadHazardWarning.value

        val (riskLevel, riskLabel, score, detail, topFeat) = when {
            // High Risk Triggers (Level 2)
            isLateralUnstable -> {
                Tuple5(2, "High Risk", 88, "Possible unstable movement detected. Reduce speed when safe.", "Accel_X_Filtered")
            }
            nearbyAggressive != null && nearbyAggressive.distanceMeters < 50f -> {
                val threatDetail = if (nearbyAggressive.relativePosition == "BEHIND") {
                    "Aggressive vehicle tailgating behind (${nearbyAggressive.distanceMeters.toInt()}m)"
                } else {
                    "Aggressive vehicle maneuvering ahead (${nearbyAggressive.distanceMeters.toInt()}m)"
                }
                Tuple5(2, "High Risk", 85, threatDetail, "Proximity_V2V")
            }
            isHarshBraking -> Tuple5(2, "High Risk", 82, "Harsh braking detected", "Accel_Y_Filtered")
            isSharpSwerve -> Tuple5(2, "High Risk", 80, "Sharp swerve detected", "Accel_X_Filtered")
            isHarshAccel -> Tuple5(2, "High Risk", 76, "Aggressive acceleration spike", "Accel_Y_Filtered")

            // Caution Triggers (Level 1)
            roadHazardAlert != null -> {
                Tuple5(1, "Medium Risk", 58, roadHazardAlert, "Proximity_V2V")
            }
            nearbyAggressive != null && nearbyAggressive.distanceMeters in 50f..100f -> {
                Tuple5(1, "Medium Risk", 54, "Aggressive vehicle in vicinity (${nearbyAggressive.distanceMeters.toInt()}m)", "Proximity_V2V")
            }
            isFirmBraking -> Tuple5(1, "Medium Risk", 46, "Firm deceleration detected", "Accel_Y_Filtered")
            isBriskCornering -> Tuple5(1, "Medium Risk", 44, "Brisk cornering force", "Accel_X_Filtered")
            isBriskAccel -> Tuple5(1, "Medium Risk", 40, "Rapid acceleration detected", "Accel_Y_Filtered")
            isErraticSpeed -> Tuple5(1, "Medium Risk", 45, "Erratic speed fluctuations", "Speed_kmh_std")

            // Normal / Safe Driving at ANY speed (Level 0)
            else -> {
                val cruiseScore = if (currentSpeedKmh > 5.0f) 8 else 5
                Tuple5(0, "Low Risk", cruiseScore, "Smooth & steady driving", "Speed_kmh")
            }
        }

        val response = PredictResponse(
            riskLevel = riskLevel,
            riskLabel = riskLabel,
            behaviour = if (riskLevel == 2) "AGGRESSIVE" else if (riskLevel == 1) "DROWSY" else "NORMAL",
            confidenceScores = mapOf(
                "low_risk" to (if (riskLevel == 0) 0.92f else 0.08f),
                "medium_risk" to (if (riskLevel == 1) 0.70f else 0.12f),
                "high_risk" to (if (riskLevel == 2) 0.85f else 0.05f)
            ),
            topFeature = topFeat,
            shapImpacts = emptyMap(),
            calculatedScore = score,
            eventDetail = detail
        )
        TelemetryRepository.updateRiskResponse(response)
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .setMinUpdateIntervalMillis(200)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var tripStartLocationName = "Current Location"

    private fun stopTracking() {
        if (isCurrentlyTracking && tripStartTimeMs > 0) {
            val durationSec = ((System.currentTimeMillis() - tripStartTimeMs) / 1000L).coerceAtLeast(0L)
            val distKm = (totalDistanceMeters / 1000.0).toFloat()
            // Only save meaningful drives (e.g., active for at least 5 seconds or moved > 20 meters)
            if (durationSec >= 5L || distKm > 0.02f) {
                val avgSpeed = if (durationSec > 0 && distKm > 0.01f) (distKm / (durationSec / 3600.0f)) else 0.0f
                val endLocationName = TelemetryRepository.locationLabel.value.takeIf { it.isNotBlank() && it != "ACQUIRING GPS..." } ?: "Trip Destination"
                val startLoc = tripStartLocationName.takeIf { it.isNotBlank() && it != "ACQUIRING GPS..." } ?: "Start Location"
                val risk = TelemetryRepository.latestRiskResponse.value
                val dateFmt = java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(java.util.Date(tripStartTimeMs))

                TelemetryRepository.saveTripRecord(
                    com.safecruise.app.data.model.TripRecord(
                        dateStr = dateFmt,
                        distanceKm = distKm,
                        durationSec = durationSec,
                        avgSpeedKph = avgSpeed,
                        maxSpeedKph = TelemetryRepository.tripMaxSpeedKph.value,
                        riskScore = risk?.riskScore ?: 0,
                        status = when {
                            (risk?.riskLevel ?: 0) >= 2 -> "High Risk"
                            (risk?.riskLevel ?: 0) == 1 -> "Caution"
                            else -> "Optimal"
                        },
                        startLocation = startLoc,
                        endLocation = endLocationName
                    )
                )
            }
        }

        isCurrentlyTracking = false
        TelemetryRepository.setTracking(false)
        samplingJob?.cancel()
        apiJob?.cancel()
        statsJob?.cancel()
        v2vJob?.cancel()

        // Unregister session from V2V network
        serviceScope.launch {
            try {
                RetrofitClient.apiService.leaveV2VSession(TelemetryRepository.vehicleSessionId.value)
            } catch (_: Exception) {}
        }
        TelemetryRepository.setV2VConnectionStatus("OFFLINE")
        TelemetryRepository.setV2VActiveCount(0)
        if (TelemetryRepository.activeScenario.value == "NONE") {
            TelemetryRepository.setNearbyVehicles(emptyList())
        }
    }

    private fun resetTracking() {
        stopTracking()
        tripStartTimeMs = 0L
        totalDistanceMeters = 0.0
        currentSpeedKmh = 0.0f
        tripStartLocationName = "Current Location"
        synchronized(rollingBuffer) {
            rollingBuffer.clear()
        }
        TelemetryRepository.resetTrip()
    }

    override fun onDestroy() {
        stopTracking()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Direct gravity-free linear acceleration provided by device sensor fusion
                currentLinearX = event.values[0]
                currentLinearY = event.values[1]
                currentLinearZ = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                currentAccelX = event.values[0]
                currentAccelY = event.values[1]
                currentAccelZ = event.values[2]

                // If linear accelerometer sensor is not available on device, apply standard high-pass filter
                if (linearAccelerometer == null) {
                    val alpha = 0.8f
                    gravityX = alpha * gravityX + (1 - alpha) * event.values[0]
                    gravityY = alpha * gravityY + (1 - alpha) * event.values[1]
                    gravityZ = alpha * gravityZ + (1 - alpha) * event.values[2]

                    currentLinearX = event.values[0] - gravityX
                    currentLinearY = event.values[1] - gravityY
                    currentLinearZ = event.values[2] - gravityZ
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                currentPitch = event.values[0]
                currentYaw = event.values[1]
                currentGyroRate = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drive.Net Telemetry Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drive.Net Active")
            .setContentText("Driver monitoring active • Live V2V safety")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "safecruise_telemetry"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.safecruise.app.START_TELEMETRY"
        const val ACTION_STOP = "com.safecruise.app.STOP_TELEMETRY"
        const val ACTION_RESET = "com.safecruise.app.RESET_TELEMETRY"
    }
}
