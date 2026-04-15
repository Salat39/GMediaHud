package com.salat.gmediahud

import android.app.Application
import com.google.firebase.FirebaseApp
import com.salat.gmediahud.logs.ExecTraceTree
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        timberInit()
        FirebaseApp.initializeApp(this)
    }

    private fun timberInit() {
        if (BuildConfig.DEBUG) {
            Timber.plant(ExecTraceTree())
        } else {
            // For release builds, consider using a different tree, like Crashlytics
            // Timber.plant(new CrashlyticsTree());
        }
    }
}
