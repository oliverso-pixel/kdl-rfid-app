package com.kdl.rfidinventory

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RFIDApp : Application() {
    override fun onCreate() {
        super.onCreate()

    }
}