package com.safecruise.app.data.api

import com.safecruise.app.data.model.PredictRequest
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.V2VLocationRequest
import com.safecruise.app.data.model.V2VLocationResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SafeCruiseApiService {

    @POST("predict")
    suspend fun predictRisk(
        @Body request: PredictRequest
    ): Response<PredictResponse>

    @POST("api/v2v/location")
    suspend fun updateV2VLocation(
        @Body request: V2VLocationRequest
    ): Response<V2VLocationResponse>

    @GET("api/v2v/nearby")
    suspend fun getNearbyVehicles(
        @Query("vehicle_id") vehicleId: String
    ): Response<V2VLocationResponse>

    @POST("api/v2v/session/leave")
    suspend fun leaveV2VSession(
        @Query("vehicle_id") vehicleId: String
    ): Response<Map<String, Any>>

    @GET("/")
    suspend fun pingServer(): Response<Map<String, Any>>
}
