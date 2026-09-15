package com.obfs.encrypt

import android.app.Application
import androidx.work.Configuration
import com.obfs.encrypt.diagnostics.AppLogger
import com.obfs.encrypt.diagnostics.CrashHandler
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
    lateinit var settingsRepository: com.obfs.encrypt.data.SettingsRepository

    @Inject
    lateinit var appLockManager: AppLockManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        AppLogger.i("ObfsApp", "onCreate version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        // WorkManager auto-initializes using Configuration.Provider

        // Initialize app lock manager for process-wide lifecycle observation
        appLockManager.init()
    }
}
