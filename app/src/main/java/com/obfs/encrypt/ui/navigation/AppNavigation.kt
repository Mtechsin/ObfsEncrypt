package com.obfs.encrypt.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.obfs.encrypt.ui.screens.DecryptScreen
import com.obfs.encrypt.ui.screens.FileBrowserScreen
import com.obfs.encrypt.ui.screens.HistoryScreen
import com.obfs.encrypt.ui.screens.HomeScreen
import com.obfs.encrypt.ui.screens.ProgressScreen
import com.obfs.encrypt.ui.screens.SettingsScreen
import com.obfs.encrypt.ui.theme.Motion
import com.obfs.encrypt.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Decrypt : Screen("decrypt")
    object FileBrowser : Screen("file_browser")
    object Progress : Screen("progress/{operation}") {
        fun createRoute(operation: String) = "progress/$operation"
    }
    object Settings : Screen("settings/{previousTabIndex}") {
        fun createRoute(previousTabIndex: Int) = "settings/$previousTabIndex"
    }
    object History : Screen("history")
}

/**
 * App Navigation using Compose Navigation
 * 
 * Why this approach:
 * We use `AnimatedContentTransitionScope` to provide premium sliding and fading 
 * transitions between screens. It leverages standard Navigation Compose paradigms.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel? = null
) {
    android.util.Log.d("AppNavigation", "AppNavigation COMPOSING")
    val vm = mainViewModel ?: hiltViewModel()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize()
    ) {
        composable(Screen.Home.route) { backStackEntry ->
            val savedTabIndex = backStackEntry.savedStateHandle.get<Int?>("restoreTabIndex")
            HomeScreen(
                viewModel = vm,
                onNavigateToSettings = { tabIndex ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("currentTabIndex", tabIndex)
                    navController.navigate(Screen.Settings.createRoute(tabIndex))
                },
                onNavigateToDecrypt = { navController.navigate(Screen.Decrypt.route) },
                onNavigateToFileBrowser = { navController.navigate(Screen.FileBrowser.route) },
                onNavigateToProgress = { operation -> navController.navigate(Screen.Progress.createRoute(operation)) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                restoreTabIndex = savedTabIndex
            )
        }

        composable(Screen.FileBrowser.route) {
            FileBrowserScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProgress = { operation -> navController.navigate(Screen.Progress.createRoute(operation)) },
                viewModel = vm
            )
        }

        composable(Screen.Decrypt.route) {
            DecryptScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProgress = { operation -> navController.navigate(Screen.Progress.createRoute(operation)) }
            )
        }

        composable(Screen.Progress.route) { backStackEntry ->
            val operation = backStackEntry.arguments?.getString("operation") ?: "encrypt"
            ProgressScreen(
                viewModel = vm,
                operation = operation,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Settings.route,
            arguments = listOf(
                navArgument("previousTabIndex") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            ),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(350, delayMillis = 50))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val previousTabIndex = backStackEntry.arguments?.getInt("previousTabIndex") ?: 0
            SettingsScreen(
                viewModel = vm,
                previousTabIndex = previousTabIndex,
                onNavigateBack = { tabIndex ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("restoreTabIndex", tabIndex)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.History.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(350, delayMillis = 50))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
