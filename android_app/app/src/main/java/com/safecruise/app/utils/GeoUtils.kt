package com.safecruise.app.utils

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculate geodesic distance in metres using the Haversine formula.
     */
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2.0) * sin(deltaPhi / 2.0) +
                cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0) * sin(deltaLambda / 2.0)
        val c = 2.0 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1.0 - a).coerceIn(0.0, 1.0)))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculate initial forward bearing from point 1 to point 2 (0..360 degrees).
     */
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    /**
     * Determine relative position of other vehicle relative to current travel direction.
     */
    fun determineRelativePosition(userHeadingDeg: Float, bearingToOtherDeg: Float): String {
        val relAngle = (bearingToOtherDeg - userHeadingDeg + 360f) % 360f
        return when {
            relAngle >= 315f || relAngle < 45f -> "AHEAD"
            relAngle in 45f..<135f -> "RIGHT"
            relAngle in 135f..<225f -> "BEHIND"
            else -> "LEFT"
        }
    }

    /**
     * Difference between two compass headings (0..180 degrees).
     */
    fun headingDifference(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    /**
     * Format distance human-readably with uncertainty bounds.
     */
    fun formatDistance(meters: Float, uncertaintyMeters: Float = 0f): String {
        return when {
            meters >= 1000f -> String.format(Locale.US, "%.1f km", meters / 1000f)
            uncertaintyMeters > 0f && uncertaintyMeters < 50f -> String.format(Locale.US, "%.0f m (±%.0fm)", meters, uncertaintyMeters)
            else -> String.format(Locale.US, "%.0f m", meters)
        }
    }
}
