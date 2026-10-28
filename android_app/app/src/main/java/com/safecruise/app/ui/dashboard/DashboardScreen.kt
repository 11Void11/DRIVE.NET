package com.safecruise.app.ui.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.compass.generated.CompassSettings
import com.mapbox.maps.plugin.scalebar.generated.ScaleBarSettings
import com.safecruise.app.R
import com.safecruise.app.data.model.IncidentLog
import com.safecruise.app.data.model.NearbyVehicle
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.data.model.TripRecord
import com.safecruise.app.ui.components.SafeCruiseBottomNav
import com.safecruise.app.ui.components.SafeCruiseBottomPanel
import com.safecruise.app.ui.components.SafeCruiseTopHeader
import com.safecruise.app.ui.diag.DiagScreen
import com.safecruise.app.ui.logs.LogsScreen
import com.safecruise.app.ui.profile.ProfileScreen
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkBackground
import com.safecruise.app.ui.theme.DarkCardBorder
import com.safecruise.app.ui.theme.DarkHudCard
import com.safecruise.app.ui.theme.HighRiskOrange
import com.safecruise.app.ui.theme.HighRiskRed
import com.safecruise.app.ui.theme.RouteGreen
import com.safecruise.app.ui.theme.TextWhite
import com.safecruise.app.ui.trip.TripScreen
import com.safecruise.app.utils.MapUtils

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current

    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val telemetry by viewModel.currentTelemetry.collectAsStateWithLifecycle()
    val riskResponse by viewModel.latestRiskResponse.collectAsStateWithLifecycle()
    val mlEngineStatus by viewModel.mlEngineStatus.collectAsStateWithLifecycle()
    val distanceKm by viewModel.tripDistanceKm.collectAsStateWithLifecycle()
    val tripDurationSec by viewModel.tripDurationSeconds.collectAsStateWithLifecycle()
    val avgSpeedKph by viewModel.tripAvgSpeedKph.collectAsStateWithLifecycle()
    val maxSpeedKph by viewModel.tripMaxSpeedKph.collectAsStateWithLifecycle()
    val satsCount by viewModel.satellitesCount.collectAsStateWithLifecycle()
    val hdopVal by viewModel.hdop.collectAsStateWithLifecycle()
    val locationLabel by viewModel.locationLabel.collectAsStateWithLifecycle()
    val routePoints by viewModel.routePoints.collectAsStateWithLifecycle()
    val riskEventPoints by viewModel.riskEventPoints.collectAsStateWithLifecycle()
    val incidentLogs by viewModel.incidentLogs.collectAsStateWithLifecycle()
    val tripHistory by viewModel.tripHistory.collectAsStateWithLifecycle()
    val harshBrakingThresh by viewModel.harshBrakingThreshold.collectAsStateWithLifecycle()
    val harshAccelThresh by viewModel.harshAccelThreshold.collectAsStateWithLifecycle()
    val harshSwerveThresh by viewModel.harshSwerveThreshold.collectAsStateWithLifecycle()
    val nearbyVehicles by viewModel.nearbyVehicles.collectAsStateWithLifecycle()
    val proximityAlert by viewModel.proximityAlert.collectAsStateWithLifecycle()
    val activeScenario by viewModel.activeScenario.collectAsStateWithLifecycle()
    val v2vConnectionStatus by viewModel.v2vConnectionStatus.collectAsStateWithLifecycle()
    val v2vActiveCount by viewModel.v2vActiveCount.collectAsStateWithLifecycle()
    val driverName by viewModel.driverName.collectAsStateWithLifecycle()

    if (!isInspection) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        LaunchedEffect(Unit) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val missingPermissions = permissionsToRequest.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }

    DashboardContent(
        isInspection = isInspection,
        isTracking = isTracking,
        telemetry = telemetry,
        riskResponse = riskResponse,
        mlEngineStatus = mlEngineStatus,
        distanceKm = distanceKm,
        tripDurationSec = tripDurationSec,
        avgSpeedKph = avgSpeedKph,
        maxSpeedKph = maxSpeedKph,
        satsCount = satsCount,
        hdopVal = hdopVal,
        locationLabel = locationLabel,
        routePoints = routePoints,
        riskEventPoints = riskEventPoints,
        incidentLogs = incidentLogs,
        tripHistory = tripHistory,
        harshBrakingThreshold = harshBrakingThresh,
        harshAccelThreshold = harshAccelThresh,
        harshSwerveThreshold = harshSwerveThresh,
        nearbyVehicles = nearbyVehicles,
        proximityAlert = proximityAlert,
        activeScenario = activeScenario,
        v2vConnectionStatus = v2vConnectionStatus,
        v2vActiveCount = v2vActiveCount,
        onSetScenario = { viewModel.setScenario(it) },
        onHarshBrakingChange = { viewModel.setHarshBrakingThreshold(it) },
        onHarshAccelChange = { viewModel.setHarshAccelThreshold(it) },
        onHarshSwerveChange = { viewModel.setHarshSwerveThreshold(it) },
        onToggleTracking = {
            if (!isInspection) {
                viewModel.toggleTracking(context)
            }
        },
        onResetTrip = {
            if (!isInspection) {
                viewModel.resetTrip(context)
            }
        },
        onClearLogs = { viewModel.clearLogs() },
        onServerUrlChange = { viewModel.setServerUrl(it) },
        onPingServer = { url, cb -> viewModel.pingServer(url, cb) },
        getServerUrl = { viewModel.getServerUrl() },
        driverName = driverName,
        onSaveDriverName = { viewModel.updateDriverName(context, it) }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun DashboardContent(
    isInspection: Boolean,
    isTracking: Boolean,
    telemetry: TelemetryPoint?,
    riskResponse: PredictResponse?,
    mlEngineStatus: String,
    distanceKm: Float,
    tripDurationSec: Long,
    avgSpeedKph: Float,
    maxSpeedKph: Float,
    satsCount: Int,
    hdopVal: Float,
    locationLabel: String,
    routePoints: List<Point>,
    riskEventPoints: List<Point>,
    incidentLogs: List<IncidentLog>,
    tripHistory: List<TripRecord>,
    harshBrakingThreshold: Float = -3.4f,
    harshAccelThreshold: Float = 3.4f,
    harshSwerveThreshold: Float = 3.4f,
    nearbyVehicles: List<NearbyVehicle> = emptyList(),
    proximityAlert: String? = null,
    activeScenario: String = "NONE",
    v2vConnectionStatus: String = "OFFLINE",
    v2vActiveCount: Int = 0,
    onSetScenario: (String) -> Unit = {},
    onHarshBrakingChange: (Float) -> Unit = {},
    onHarshAccelChange: (Float) -> Unit = {},
    onHarshSwerveChange: (Float) -> Unit = {},
    onToggleTracking: () -> Unit,
    onResetTrip: () -> Unit,
    onClearLogs: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onPingServer: (String, (Boolean, String) -> Unit) -> Unit,
    getServerUrl: () -> String,
    driverName: String = com.safecruise.app.data.repository.DriverProfileRepository.DEFAULT_DRIVER_NAME,
    onSaveDriverName: (String) -> Result<String> = { Result.success(it) }
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("HUD") }
    var showProfileScreen by remember { mutableStateOf(false) }

    val rawLat = telemetry?.latitude ?: 0.0
    val rawLng = telemetry?.longitude ?: 0.0
    val hasRealLocation = rawLat != 0.0 && rawLng != 0.0

    val currentLat = if (hasRealLocation) rawLat else 37.7749
    val currentLng = if (hasRealLocation) rawLng else -122.4194
    val currentHeading = telemetry?.heading ?: 0.0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // SCREEN CONTENT BASED ON SELECTED TAB
        when (selectedTab) {
            "HUD" -> {
                // HUD MAP LAYER
                if (isInspection) {
                    DarkMapPreviewBackground()
                } else {
                    val mapViewportState = rememberMapViewportState {
                        setCameraOptions {
                            center(Point.fromLngLat(currentLng, currentLat))
                            zoom(if (hasRealLocation) 15.5 else 13.0)
                            pitch(0.0)
                        }
                    }

                    var hasInitialCentered by remember { mutableStateOf(false) }

                    LaunchedEffect(hasRealLocation, currentLat, currentLng, currentHeading) {
                        if (hasRealLocation) {
                            val currentSpeed = telemetry?.speedKmh ?: 0.0f
                            val isMoving = currentSpeed > 2.0f
                            val targetBearing = if (isMoving && currentHeading > 0f) currentHeading.toDouble() else null
                            val targetPitch = if (isMoving) 35.0 else 0.0

                            if (!hasInitialCentered) {
                                hasInitialCentered = true
                                mapViewportState.flyTo(
                                    cameraOptions = cameraOptions {
                                        center(Point.fromLngLat(currentLng, currentLat))
                                        zoom(16.5)
                                        bearing(targetBearing ?: 0.0)
                                        pitch(targetPitch)
                                    },
                                    animationOptions = MapAnimationOptions.mapAnimationOptions {
                                        duration(1000)
                                    }
                                )
                            } else {
                                // Smoothly follow vehicle position and orientation along direction of movement
                                mapViewportState.easeTo(
                                    cameraOptions = cameraOptions {
                                        center(Point.fromLngLat(currentLng, currentLat))
                                        if (targetBearing != null) {
                                            bearing(targetBearing)
                                        }
                                        pitch(targetPitch)
                                    },
                                    animationOptions = MapAnimationOptions.mapAnimationOptions {
                                        duration(600)
                                    }
                                )
                            }
                        }
                    }

                    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

                    val navArrowBitmap = remember(context) {
                        getBitmapFromVectorDrawable(context, R.drawable.ic_nav_arrow)
                    }

                    val orangeDotBitmap = remember(context) {
                        getOrangeDotBitmap()
                    }

                    val redVehicleDotBitmap = remember {
                        getRedVehicleDotBitmap()
                    }

                    val cyanVehicleDotBitmap = remember {
                        getCyanVehicleDotBitmap()
                    }

                    val recenterCamera: () -> Unit = {
                        if (hasRealLocation) {
                            val isMoving = (telemetry?.speedKmh ?: 0f) > 2.0f
                            val targetBearing = if (isMoving && currentHeading > 0f) currentHeading.toDouble() else 0.0
                            mapViewportState.flyTo(
                                cameraOptions = cameraOptions {
                                    center(Point.fromLngLat(currentLng, currentLat))
                                    zoom(16.5)
                                    bearing(targetBearing)
                                    pitch(if (isMoving) 35.0 else 0.0)
                                },
                                animationOptions = MapAnimationOptions.mapAnimationOptions {
                                    duration(800)
                                }
                            )
                        } else {
                            try {
                                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                    if (loc != null) {
                                        mapViewportState.flyTo(
                                            cameraOptions = cameraOptions {
                                                center(Point.fromLngLat(loc.longitude, loc.latitude))
                                                zoom(16.5)
                                                bearing(loc.bearing.toDouble())
                                            },
                                            animationOptions = MapAnimationOptions.mapAnimationOptions {
                                                duration(800)
                                            }
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    MapboxMap(
                        modifier = Modifier.fillMaxSize(),
                        mapViewportState = mapViewportState,
                        compassSettings = CompassSettings {
                            enabled = false
                        },
                        scaleBarSettings = ScaleBarSettings {
                            enabled = false
                        },
                        mapInitOptionsFactory = { ctx ->
                            MapInitOptions(ctx, styleUri = MapUtils.MAPBOX_DARK_STYLE)
                        }
                    ) {
                        if (routePoints.size >= 2) {
                            PolylineAnnotation(
                                points = routePoints,
                                lineColorString = "#00E676",
                                lineWidth = 4.5
                            )
                        }

                        // Nearby V2V Vehicles rendered with validated heading rotation
                        nearbyVehicles.forEach { vehicle ->
                            val icon = if (vehicle.isAggressive) redVehicleDotBitmap else cyanVehicleDotBitmap
                            val shouldRotate = vehicle.speedKmh > 2.0f && vehicle.headingDegrees > 0f
                            PointAnnotation(
                                point = Point.fromLngLat(vehicle.longitude, vehicle.latitude),
                                iconImageBitmap = icon,
                                iconSize = if (vehicle.isAggressive) 1.2 else 1.0,
                                iconRotate = if (shouldRotate) vehicle.headingDegrees.toDouble() else 0.0
                            )
                        }

                        riskEventPoints.forEach { eventPoint ->
                            PointAnnotation(
                                point = eventPoint,
                                iconImageBitmap = orangeDotBitmap,
                                iconSize = 0.9
                            )
                        }

                        if (hasRealLocation) {
                            if (navArrowBitmap != null) {
                                PointAnnotation(
                                    point = Point.fromLngLat(currentLng, currentLat),
                                    iconImageBitmap = navArrowBitmap,
                                    iconRotate = currentHeading.toDouble(),
                                    iconSize = 1.3
                                )
                            } else {
                                PointAnnotation(
                                    point = Point.fromLngLat(currentLng, currentLat)
                                )
                            }
                        }
                    }

                    // OVERLAY HUD LAYOUT
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SafeCruiseTopHeader(
                                mlEngineStatus = mlEngineStatus,
                                telemetry = telemetry,
                                satellitesCount = satsCount,
                                hdop = hdopVal,
                                locationLabel = locationLabel,
                                v2vStatus = v2vConnectionStatus,
                                v2vCount = v2vActiveCount,
                                onAvatarClick = { showProfileScreen = true }
                            )

                            // Quick AI Recommendation / Collision Warning Pop-up (auto-dismissed in 3 sec)
                            AnimatedVisibility(
                                visible = proximityAlert != null,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                proximityAlert?.let { alertText ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xF0180404), RoundedCornerShape(12.dp))
                                            .border(1.5.dp, HighRiskRed, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(Color(0x33FF3333), CircleShape)
                                                    .border(1.dp, HighRiskRed, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Alert",
                                                    tint = HighRiskRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "AI SAFETY & PROXIMITY ADVISORY",
                                                    color = HighRiskRed,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Text(
                                                    text = alertText,
                                                    color = TextWhite,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // MyLocation Button above info box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp, bottom = 8.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                FloatingActionButton(
                                    onClick = recenterCamera,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .border(1.2.dp, DarkCardBorder, CircleShape),
                                    shape = CircleShape,
                                    containerColor = DarkHudCard,
                                    contentColor = BrightNeonGreen,
                                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "My Location",
                                        tint = BrightNeonGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            SafeCruiseBottomPanel(
                                isTracking = isTracking,
                                telemetry = telemetry,
                                response = riskResponse,
                                distanceKm = distanceKm,
                                tripDurationSec = tripDurationSec,
                                avgSpeedKph = avgSpeedKph,
                                onToggleTracking = onToggleTracking,
                                onResetTrip = onResetTrip
                            )

                            SafeCruiseBottomNav(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it }
                            )
                        }
                    }
                }
            }

            "TRIP" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TripScreen(
                            isTracking = isTracking,
                            currentSpeedKmh = telemetry?.speedKmh ?: 0f,
                            distanceKm = distanceKm,
                            durationSec = tripDurationSec,
                            avgSpeedKph = avgSpeedKph,
                            maxSpeedKph = maxSpeedKph,
                            tripHistory = tripHistory
                        )
                    }

                    SafeCruiseBottomNav(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }

            "LOGS" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LogsScreen(
                            logs = incidentLogs,
                            onClearLogs = onClearLogs
                        )
                    }

                    SafeCruiseBottomNav(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }

            "DEV", "DIAG" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DiagScreen(
                            telemetry = telemetry,
                            satellitesCount = satsCount,
                            hdop = hdopVal,
                            bufferSize = (telemetry?.timestamp?.toInt() ?: 0) * 10,
                            bufferSpanSeconds = telemetry?.timestamp ?: 0f,
                            mlEngineStatus = mlEngineStatus,
                            currentServerUrl = getServerUrl(),
                            harshBrakingThreshold = harshBrakingThreshold,
                            harshAccelThreshold = harshAccelThreshold,
                            harshSwerveThreshold = harshSwerveThreshold,
                            activeScenario = activeScenario,
                            onSetScenario = { scenario ->
                                onSetScenario(scenario)
                                if (scenario != "NONE" && scenario != "CLEAR") {
                                    selectedTab = "HUD"
                                }
                            },
                            onHarshBrakingChange = onHarshBrakingChange,
                            onHarshAccelChange = onHarshAccelChange,
                            onHarshSwerveChange = onHarshSwerveChange,
                            onServerUrlChange = onServerUrlChange,
                            onPingServer = onPingServer
                        )
                    }

                    SafeCruiseBottomNav(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        }

        // FULL SCREEN PROFILE MODAL OVERLAY
        AnimatedVisibility(
            visible = showProfileScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                ProfileScreen(
                    driverName = driverName,
                    tripHistory = tripHistory,
                    currentDistanceKm = distanceKm,
                    currentDurationSec = tripDurationSec,
                    onSaveDriverName = onSaveDriverName,
                    onDismiss = { showProfileScreen = false }
                )
            }
        }
    }
}

private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

private fun getOrangeDotBitmap(): Bitmap {
    val size = 26
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val fillPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#FF9100")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    val radius = size / 2f - 2f
    canvas.drawCircle(size / 2f, size / 2f, radius, fillPaint)
    canvas.drawCircle(size / 2f, size / 2f, radius, strokePaint)
    return bitmap
}

private fun getRedVehicleDotBitmap(): Bitmap {
    val size = 30
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val haloPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#44FF3333")
        style = Paint.Style.FILL
    }
    val fillPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#FF3333")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, haloPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, fillPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, strokePaint)
    return bitmap
}

private fun getCyanVehicleDotBitmap(): Bitmap {
    val size = 28
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val fillPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    val radius = size / 2f - 3f
    canvas.drawCircle(size / 2f, size / 2f, radius, fillPaint)
    canvas.drawCircle(size / 2f, size / 2f, radius, strokePaint)
    return bitmap
}

@Composable
private fun DarkMapPreviewBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val gridStep = 80f
        var x = 0f
        while (x < width) {
            drawLine(
                color = Color(0xFF161C26),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += gridStep
        }
        var y = 0f
        while (y < height) {
            drawLine(
                color = Color(0xFF161C26),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += gridStep
        }

        val route = Path().apply {
            moveTo(width * 0.2f, height * 0.15f)
            lineTo(width * 0.45f, height * 0.25f)
            lineTo(width * 0.55f, height * 0.35f)
            lineTo(width * 0.58f, height * 0.48f)
            lineTo(width * 0.65f, height * 0.55f)
        }
        drawPath(
            path = route,
            color = RouteGreen,
            style = Stroke(width = 12f)
        )

        drawCircle(
            color = HighRiskOrange,
            radius = 12f,
            center = Offset(width * 0.45f, height * 0.25f)
        )

        drawCircle(
            color = Color(0x6600FF66),
            radius = 24f,
            center = Offset(width * 0.65f, height * 0.55f)
        )
        drawCircle(
            color = BrightNeonGreen,
            radius = 10f,
            center = Offset(width * 0.65f, height * 0.55f)
        )
    }
}
