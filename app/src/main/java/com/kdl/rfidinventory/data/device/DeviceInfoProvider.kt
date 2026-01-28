package com.kdl.rfidinventory.data.device

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import com.kdl.rfidinventory.BuildConfig
import com.kdl.rfidinventory.data.local.preferences.PreferencesManager
import com.kdl.rfidinventory.data.model.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.net.NetworkInterface
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    /**
     * 獲取設備唯一 ID（MediaDrm ID）
     */
    fun getDeviceId(): String {
        return try {
            val widevineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
            val mediaDrm = MediaDrm(widevineUuid)
            val deviceId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            mediaDrm.release()

            deviceId.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get MediaDrm ID, using Android ID as fallback")
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }
    }

    /**
     * 獲取設備名稱
     */
    fun getDeviceName(): String {
        return preferencesManager.getCustomDeviceName() ?: "Warehouse Scanner ${Build.MODEL}"
    }

    /**
     * 獲取設備型號
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }

    /**
     * 獲取 Android 版本
     */
    fun getOsVersion(): String {
        return "Android ${Build.VERSION.RELEASE}"
    }

    /**
     * 獲取 App 版本
     */
    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    /**
     * 獲取設備 IP 地址
     */
    fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // 只取 IPv4 且非 loopback 地址
                    if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get IP address")
        }
        return "0.0.0.0"
    }

    /**
     * 獲取完整設備信息
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceId = getDeviceId(),
            name = getDeviceName(),
            model = getDeviceModel(),
            osVersion = getOsVersion(),
            appVersion = getAppVersion(),
            ipAddress = getIpAddress()
        )
    }
}