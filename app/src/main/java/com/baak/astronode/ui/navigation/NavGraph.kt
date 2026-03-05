package com.baak.astronode.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.baak.astronode.ui.screen.history.HistoryScreen
import com.baak.astronode.ui.screen.home.HomeScreen
import com.baak.astronode.ui.screen.map.MapScreen
import com.baak.astronode.ui.screen.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val MAP = "map"
    const val HISTORY = "history"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomNav = currentRoute in listOf(Routes.HOME, Routes.MAP, Routes.HISTORY)

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar {
                    listOf(
                        Triple(Routes.HOME, Icons.Default.Home, "Ölçüm"),
                        Triple(Routes.MAP, Icons.Default.Place, "Harita"),
                        Triple(Routes.HISTORY, Icons.AutoMirrored.Filled.List, "Geçmiş")
                    ).forEach { (route, icon, label) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
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
                HomeScreen()
            }
            composable(Routes.MAP) {
                MapScreen()
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
        }
    }
}
