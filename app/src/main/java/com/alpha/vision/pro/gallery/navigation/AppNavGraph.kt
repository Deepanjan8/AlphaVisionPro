package com.alpha.vision.pro.gallery.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alpha.vision.pro.gallery.editor.ui.EditorScreen
import com.alpha.vision.pro.gallery.gallery.ui.GalleryScreen
import com.alpha.vision.pro.gallery.gallery.ui.PreviewScreen
import com.alpha.vision.pro.gallery.settings.ui.SettingsScreen
import com.alpha.vision.pro.gallery.vault.ui.VaultScreen

sealed class Screen(val route: String) {
    data object Gallery  : Screen("gallery")
    data object Preview  : Screen("preview/{mediaId}") {
        fun createRoute(mediaId: Long) = "preview/$mediaId"
    }
    data object Editor   : Screen("editor/{mediaId}") {
        fun createRoute(mediaId: Long) = "editor/$mediaId"
    }
    data object Vault    : Screen("vault")
    data object Settings : Screen("settings")
}

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Gallery.route,
        modifier         = modifier
    ) {
        composable(Screen.Gallery.route) {
            GalleryScreen(
                onMediaClick    = { mediaId -> navController.navigate(Screen.Preview.createRoute(mediaId)) },
                onVaultClick    = { navController.navigate(Screen.Vault.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            route     = Screen.Preview.route,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { backStack ->
            val mediaId = backStack.arguments?.getLong("mediaId") ?: return@composable
            PreviewScreen(
                mediaId = mediaId,
                onBack = { navController.popBackStack() },
                onEditClick = { id -> navController.navigate(Screen.Editor.createRoute(id)) }
            )
        }
        composable(
            route     = Screen.Editor.route,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { backStack ->
            val mediaId = backStack.arguments?.getLong("mediaId") ?: return@composable
            EditorScreen(mediaId = mediaId, onBack = { navController.popBackStack() })
        }
        composable(Screen.Vault.route) {
            VaultScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
