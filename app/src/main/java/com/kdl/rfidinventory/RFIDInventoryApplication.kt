package com.kdl.rfidinventory

import android.app.Application
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.util.rfid.RFIDManager
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class RFIDInventoryApplication : Application() {

    @Inject
    lateinit var rfidManager: RFIDManager

    @Inject
    lateinit var webSocketManager: WebSocketManager

    override fun onCreate() {
        super.onCreate()

        // 初始化 Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("RFIDInventoryApplication started")
    }

    override fun onTerminate() {
        super.onTerminate()
        Timber.d("RFIDInventoryApplication onTerminate, releasing RFIDManager.")
        rfidManager.release()
        webSocketManager.cleanup()
    }

}