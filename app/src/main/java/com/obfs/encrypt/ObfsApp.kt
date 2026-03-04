package com.obfs.encrypt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ObfsApp - Main Application Class.
 * 
 * Why this approach:
 * We use Hilt for dependency injection to have a cleaner separation of concerns.
 * Hilt simplifies the Dagger setup and manages lifecycles for ViewModels and Repositories effectively.
 */
@HiltAndroidApp
class ObfsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Here we could initialize logging, crashlytics, or AppSearch if needed.
    }
}
