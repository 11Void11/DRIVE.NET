package com.safecruise.app.data.model

data class TripRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dateStr: String,
    val distanceKm: Float,
    val durationSec: Long,
    val avgSpeedKph: Float,
    val maxSpeedKph: Float,
    val riskScore: Int,
    val status: String, // "Optimal", "Moderate", "High Risk"
    val startLocation: String,
    val endLocation: String
)
