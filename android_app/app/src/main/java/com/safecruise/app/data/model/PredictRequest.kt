package com.safecruise.app.data.model

import com.google.gson.annotations.SerializedName

data class PredictRequest(
    @SerializedName("readings") val readings: List<TelemetryPoint>
)
