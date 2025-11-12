package com.kdl.rfidinventory.util

object Constants {
    // API
    const val BASE_URL = "http://test/api"
    const val WEBSOCKET_URL = "ws://test/ws"

    // WebSocket
    const val WS_HEARTBEAT_INTERVAL = 30_000L // 30 s
    const val WS_TIMEOUT = 60_000L // 60 s

    // API Timeout
    const val API_TIMEOUT = 15_000L // 15 s

    // Database
    const val DATABASE_NAME = "rfid_inventory.db"

    // SharedPreferences / DataStore
    const val PREFS_NAME = "rfid_prefs"
    const val ADMIN_PASSWORD_KEY = "admin_password"
}