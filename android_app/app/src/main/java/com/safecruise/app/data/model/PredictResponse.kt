package com.safecruise.app.data.model

import com.google.gson.annotations.SerializedName
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class PredictResponse(
    @SerializedName("risk_level") val riskLevel: Int = 0, // 0: Low, 1: Medium, 2: High
    @SerializedName("risk_label") val riskLabel: String = "Low Risk",
    @SerializedName("behaviour") val behaviour: String? = null,
    @SerializedName("confidence_scores") val confidenceScores: Map<String, Float>? = null,
    @SerializedName("top_contributing_feature") val topFeature: String? = null,
    @SerializedName("explainability_shap_impacts") val shapImpacts: Map<String, Float>? = null,
    @SerializedName("warning") val backendWarning: String? = null,
    @SerializedName("trip_id") val tripId: String? = null,
    val calculatedScore: Int? = null,
    val eventDetail: String? = null
) {
    /**
     * Compute 0-100 risk score.
     */
    val riskScore: Int
        get() {
            if (calculatedScore != null) return calculatedScore
            val highProb = confidenceScores?.get("high_risk") ?: 0f
            val medProb = confidenceScores?.get("medium_risk") ?: 0f

            return when (riskLevel) {
                2 -> (72 + (highProb * 24f).roundToInt()).coerceIn(70, 95)
                1 -> (35 + (medProb * 20f).roundToInt()).coerceIn(30, 55)
                else -> (5 + (medProb * 8f).roundToInt()).coerceIn(5, 18)
            }
        }

    /**
     * Clean, driver-friendly status title (e.g. "Safe Driving", "Caution", "High Risk")
     */
    val primaryStatus: String
        get() = when (riskLevel) {
            2 -> "High Risk Alert"
            1 -> "Caution"
            else -> "Safe Driving"
        }

    /**
     * Clean, readable one-sentence summary showing only what the driver needs to know
     */
    val detailDescription: String
        get() {
            if (!eventDetail.isNullOrBlank()) return eventDetail

            return when (riskLevel) {
                2 -> when {
                    topFeature?.contains("Accel_Y", ignoreCase = true) == true -> "Harsh braking detected"
                    topFeature?.contains("Accel_X", ignoreCase = true) == true -> "Sharp swerve detected"
                    topFeature?.contains("Jerk", ignoreCase = true) == true -> "Sudden aggressive movement"
                    topFeature?.contains("Speed_std", ignoreCase = true) == true -> "Erratic speed variations"
                    else -> "Aggressive driving pattern"
                }
                1 -> when {
                    topFeature?.contains("Accel_Y", ignoreCase = true) == true -> "Brisk deceleration"
                    topFeature?.contains("Accel_X", ignoreCase = true) == true -> "Brisk cornering force"
                    topFeature?.contains("Jerk", ignoreCase = true) == true -> "Noticeable jerk spike"
                    else -> "Minor driving variation"
                }
                else -> "Smooth & steady driving"
            }
        }

    /**
     * Clean, subtle status footer
     */
    val statusSubtitle: String
        get() = when (riskLevel) {
            2 -> "AI Engine: High Alert"
            1 -> "AI Engine: Monitoring"
            else -> "AI Engine: Optimal"
        }
}
