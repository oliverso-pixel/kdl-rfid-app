package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.device.DeviceInfoProvider
import com.kdl.rfidinventory.data.model.DeviceInfo
import com.kdl.rfidinventory.data.model.DeviceRegistrationRequest
import com.kdl.rfidinventory.data.remote.api.DeviceApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceApi: DeviceApi,
    private val deviceInfoProvider: DeviceInfoProvider
) {

    /**
     * 註冊設備到伺服器
     */
    suspend fun registerDevice(): Result<DeviceInfo> {
        return try {
            val deviceInfo = deviceInfoProvider.getDeviceInfo()

            val request = DeviceRegistrationRequest(
                deviceId = deviceInfo.deviceId,
                name = deviceInfo.name,
                model = deviceInfo.model,
                osVersion = deviceInfo.osVersion,
                appVersion = deviceInfo.appVersion,
                ipAddress = deviceInfo.ipAddress
            )

            Timber.d("📱 Registering device: ${request.deviceId}")
            Timber.d("📱 Device Info: name=${request.name}, model=${request.model}, os=${request.osVersion}, ip=${request.ipAddress}")

            val response = deviceApi.registerDevice(request)

            if (response.success) {
                Timber.d("✅ Device registered successfully")
                Result.success(deviceInfo)
            } else {
                Timber.e("❌ Device registration failed: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Device registration error")
            Result.failure(e)
        }
    }

    /**
     * 獲取設備信息（本地）
     */
    fun getDeviceInfo(): DeviceInfo {
        return deviceInfoProvider.getDeviceInfo()
    }

    /**
     * 獲取設備 ID（用於 WebSocket）
     */
    fun getDeviceId(): String {
        return deviceInfoProvider.getDeviceId()
    }
}