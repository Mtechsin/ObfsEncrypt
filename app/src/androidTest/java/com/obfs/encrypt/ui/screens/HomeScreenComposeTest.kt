package com.obfs.encrypt.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.security.BiometricAuthManager
import com.obfs.encrypt.ui.theme.ObfsEncryptTheme
import com.obfs.encrypt.viewmodel.MainViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Compose UI tests for HomeScreen.
 * 
 * These tests verify:
 * - UI components are displayed correctly
 * - User interactions work as expected
 * - Navigation buttons respond to clicks
 */
class HomeScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mainViewModel: MainViewModel
    private lateinit var encryptionHelper: EncryptionHelper
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appDirectoryManager: AppDirectoryManager
    private lateinit var biometricAuthManager: BiometricAuthManager

    @Before
    fun setup() {
        // Create mocks
        encryptionHelper = mock()
        settingsRepository = mock()
        appDirectoryManager = mock()
        biometricAuthManager = mock()

        // Note: Full ViewModel initialization requires Android environment
        // For UI tests, we focus on composables with mocked data
    }

    @Test
    fun homeScreen_displaysHeroHeader() {
        composeTestRule.setContent {
            ObfsEncryptTheme {
                ModernHeroHeader(encryptedCount = 0)
            }
        }

        // Verify hero header is displayed
        composeTestRule.onNodeWithText("Obfs Encrypt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Military-grade encryption for your files").assertIsDisplayed()
        composeTestRule.onNodeWithText("AES-256-GCM").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysQuickActionsSection() {
        composeTestRule.setContent {
            ObfsEncryptTheme {
                ModernQuickActionGrid(
                    onSingleFile = {},
                    onMultiFile = {},
                    onFolder = {}
                )
            }
        }

        // Verify quick action cards are displayed
        composeTestRule.onNodeWithText("Single File").assertIsDisplayed()
        composeTestRule.onNodeWithText("Multi File").assertIsDisplayed()
        composeTestRule.onNodeWithText("Folder").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysToolsSection() {
        composeTestRule.setContent {
            ObfsEncryptTheme {
                ModernToolsSection(
                    onDecryptClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // Verify tools section is displayed
        composeTestRule.onNodeWithText("Decrypt Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun homeScreen_quickActionCards_areClickable() {
        var singleFileClicked = false
        var multiFileClicked = false
        var folderClicked = false

        composeTestRule.setContent {
            ObfsEncryptTheme {
                ModernQuickActionGrid(
                    onSingleFile = { singleFileClicked = true },
                    onMultiFile = { multiFileClicked = true },
                    onFolder = { folderClicked = true }
                )
            }
        }

        // Click on action cards and verify callbacks
        composeTestRule.onNodeWithText("Single File").performClick()
        assert(singleFileClicked) { "Single File click should trigger callback" }

        composeTestRule.onNodeWithText("Multi File").performClick()
        assert(multiFileClicked) { "Multi File click should trigger callback" }

        composeTestRule.onNodeWithText("Folder").performClick()
        assert(folderClicked) { "Folder click should trigger callback" }
    }

    @Test
    fun homeScreen_toolsSectionButtons_areClickable() {
        var decryptClicked = false
        var settingsClicked = false

        composeTestRule.setContent {
            ObfsEncryptTheme {
                ModernToolsSection(
                    onDecryptClick = { decryptClicked = true },
                    onSettingsClick = { settingsClicked = true }
                )
            }
        }

        // Click tool buttons and verify callbacks
        composeTestRule.onNodeWithText("Decrypt Files").performClick()
        assert(decryptClicked) { "Decrypt click should trigger callback" }

        composeTestRule.onNodeWithText("Settings").performClick()
        assert(settingsClicked) { "Settings click should trigger callback" }
    }

    @Test
    fun homeScreen_securityTipsSection_isDisplayed() {
        composeTestRule.setContent {
            ObfsEncryptTheme {
                SecurityTipsCard()
            }
        }

        // Verify security tips are displayed
        composeTestRule.onNodeWithText("Security Tips").assertIsDisplayed()
    }

    @Test
    fun homeScreen_bottomNavigation_displaysBothTabs() {
        composeTestRule.setContent {
            ObfsEncryptTheme {
                AnimatedNavigationBar(
                    items = listOf(
                        BottomNavItem(
                            title = "Encrypt",
                            selectedIcon = androidx.compose.material.icons.filled.Icons.Default.Shield,
                            unselectedIcon = androidx.compose.material.icons.outlined.Icons.Outlined.Lock
                        ),
                        BottomNavItem(
                            title = "Files",
                            selectedIcon = androidx.compose.material.icons.filled.Icons.Default.Folder,
                            unselectedIcon = androidx.compose.material.icons.outlined.Icons.Outlined.Folder
                        )
                    ),
                    selectedIndex = 0,
                    onItemSelected = {}
                )
            }
        }

        // Verify both navigation items are displayed
        composeTestRule.onNodeWithText("Encrypt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Files").assertIsDisplayed()
    }

    @Test
    fun homeScreen_bottomNavigation_switchesTabs() {
        var selectedIndex = 0

        composeTestRule.setContent {
            ObfsEncryptTheme {
                AnimatedNavigationBar(
                    items = listOf(
                        BottomNavItem(
                            title = "Encrypt",
                            selectedIcon = androidx.compose.material.icons.filled.Icons.Default.Shield,
                            unselectedIcon = androidx.compose.material.icons.outlined.Icons.Outlined.Lock
                        ),
                        BottomNavItem(
                            title = "Files",
                            selectedIcon = androidx.compose.material.icons.filled.Icons.Default.Folder,
                            unselectedIcon = androidx.compose.material.icons.outlined.Icons.Outlined.Folder
                        )
                    ),
                    selectedIndex = selectedIndex,
                    onItemSelected = { index -> selectedIndex = index }
                )
            }
        }

        // Click on Files tab
        composeTestRule.onNodeWithText("Files").performClick()
        
        // Verify tab switch (would need state hoisting for full verification)
        // For now, verify the click is registered
        assert(selectedIndex == 1) { "Files tab should be selected after click" }
    }
}
