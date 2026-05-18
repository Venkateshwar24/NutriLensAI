package com.nutrilens.nutrilensai

import android.app.Application
import com.nutrilens.nutrilensai.data.db.HealthReportDatabase
import timber.log.Timber

class NutriLensApplication : Application() {

    val database: HealthReportDatabase by lazy { HealthReportDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
