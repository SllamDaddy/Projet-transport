package com.example.gareter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gareter.data.model.RouteDirection
import com.example.gareter.service.LocationService
import com.example.gareter.ui.screens.CarnetScanScreen
import com.example.gareter.ui.screens.CaisseScreen
import com.example.gareter.ui.screens.HomeScreen
import com.example.gareter.ui.screens.LoginScreen
import com.example.gareter.ui.screens.RapportScreen
import com.example.gareter.ui.screens.SettingsScreen
import com.example.gareter.ui.screens.TrackingScreen
import com.example.gareter.ui.viewmodel.CaisseViewModel
import com.example.gareter.ui.viewmodel.HomeViewModel

private object Dest {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TRACKING = "tracking"
    const val CAISSE = "caisse"
    const val RAPPORT = "rapport"
    const val CARNET_SCAN = "carnet_scan"
}

@Composable
fun NavGraph(
    homeViewModel: HomeViewModel,
    caisseViewModel: CaisseViewModel,
) {
    val navController = rememberNavController()
    val driverAgent by homeViewModel.driverAgent.collectAsState()

    LaunchedEffect(driverAgent) {
        if (driverAgent != null) {
            navController.navigate(Dest.HOME) {
                popUpTo(Dest.LOGIN) { inclusive = true }
            }
        } else {
            navController.navigate(Dest.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Dest.LOGIN) {

        composable(Dest.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Dest.HOME) {
                        popUpTo(Dest.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Dest.HOME) {
            val trackingState by homeViewModel.trackingState.collectAsState()
            
            // Auto-redirect to tracking if active when landing on Home
            LaunchedEffect(trackingState) {
                if (trackingState is LocationService.ServiceState.Tracking) {
                    navController.navigate(Dest.TRACKING) {
                        popUpTo(Dest.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            HomeScreen(
                onGoToSettings = { navController.navigate(Dest.SETTINGS) },
                onGoToCaisse = { navController.navigate(Dest.CAISSE) },
                onStartTracking = { route, direction ->
                    homeViewModel.startTracking(route, direction)
                    navController.navigate(Dest.TRACKING)
                },
                onGoToTracking = { navController.navigate(Dest.TRACKING) },
                viewModel = homeViewModel
            )
        }

        composable(Dest.TRACKING) {
            TrackingScreen(
                onBack = { navController.popBackStack() },
                onScanQr = { navController.navigate(Dest.CARNET_SCAN) },
                viewModel = homeViewModel,
                caisseViewModel = caisseViewModel,
            )
        }

        composable(Dest.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = homeViewModel,
            )
        }

        composable(Dest.CAISSE) {
            CaisseScreen(
                onBack = { navController.popBackStack() },
                onEndSession = {
                    navController.navigate(Dest.RAPPORT) {
                        popUpTo(Dest.CAISSE) { inclusive = true }
                    }
                },
                onScanCarnet = { navController.navigate(Dest.CARNET_SCAN) },
                viewModel = caisseViewModel,
            )
        }

        composable(Dest.RAPPORT) {
            RapportScreen(
                onBack = { navController.popBackStack() },
                onNewSession = {
                    navController.navigate(Dest.CAISSE) {
                        popUpTo(Dest.HOME) { inclusive = false }
                    }
                },
                viewModel = caisseViewModel,
            )
        }

        composable(Dest.CARNET_SCAN) {
            CarnetScanScreen(
                onBack = { navController.popBackStack() },
                viewModel = caisseViewModel,
            )
        }
    }
}
