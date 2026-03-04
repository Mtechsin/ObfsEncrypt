package com.obfs.encrypt

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.PermissionHelper
import com.obfs.encrypt.performance.CoilImageLoader
import com.obfs.encrypt.performance.MemoryManager
import com.obfs.encrypt.performance.MemoryManager.Companion.isMemoryLow
import com.obfs.encrypt.performance.memoryManager
import com.obfs.encrypt.ui.components.PermissionDialog
import com.obfs.encrypt.ui.components.PermissionGrantedDialog
import com.obfs.encrypt.ui.navigation.AppNavigation
import com.obfs.encrypt.ui.screens.AppDirectoryManagerInstanceHolder
import com.obfs.encrypt.ui.theme.ObfsEncryptTheme
import com.obfs.encrypt.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appDirectoryManager: AppDirectoryManager

    private var mainViewModel: MainViewModel? = null
    private lateinit var memoryManager: MemoryManager

    var hasPermission by mutableStateOf(false)
    var showPermissionDialog by mutableStateOf(false)
    var showPermissionGrantedDialog by mutableStateOf(false)
    var outputFolderPath by mutableStateOf("")
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            hasPermission = true
            outputFolderPath = appDirectoryManager.getOutputDirectoryPath()
            showPermissionGrantedDialog = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for modern Android
        enableEdgeToEdge()

        // Initialize memory manager for automatic memory optimization
        memoryManager = application.memoryManager
        
        // Register memory trim callbacks for cache cleanup
        memoryManager.registerTrimCallback { level ->
            when (level) {
                MemoryManager.MemoryLevel.MODERATE -> {
                    // Trim image caches on moderate memory pressure
                    CoilImageLoader.clearMemoryCache()
                }
                MemoryManager.MemoryLevel.LOW,
                MemoryManager.MemoryLevel.CRITICAL -> {
                    // Clear all caches on low/critical memory
                    CoilImageLoader.clearCache(this)
                }
                MemoryManager.MemoryLevel.NORMAL -> {}
            }
        }

        AppDirectoryManagerInstanceHolder.manager = appDirectoryManager

        // Initial permission check
        hasPermission = PermissionHelper.hasStoragePermission(this)

        if (!hasPermission) {
            showPermissionDialog = true
        } else {
            outputFolderPath = appDirectoryManager.getOutputDirectoryPath()
        }

        setContent {
            ObfsEncryptTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }

                // Show permission dialogs when needed
                if (showPermissionDialog) {
                    PermissionDialog(
                        onGrantPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // Android 11+: Open settings directly
                                PermissionHelper.requestStoragePermission(this@MainActivity)
                            } else {
                                // Android 10 and below: Use runtime permission launcher
                                permissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                            showPermissionDialog = false
                        },
                        onDismiss = {
                            showPermissionDialog = false
                        },
                        showDismiss = true
                    )
                }

                if (showPermissionGrantedDialog) {
                    PermissionGrantedDialog(
                        outputFolderPath = outputFolderPath,
                        onContinue = {
                            showPermissionGrantedDialog = false
                        }
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val hadPermission = hasPermission
                hasPermission = PermissionHelper.hasStoragePermission(this@MainActivity)

                if (hasPermission && !hadPermission) {
                    outputFolderPath = appDirectoryManager.getOutputDirectoryPath()
                    showPermissionDialog = false
                    showPermissionGrantedDialog = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val hadPermission = hasPermission
        hasPermission = PermissionHelper.hasStoragePermission(this)

        if (hasPermission && !hadPermission) {
            outputFolderPath = appDirectoryManager.getOutputDirectoryPath()
            showPermissionDialog = false
            showPermissionGrantedDialog = true
        }

        // Perform memory cleanup on resume if memory is low
        if (isMemoryLow()) {
            memoryManager.performCleanup()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Force cleanup on low memory
        memoryManager.performCleanup()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up Coil image loader to prevent leaks
        CoilImageLoader.dispose()
        // Unregister memory manager
        memoryManager.dispose()
    }
}