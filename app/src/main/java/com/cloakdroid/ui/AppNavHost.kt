package com.cloakdroid.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cloakdroid.CloakDroidApplication
import com.cloakdroid.di.AppModule
import com.cloakdroid.engine.GeckoSessionManager
import com.cloakdroid.ui.browser.BrowserScreen
import com.cloakdroid.ui.profiles.ProfileEditorScreen
import com.cloakdroid.ui.profiles.ProfileListScreen
import com.cloakdroid.ui.profiles.ProfileViewModel
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface GeckoSessionManagerEntryPoint {
    fun sessionManager(): GeckoSessionManager
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "profiles") {
        composable("profiles") { backStackEntry ->
            val viewModel: ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            ProfileListScreen(
                viewModel = viewModel,
                onLaunch = { id -> navController.navigate("browser/$id") },
                onEdit = { id -> navController.navigate("editor/$id") }
            )
        }
        composable(
            route = "editor/{profileId}",
            arguments = listOf(navArgument("profileId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val viewModel: ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            ProfileEditorScreen(
                profileId = backStackEntry.arguments?.getString("profileId"),
                viewModel = viewModel,
                onDone = { navController.popBackStack() }
            )
        }
        composable(
            route = "browser/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { backStackEntry ->
            val viewModel: ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            val sessionManager = EntryPointAccessors.fromApplication(
                context.applicationContext as CloakDroidApplication,
                GeckoSessionManagerEntryPoint::class.java
            ).sessionManager()
            BrowserScreen(
                profileId = backStackEntry.arguments?.getString("profileId") ?: "",
                viewModel = viewModel,
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
