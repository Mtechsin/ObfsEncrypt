package com.obfs.encrypt

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.security.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * ObfsApp - Main Application Class.
 *
 * Why this approach:
 * We use Hilt for dependency injection to have a cleaner separation of concerns.
 * Hilt simplifies the Dagger setup and manages lifecycles for ViewModels and Repositories effectively.
 */
@HiltAndroidApp
class ObfsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var appLockManager: AppLockManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // WorkManager auto-initializes using Configuration.Provider
        
        // Initialize app lock manager for process-wide lifecycle observation
        appLockManager.init()
    }
}
