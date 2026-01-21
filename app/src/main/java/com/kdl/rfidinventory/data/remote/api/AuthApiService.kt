package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.model.LoginResponse
import com.kdl.rfidinventory.data.model.LogoutResponse
import com.kdl.rfidinventory.data.model.User
import retrofit2.http.*

interface AuthApiService {

    @POST("auth/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): LoginResponse

    @GET("auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): User

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): LogoutResponse
}