package com.sidekeys.hibreak.ui

import android.net.Uri
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
import com.sidekeys.hibreak.feature.activitypicker.ActivityPickerScreen
import com.sidekeys.hibreak.feature.apppicker.AppPickerScreen
import com.sidekeys.hibreak.feature.capture.CaptureScreen
import com.sidekeys.hibreak.feature.charge.ChargeLimitScreen
import com.sidekeys.hibreak.feature.home.HomeScreen
import com.sidekeys.hibreak.feature.mapping.MappingScreen
import com.sidekeys.hibreak.feature.profiles.AppProfileScreen
import com.sidekeys.hibreak.feature.profiles.AppProfilesScreen
import com.sidekeys.hibreak.feature.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture?pkg={pkg}&label={label}"
    const val MAPPING = "mapping/{keyCode}?pkg={pkg}&label={label}"
    const val APP_PICKER = "apppicker/{purpose}"
    const val ACTIVITY_PICKER = "activitypicker/{pkg}?label={label}"
    const val SETTINGS = "settings"
    const val CHARGE = "charge"
    const val PROFILES = "profiles"
    const val PROFILE = "profile/{pkg}?label={label}"

    /** Why the app picker was opened — decides where its result goes. */
    const val PURPOSE_LAUNCH_APP = "launch"
    const val PURPOSE_ACTIVITY = "activity"
    const val PURPOSE_PROFILE = "profile"

    private fun enc(s: String?) = if (s == null) "" else Uri.encode(s)

    fun capture(pkg: String? = null, label: String? = null) =
        "capture?pkg=${enc(pkg)}&label=${enc(label)}"

    fun mapping(keyCode: Int, pkg: String? = null, label: String? = null) =
        "mapping/$keyCode?pkg=${enc(pkg)}&label=${enc(label)}"

    fun appPicker(purpose: String) = "apppicker/$purpose"

    fun activityPicker(pkg: String, label: String?) =
        "activitypicker/${enc(pkg)}?label=${enc(label)}"

    fun profile(pkg: String, label: String?) = "profile/${enc(pkg)}?label=${enc(label)}"
}

/** Result keys used via SavedStateHandle. Values are "$data\n$label". */
const val PICKED_APP_RESULT_KEY = "picked_app"
const val PICKED_ACTIVITY_RESULT_KEY = "picked_activity"

private val optionalString = navArgument("pkg") { type = NavType.StringType; defaultValue = "" }
private val optionalLabel = navArgument("label") { type = NavType.StringType; defaultValue = "" }

private fun String?.orNullIfBlank(): String? = if (isNullOrBlank()) null else this

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
                    onAddKey = { navController.navigate(Routes.capture()) },
                    onEditKey = { keyCode -> navController.navigate(Routes.mapping(keyCode)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenChargeLimit = { navController.navigate(Routes.CHARGE) },
                    onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                )
            }
            composable(
                route = Routes.CAPTURE,
                arguments = listOf(optionalString, optionalLabel),
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg").orNullIfBlank()
                val label = entry.arguments?.getString("label").orNullIfBlank()
                CaptureScreen(
                    onCaptured = { keyCode ->
                        navController.navigate(Routes.mapping(keyCode, pkg, label)) {
                            // Return to the profile (or home) after saving, not to capture.
                            popUpTo(if (pkg != null) Routes.PROFILE else Routes.HOME)
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.MAPPING,
                arguments = listOf(
                    navArgument("keyCode") { type = NavType.IntType },
                    optionalString,
                    optionalLabel,
                ),
            ) { entry ->
                MappingScreen(
                    keyCode = entry.arguments?.getInt("keyCode") ?: 0,
                    packageName = entry.arguments?.getString("pkg").orNullIfBlank(),
                    appLabel = entry.arguments?.getString("label").orNullIfBlank(),
                    navController = navController,
                    backStackEntry = entry,
                )
            }
            composable(
                route = Routes.APP_PICKER,
                arguments = listOf(navArgument("purpose") { type = NavType.StringType }),
            ) { entry ->
                val purpose = entry.arguments?.getString("purpose") ?: Routes.PURPOSE_LAUNCH_APP
                AppPickerScreen(
                    onPicked = { packageName, label ->
                        // Guard against double taps on the sluggish e-ink panel:
                        // only act while this picker is still the top entry.
                        if (navController.currentBackStackEntry != entry) return@AppPickerScreen
                        when (purpose) {
                            Routes.PURPOSE_ACTIVITY -> {
                                navController.navigate(Routes.activityPicker(packageName, label)) {
                                    popUpTo(Routes.APP_PICKER) { inclusive = true }
                                }
                            }
                            Routes.PURPOSE_PROFILE -> {
                                navController.navigate(Routes.profile(packageName, label)) {
                                    popUpTo(Routes.APP_PICKER) { inclusive = true }
                                }
                            }
                            else -> {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(PICKED_APP_RESULT_KEY, "$packageName\n$label")
                                navController.popBackStack()
                            }
                        }
                    },
                    onCancel = {
                        if (navController.currentBackStackEntry == entry) navController.popBackStack()
                    },
                )
            }
            composable(
                route = Routes.ACTIVITY_PICKER,
                arguments = listOf(
                    navArgument("pkg") { type = NavType.StringType },
                    optionalLabel,
                ),
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                val label = entry.arguments?.getString("label").orNullIfBlank()
                ActivityPickerScreen(
                    packageName = pkg,
                    appLabel = label,
                    onPicked = { component, activityLabel ->
                        if (navController.currentBackStackEntry != entry) return@ActivityPickerScreen
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(PICKED_ACTIVITY_RESULT_KEY, "$component\n$activityLabel")
                        navController.popBackStack()
                    },
                    onCancel = {
                        if (navController.currentBackStackEntry == entry) navController.popBackStack()
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHARGE) {
                ChargeLimitScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PROFILES) {
                AppProfilesScreen(
                    onBack = { navController.popBackStack() },
                    onAddApp = { navController.navigate(Routes.appPicker(Routes.PURPOSE_PROFILE)) },
                    onOpenProfile = { pkg, label -> navController.navigate(Routes.profile(pkg, label)) },
                )
            }
            composable(
                route = Routes.PROFILE,
                arguments = listOf(
                    navArgument("pkg") { type = NavType.StringType },
                    optionalLabel,
                ),
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                val label = entry.arguments?.getString("label").orNullIfBlank()
                AppProfileScreen(
                    packageName = pkg,
                    appLabel = label,
                    onBack = { navController.popBackStack() },
                    onAddKey = { navController.navigate(Routes.capture(pkg, label)) },
                    onEditKey = { keyCode -> navController.navigate(Routes.mapping(keyCode, pkg, label)) },
                )
            }
        }
    }
}
