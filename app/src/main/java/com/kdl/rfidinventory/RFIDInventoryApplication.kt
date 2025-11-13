package com.kdl.rfidinventory

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class RFIDInventoryApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化 Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("RFIDInventoryApplication started")
    }
}