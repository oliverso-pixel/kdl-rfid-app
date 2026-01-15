package com.kdl.rfidinventory.util

object Constants {
    // API
    const val BASE_URL = "http://192.9.204.144:8000/api/v1/"
    const val DEFAULT_WEBSOCKET_URL = "ws://192.9.204.144:3000/ws"
    var WEBSOCKET_URL = DEFAULT_WEBSOCKET_URL

    // WebSocket
    const val WS_HEARTBEAT_INTERVAL = 30_000L // 30 s
    const val WS_TIMEOUT = 60_000L // 60 s

    // API Timeout
    const val API_TIMEOUT = 15_000L // 15 s

    // Database
    const val DATABASE_NAME = "rfid_inventory.db"

    // SharedPreferences keys
    const val PREF_NAME = "rfid_inventory_prefs"
    const val PREF_SERVER_URL = "server_url"
    const val PREF_WEBSOCKET_URL = "websocket_url"
    const val PREF_ENABLE_WEBSOCKET = "enable_websocket"
    const val PREF_SCAN_TIMEOUT = "scan_timeout"
    const val PREF_AUTO_SYNC = "auto_sync"
}