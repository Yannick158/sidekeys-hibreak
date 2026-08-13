package com.sidekeys.hibreak.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sidekeys.hibreak.feature.apppicker.AppPickerScreen
import com.sidekeys.hibreak.feature.capture.CaptureScreen
import com.sidekeys.hibreak.feature.home.HomeScreen
import com.sidekeys.hibreak.feature.mapping.MappingScreen
import com.sidekeys.hibreak.feature.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val MAPPING = "mapping/{keyCode}"
    const val APP_PICKER = "apppicker"
    const val SETTINGS = "settings"

    fun mapping(keyCode: Int) = "mapping/$keyCode"
}

/** Result key used by the app picker via SavedStateHandle. */
const val PICKED_APP_RESULT_KEY = "picked_app"

@Composable
fun SideKeysApp() {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize()) {
        // E-ink: every transition is disabled — animations ghost badly on e-ink panels.
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddKey = { navController.navigate(Routes.CAPTURE) },
                    onEditKey = { keyCode -> navController.navigate(Routes.mapping(keyCode)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    onCaptured = { keyCode ->
                        navController.navigate(Routes.mapping(keyCode)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.MAPPING,
                arguments = listOf(navArgument("keyCode") { type = NavType.IntType }),
            ) { entry ->
                MappingScreen(
                    keyCode = entry.arguments?.getInt("keyCode") ?: 0,
                    navController = navController,
                    backStackEntry = entry,
                )
            }
            composable(Routes.APP_PICKER) { entry ->
                AppPickerScreen(
                    onPicked = { packageName, label ->
                        // Guard against double taps on the sluggish e-ink panel:
                        // only act while this picker is still the top entry.
                        if (navController.currentBackStackEntry == entry) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(PICKED_APP_RESULT_KEY, "$packageName\n$label")
                            navController.popBackStack()
                        }
                    },
                    onCancel = {
                        if (navController.currentBackStackEntry == entry) navController.popBackStack()
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
