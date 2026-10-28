package com.safecruise.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Validated representation of a participating vehicle detected in vicinity via V2V.
 */
data class NearbyVehicle(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val distanceMeters: Float,
    val bearingDegrees: Float,
    val relativePosition: String, // "BEHIND", "AHEAD", "LEFT", "RIGHT"
    val isAggressive: Boolean = false,
    val aggressiveReason: String = "",
    val headingDegrees: Float = 0f,
    val accuracyMeters: Float = 5f,
    val distanceUncertaintyMeters: Float = 5f,
    val closingSpeedKmh: Float = 0f,
    val roadContext: String = "NORMAL", // "NORMAL", "WINDING_ROAD", "HAIRPIN_BEND", "LOW_ACCURACY"
    val warningMessage: String? = null,
    val isUnstable: Boolean = false,
    val riskLevel: Int = 0,
    val lastUpdateAgeSeconds: Float = 0f
)

data class V2VLocationRequest(
    @SerializedName("vehicle_id") val vehicleId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy_m") val accuracyM: Float,
    @SerializedName("speed_kmh") val speedKmh: Float,
    @SerializedName("heading_deg") val headingDeg: Float,
    @SerializedName("timestamp_s") val timestampS: Double,
    @SerializedName("altitude_m") val altitudeM: Double? = null,
    @SerializedName("lateral_accel_g") val lateralAccelG: Float? = null,
    @SerializedName("yaw_rate_rads") val yawRateRads: Float? = null,
    @SerializedName("risk_level") val riskLevel: Int? = 0,
    @SerializedName("is_unstable") val isUnstable: Boolean? = false
)

data class V2VLocationResponse(
    @SerializedName("status") val status: String,
    @SerializedName("vehicle_id") val vehicleId: String,
    @SerializedName("nearby_count") val nearbyCount: Int,
    @SerializedName("nearby_vehicles") val nearbyVehicles: List<V2VVehicleDto> = emptyList(),
    @SerializedName("active_alerts") val activeAlerts: List<String> = emptyList(),
    @SerializedName("server_timestamp") val serverTimestamp: Double = 0.0
)

data class V2VVehicleDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy_m") val accuracyM: Float,
    @SerializedName("speed_kmh") val speedKmh: Float,
    @SerializedName("heading_deg") val headingDeg: Float,
    @SerializedName("distance_meters") val distanceMeters: Float,
    @SerializedName("distance_uncertainty_m") val distanceUncertaintyM: Float,
    @SerializedName("bearing_degrees") val bearingDegrees: Float,
    @SerializedName("relative_position") val relativePosition: String,
    @SerializedName("closing_speed_kmh") val closingSpeedKmh: Float = 0f,
    @SerializedName("is_aggressive") val isAggressive: Boolean = false,
    @SerializedName("is_unstable") val isUnstable: Boolean = false,
    @SerializedName("risk_level") val riskLevel: Int = 0,
    @SerializedName("warning_message") val warningMessage: String? = null,
    @SerializedName("road_context") val roadContext: String = "NORMAL",
    @SerializedName("last_update_age_s") val lastUpdateAgeS: Float = 0f
) {
    fun toNearbyVehicle(): NearbyVehicle {
        return NearbyVehicle(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            speedKmh = speedKmh,
            distanceMeters = distanceMeters,
            bearingDegrees = bearingDegrees,
            relativePosition = relativePosition,
            isAggressive = isAggressive,
            aggressiveReason = warningMessage ?: (if (isAggressive) "High risk driving pattern" else ""),
            headingDegrees = headingDeg,
            accuracyMeters = accuracyM,
            distanceUncertaintyMeters = distanceUncertaintyM,
            closingSpeedKmh = closingSpeedKmh,
            roadContext = roadContext,
            warningMessage = warningMessage,
            isUnstable = isUnstable,
            riskLevel = riskLevel,
            lastUpdateAgeSeconds = lastUpdateAgeS
        )
    }
}
