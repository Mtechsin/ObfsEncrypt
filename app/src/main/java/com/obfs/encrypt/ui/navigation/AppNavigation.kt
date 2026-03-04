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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.obfs.encrypt.ui.screens.DecryptScreen
import com.obfs.encrypt.ui.screens.FileBrowserScreen
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
    object Settings : Screen("settings")
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
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = vm,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToDecrypt = { navController.navigate(Screen.Decrypt.route) },
                onNavigateToFileBrowser = { navController.navigate(Screen.FileBrowser.route) },
                onNavigateToProgress = { operation -> navController.navigate(Screen.Progress.createRoute(operation)) }
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

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
