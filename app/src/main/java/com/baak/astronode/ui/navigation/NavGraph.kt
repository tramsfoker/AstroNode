package com.baak.astronode.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baak.astronode.ui.screen.history.HistoryScreen
import com.baak.astronode.ui.screen.home.HomeScreen
import com.baak.astronode.ui.screen.session.SessionScreen
import com.baak.astronode.ui.screen.map.MapScreen
import com.baak.astronode.ui.screen.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val MAP = "map"
    const val MAP_FOCUS = "map/{lat}/{lng}"
    const val HISTORY = "history"
    const val SESSION = "session"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomNav = currentRoute in listOf(Routes.HOME, Routes.MAP, Routes.HISTORY) ||
        currentRoute?.startsWith(Routes.MAP) == true

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar {
                    listOf(
                        Triple(Routes.HOME, Icons.Default.Science, "Ölçüm"),
                        Triple(Routes.MAP, Icons.Default.Map, "Harita"),
                        Triple(Routes.HISTORY, Icons.Default.History, "Geçmiş")
                    ).forEach { (route, icon, label) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentRoute == route ||
                                (route == Routes.MAP && currentRoute?.startsWith("map") == true),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE83E2E),
                                unselectedIconColor = Color(0xFF888888),
                                selectedTextColor = Color(0xFFE83E2E),
                                unselectedTextColor = Color(0xFF888888)
                            ),
                            onClick = {
                                val targetRoute = if (route == Routes.MAP) Routes.MAP else route
                                navController.navigate(targetRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToSession = {
                        navController.navigate(Routes.SESSION)
                    }
                )
            }
            composable(Routes.SESSION) {
                SessionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.MAP) {
                MapScreen()
            }
            composable(
                route = Routes.MAP_FOCUS,
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType },
                    navArgument("lng") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull()
                val lng = backStackEntry.arguments?.getString("lng")?.toDoubleOrNull()
                MapScreen(initialFocusLat = lat, initialFocusLng = lng)
            }
            composable(Routes.HISTORY) {
                HistoryScreen(navController = navController)
            }
        }
    }
}
