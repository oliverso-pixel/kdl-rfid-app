package com.kdl.rfidinventory.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )

    companion object {
        // WebSocket
        private const val KEY_WEBSOCKET_URL = "websocket_url"
        private const val KEY_WEBSOCKET_ENABLED = "websocket_enabled"
        const val DEFAULT_WEBSOCKET_URL = "ws://192.9.204.144:3001/ws"

        // Device
        private const val KEY_CUSTOM_DEVICE_NAME = "custom_device_name"

        // Server
        private const val KEY_SERVER_URL = "server_url"
        const val DEFAULT_SERVER_URL = "http://192.9.204.144:8000"

        // Scan
        private const val KEY_SCAN_TIMEOUT = "scan_timeout_seconds"
        const val DEFAULT_SCAN_TIMEOUT = 30

        private const val KEY_MAX_BASKETS_PER_SCAN = "max_baskets_per_scan"
        const val DEFAULT_MAX_BASKETS_PER_SCAN = 5
        const val MIN_MAX_BASKETS_PER_SCAN = 1
        const val MAX_MAX_BASKETS_PER_SCAN = 30

        // Sync
        private const val KEY_AUTO_SYNC = "auto_sync"
        const val DEFAULT_AUTO_SYNC = false
    }

    // ==================== WebSocket 設置 ====================

    fun getWebSocketUrl(): String =
        prefs.getString(KEY_WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL) ?: DEFAULT_WEBSOCKET_URL

    fun setWebSocketUrl(url: String) {
        prefs.edit { putString(KEY_WEBSOCKET_URL, url) }
    }

    fun isWebSocketEnabled(): Boolean =
        prefs.getBoolean(KEY_WEBSOCKET_ENABLED, false)

    fun setWebSocketEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WEBSOCKET_ENABLED, enabled) }
    }

    // ==================== Device ====================

    fun getCustomDeviceName(): String? =
        prefs.getString(KEY_CUSTOM_DEVICE_NAME, null)

    fun setCustomDeviceName(name: String) {
        prefs.edit { putString(KEY_CUSTOM_DEVICE_NAME, name) }
    }

    // ==================== Server ====================

    fun getServerUrl(): String =
        prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun setServerUrl(url: String) {
        prefs.edit { putString(KEY_SERVER_URL, url) }
    }

    // ==================== Scan 設定 ====================

    fun getScanTimeout(): Int =
        prefs.getInt(KEY_SCAN_TIMEOUT, DEFAULT_SCAN_TIMEOUT)

    fun setScanTimeout(seconds: Int) {
        prefs.edit { putInt(KEY_SCAN_TIMEOUT, seconds.coerceIn(5, 300)) }
    }

    /**
     * 每次掃描籃子上限（Production / Receiving / BasketManagement 共用）
     */
    fun getMaxBasketsPerScan(): Int =
        prefs.getInt(KEY_MAX_BASKETS_PER_SCAN, DEFAULT_MAX_BASKETS_PER_SCAN)

    fun setMaxBasketsPerScan(value: Int) {
        prefs.edit {
            putInt(
                KEY_MAX_BASKETS_PER_SCAN,
                value.coerceIn(MIN_MAX_BASKETS_PER_SCAN, MAX_MAX_BASKETS_PER_SCAN)
            )
        }
    }

    /**
     * Flow 形式：讓 ViewModel 可以 collect / stateIn，設定變動即時生效
     */
    val maxBasketsPerScanFlow: Flow<Int> =
        intFlow(KEY_MAX_BASKETS_PER_SCAN, DEFAULT_MAX_BASKETS_PER_SCAN)

    val scanTimeoutFlow: Flow<Int> =
        intFlow(KEY_SCAN_TIMEOUT, DEFAULT_SCAN_TIMEOUT)

    // ==================== Sync ====================

    fun isAutoSyncEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_SYNC, DEFAULT_AUTO_SYNC)

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SYNC, enabled) }
    }

    val autoSyncFlow: Flow<Boolean> =
        booleanFlow(KEY_AUTO_SYNC, DEFAULT_AUTO_SYNC)

    // ==================== Flow Helpers ====================

    /**
     * 將 SharedPreferences 某個 Int 鍵包裝成 Flow
     * 首次會 emit 當前值，之後只在該 key 變更時 emit
     */
    private fun intFlow(key: String, default: Int): Flow<Int> = callbackFlow {
        trySend(prefs.getInt(key, default))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == key) {
                trySend(sp.getInt(key, default))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
        trySend(prefs.getBoolean(key, default))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == key) {
                trySend(sp.getBoolean(key, default))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
