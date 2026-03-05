package com.obfs.encrypt.security

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.obfs.encrypt.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages app lock functionality with biometric authentication.
 * 
 * Features:
 * - Automatic lock when app is backgrounded
 * - Configurable lock timeout
 * - Biometric authentication unlock
 * - State flow for lock status observation
 * 
 * Usage:
 * ```kotlin
 * // In Application class
 * class MyApplication : Application() {
 *     val appLockManager: AppLockManager by lazy {
 *         AppLockManager(this, settingsRepository)
 *     }
 *     
 *     override fun onCreate() {
 *         super.onCreate()
 *         appLockManager.init()
 *     }
 * }
 * 
 * // In Activity/Composable
 * val isLocked by appLockManager.isLocked.collectAsState()
 * if (isLocked) {
 *     BiometricAuthDialog(onAuthSuccess = { appLockManager.unlock() })
 * }
 * ```
 */
@Singleton
class AppLockManager @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isLockEnabled = MutableStateFlow(false)
    val isLockEnabled: StateFlow<Boolean> = _isLockEnabled.asStateFlow()

    private var lockTimeoutMs = 0L
    private var backgroundTime = 0L
    private var isInBackground = false

    /**
     * Initialize the app lock manager.
     * Call this in Application.onCreate()
     */
    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        scope.launch {
            settingsRepository.appLockEnabled.collect { enabled ->
                _isLockEnabled.value = enabled
                if (!enabled) {
                    _isLocked.value = false
                }
            }
        }

        scope.launch {
            settingsRepository.appLockTimeout.collect { timeout ->
                lockTimeoutMs = timeout
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isInBackground = true
        backgroundTime = System.currentTimeMillis()
        
        // Schedule lock check based on timeout
        if (lockTimeoutMs > 0) {
            mainHandler.postDelayed({
                checkAndLock()
            }, lockTimeoutMs)
        } else {
            // Lock immediately
            _isLocked.value = true
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        isInBackground = false
        
        // Check if we should unlock based on timeout
        if (lockTimeoutMs > 0) {
            val timeInBackground = System.currentTimeMillis() - backgroundTime
            if (timeInBackground < lockTimeoutMs) {
                // Still within timeout window, don't lock
                return
            }
        }
        
        // Check lock status when coming to foreground
        checkAndLock()
    }

    /**
     * Check if app should be locked and update state.
     */
    private fun checkAndLock() {
        if (isInBackground && _isLockEnabled.value) {
            _isLocked.value = true
        }
    }

    /**
     * Unlock the app after successful authentication.
     */
    fun unlock() {
        _isLocked.value = false
    }

    /**
     * Manually lock the app.
     */
    fun lock() {
        _isLocked.value = true
    }

    /**
     * Enable app lock with biometric authentication.
     */
    fun enableAppLock() {
        scope.launch {
            settingsRepository.setAppLockEnabled(true)
        }
    }

    /**
     * Disable app lock.
     */
    fun disableAppLock() {
        scope.launch {
            settingsRepository.setAppLockEnabled(false)
            _isLocked.value = false
        }
    }

    /**
     * Set the lock timeout in milliseconds.
     * @param timeout Timeout in milliseconds (0 for immediate lock)
     */
    fun setLockTimeout(timeout: Long) {
        scope.launch {
            settingsRepository.setAppLockTimeout(timeout)
            lockTimeoutMs = timeout
        }
    }

    /**
     * Get available timeout options for UI selection.
     */
    fun getTimeoutOptions(): List<TimeoutOption> {
        return listOf(
            TimeoutOption(0L, "Immediately"),
            TimeoutOption(5_000L, "5 seconds"),
            TimeoutOption(15_000L, "15 seconds"),
            TimeoutOption(30_000L, "30 seconds"),
            TimeoutOption(60_000L, "1 minute"),
            TimeoutOption(300_000L, "5 minutes"),
            TimeoutOption(900_000L, "15 minutes")
        )
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    /**
     * Timeout option for UI selection.
     */
    data class TimeoutOption(
        val milliseconds: Long,
        val displayName: String
    )
}
