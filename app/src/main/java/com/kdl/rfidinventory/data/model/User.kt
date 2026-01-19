// data/model/User.kt
package com.kdl.rfidinventory.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val uid: Int,
    val username: String,
    val name: String,
    val role: String,
    val department: String,
    val permissions: List<String>,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("last_login")
    val lastLogin: String? = null
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    val role: String,
    val username: String,
    val department: String,
    val permissions: List<String>
)

data class LogoutResponse(
    val message: String
)