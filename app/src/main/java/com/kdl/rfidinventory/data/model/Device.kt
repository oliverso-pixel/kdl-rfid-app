// data/model/Device.kt
package com.kdl.rfidinventory.data.model

import com.google.gson.annotations.SerializedName

data class DeviceRegistrationRequest(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("model")
    val model: String,

    @SerializedName("os_version")
    val osVersion: String,

    @SerializedName("app_version")
    val appVersion: String,

    @SerializedName("ip_address")
    val ipAddress: String
)

data class DeviceRegistrationResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("device_id")
    val deviceId: String? = null
)

data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
    val ipAddress: String
)