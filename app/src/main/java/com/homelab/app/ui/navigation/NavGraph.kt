package com.homelab.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homelab.app.ui.screen.home.HomeScreen
import com.homelab.app.ui.screen.keys.KeyManagementScreen
import com.homelab.app.ui.screen.onboarding.OnboardingScreen
import com.homelab.app.ui.screen.settings.SettingsScreen
import com.homelab.app.ui.screen.terminal.TerminalScreen
import com.homelab.app.ui.screen.home.HomeViewModel

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Terminal : Screen("terminal/{sessionId}/{hostId}") {
        fun createRoute(sessionId: String, hostId: String) = "terminal/$sessionId/$hostId"
    }
    object KeyManagement : Screen("keys")
    object Settings : Screen("settings")
}

@Composable
fun HomelabNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Onboarding.route) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onAuthenticated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onConnectToHost = { sessionId, hostId ->
                    navController.navigate(Screen.Terminal.createRoute(sessionId, hostId))
                },
                onNavigateToKeys = { navController.navigate(Screen.KeyManagement.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("hostId") { type = NavType.StringType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getString("sessionId") ?: return@composable
            val hostId = backStack.arguments?.getString("hostId") ?: return@composable
            TerminalScreen(
                sessionId = sessionId,
                hostId = hostId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.KeyManagement.route) {
            KeyManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
