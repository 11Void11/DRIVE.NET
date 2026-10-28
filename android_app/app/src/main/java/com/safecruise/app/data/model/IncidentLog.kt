package com.safecruise.app.data.model

data class IncidentLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val severity: Int, // 0 = Safe/Info, 1 = Caution, 2 = Alert
    val detail: String,
    val speedKmh: Float,
    val locationLabel: String
)
