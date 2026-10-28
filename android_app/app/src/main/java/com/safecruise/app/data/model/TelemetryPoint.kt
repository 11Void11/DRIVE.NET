package com.safecruise.app.data.model

import com.google.gson.annotations.SerializedName

data class TelemetryPoint(
    @SerializedName("Timestamp") val timestamp: Float, // Seconds elapsed since trip start
    @SerializedName("Speed_kmh") val speedKmh: Float, // Speed in km/h
    @SerializedName("Accel_X_Filtered") val accelXFiltered: Float, // Lateral acceleration in g
    @SerializedName("Accel_Y_Filtered") val accelYFiltered: Float, // Longitudinal acceleration in g
    @SerializedName("Accel_Z_Filtered") val accelZFiltered: Float, // Vertical acceleration in g
    @SerializedName("Pitch") val pitch: Float, // Device pitch in degrees
    @SerializedName("Yaw") val yaw: Float, // Heading / yaw in radians
    
    // GPS & display metadata (non-model fields)
    @SerializedName("Latitude") val latitude: Double = 0.0,
    @SerializedName("Longitude") val longitude: Double = 0.0,
    @SerializedName("Altitude") val altitude: Double = 0.0,
    @SerializedName("Heading") val heading: Float = 0f,
    
    // Raw physical sensor values for HUD display
    val rawAccelX: Float = 0f, // m/s^2
    val rawAccelY: Float = 0f, // m/s^2
    val rawAccelZ: Float = 9.81f, // m/s^2
    val gyroRate: Float = 0.14f, // rad/s
    val wallClockTime: Long = System.currentTimeMillis()
)
