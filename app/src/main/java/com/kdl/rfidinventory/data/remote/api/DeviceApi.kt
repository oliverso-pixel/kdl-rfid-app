// data/remote/api/DeviceApi.kt
package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.model.DeviceRegistrationRequest
import com.kdl.rfidinventory.data.model.DeviceRegistrationResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface DeviceApi {
    @POST("devices/register")
    suspend fun registerDevice(
        @Body request:  DeviceRegistrationRequest
    ): DeviceRegistrationResponse
}