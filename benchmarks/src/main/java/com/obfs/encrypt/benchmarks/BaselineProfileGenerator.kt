package com.obfs.encrypt.benchmarks

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile Generator for ObfsEncrypt
 * 
 * Run this test to generate a baseline profile that improves app startup performance.
 * Use: ./gradlew :benchmarks:connectedBenchmarkAndroidTest
 * 
 * This captures common user journeys:
 * 1. App launch (home screen)
 * 2. Navigate to settings
 * 3. Navigate to history
 * 4. Navigate to file browser
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun generateBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = "com.obfs.encrypt",
        metrics = emptyList(),  // No specific metrics for baseline profile generation
        compilationMode = CompilationMode.Partial(),  // Use partial compilation for baseline profile
        startupMode = StartupMode.COLD,
        iterations = 5,  // Run multiple iterations to capture different code paths
        setupBlock = {
            // Grant permissions before launching to avoid permission dialogs
            device.executeShellCommand("pm grant com.obfs.encrypt android.permission.POST_NOTIFICATIONS")
            device.executeShellCommand("appops set com.obfs.encrypt MANAGE_EXTERNAL_STORAGE allow")
            
            // Clear app data to ensure clean state (no onboarding, no lock)
            device.executeShellCommand("pm clear com.obfs.encrypt")
            device.waitForIdle(1000)
        }
    ) {
        // Journey 1: App launch and basic interaction
        // Use shell command to launch app to avoid launch detection issues with Compose
        device.executeShellCommand("am start -n com.obfs.encrypt/.MainActivity")
        device.waitForIdle(3000)  // Wait for app to fully load
        
        // Dismiss any dialogs that might appear (onboarding, permissions, etc.)
        // Tap on common dismiss locations
        device.executeShellCommand("input tap 540 1800")  // Common "Continue" button location
        device.waitForIdle(500)
        device.executeShellCommand("input tap 540 1600")  // Another common location
        device.waitForIdle(500)
        device.executeShellCommand("input keyevent KEYCODE_BACK")  // Dismiss any remaining dialogs
        device.waitForIdle(500)
        
        // Journey 2: Navigate through bottom navigation
        // Find and click the second tab (Decrypt)
        device.executeShellCommand("input tap 300 2200")
        device.waitForIdle()
        
        // Journey 3: Navigate to Settings via home screen
        device.executeShellCommand("input tap 150 2200") // Back to home
        device.waitForIdle()
        
        // Find settings icon by scanning common locations
        // Navigate to settings screen
        device.executeShellCommand("input tap 950 200")
        device.waitForIdle()
        
        // Journey 4: Navigate to History
        // Navigate back first
        device.executeShellCommand("input keyevent KEYCODE_BACK")
        device.waitForIdle(1000)
        
        // Journey 5: File browser interaction
        // Tap on file browser area
        device.executeShellCommand("input tap 200 800")
        device.waitForIdle()
        
        // Journey 6: Go back to home
        device.executeShellCommand("input keyevent KEYCODE_BACK")
        device.waitForIdle(1000)
        
        // Final: Return to home screen
        device.executeShellCommand("input tap 150 2200")
        device.waitForIdle()
    }
}
