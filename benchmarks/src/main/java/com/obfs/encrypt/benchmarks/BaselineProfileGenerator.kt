package com.obfs.encrypt.benchmarks

import androidx.benchmark.macro.junit4.BaselineProfileRule
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
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(
        packageName = "com.obfs.encrypt"
    ) {
        // Start the app
        startActivityAndWait()

        // Navigate through main screens to capture common paths
        device.findObject(androidx.test.uiautomator.UiSelector().text("Encrypt")).click()
        device.waitForIdle()

        device.findObject(androidx.test.uiautomator.UiSelector().text("Device")).click()
        device.waitForIdle()

        // Scroll through file list if available
        device.findObject(androidx.test.uiautomator.UiSelector().className("androidx.compose.ui.platform.ComposeView")).scroll(Direction.DOWN, 1.0f)
        device.waitForIdle()

        // Go back to Encrypt tab
        device.findObject(androidx.test.uiautomator.UiSelector().text("Encrypt")).click()
        device.waitForIdle()
    }
}
