package com.homelab.app.data.remote

import com.homelab.app.data.remote.dto.DevicesResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface TailscaleApiService {
    @GET("api/v2/tailnet/{tailnet}/devices")
    suspend fun getDevices(
        @Path("tailnet") tailnet: String
    ): DevicesResponse
}
