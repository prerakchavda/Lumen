package com.seerflix.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.feature.details.DetailsScreen
import com.seerflix.tv.feature.home.HomeScreen
import com.seerflix.tv.feature.player.PlayerScreen
import com.seerflix.tv.feature.profiles.ProfilePickerScreen
import com.seerflix.tv.feature.requests.RequestsScreen
import com.seerflix.tv.feature.search.SearchScreen
import com.seerflix.tv.feature.setup.SetupScreen
import com.seerflix.tv.ui.theme.NetflixColors
import com.seerflix.tv.ui.theme.SeerflixTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeerflixTheme {
                SeerflixNavGraph(authRepository)
            }
        }
    }
}

private object Routes {
    const val SETUP = "setup"
    const val PROFILES = "profiles"
    const val HOME = "home"
    const val SEARCH = "search"
    const val REQUESTS = "requests"
    const val DETAILS = "details/{tmdbId}/{jellyfinId}/{mediaType}"
    const val PLAYER = "player/{itemId}"

    fun details(item: UnifiedMediaItem): String {
        val tmdb = item.tmdbId ?: 0
        val jf = item.jellyfinId ?: "none"
        return "details/$tmdb/$jf/${item.mediaType.name}"
    }

    fun details(tmdbId: Int, mediaType: MediaType): String =
        "details/$tmdbId/none/${mediaType.name}"

    fun player(itemId: String): String = "player/$itemId"
}

@Composable
private fun SeerflixNavGraph(authRepository: AuthRepository) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        authRepository.warmup()
        val config = authRepository.serverConfigFlow.first()
        val activeProfile = authRepository.activeProfileFlow.first()
        startDestination = when {
            !config.isConfigured -> Routes.SETUP
            activeProfile == null -> Routes.PROFILES
            else -> Routes.HOME
        }
    }

    if (startDestination == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixColors.Background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = NetflixColors.NetflixRed)
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Routes.PROFILES) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PROFILES) {
            ProfilePickerScreen(
                onProfileSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PROFILES) { inclusive = true }
                    }
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETUP)
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenDetails = { item -> navController.navigate(Routes.details(item)) },
                onPlayDirect = { itemId -> navController.navigate(Routes.player(itemId)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenRequests = { navController.navigate(Routes.REQUESTS) },
                onSwitchProfile = { navController.navigate(Routes.PROFILES) },
                onOpenSettings = { navController.navigate(Routes.SETUP) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenDetails = { item -> navController.navigate(Routes.details(item)) }
            )
        }

        composable(Routes.REQUESTS) {
            RequestsScreen(
                onBack = { navController.popBackStack() },
                onOpenRequestDetails = { tmdbId, mediaType ->
                    navController.navigate(Routes.details(tmdbId, mediaType))
                }
            )
        }

        composable(
            route = Routes.DETAILS,
            arguments = listOf(
                navArgument("tmdbId") { type = NavType.StringType },
                navArgument("jellyfinId") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType }
            )
        ) {
            DetailsScreen(
                onBack = { navController.popBackStack() },
                onPlayItem = { itemId -> navController.navigate(Routes.player(itemId)) },
                onOpenDetails = { item -> navController.navigate(Routes.details(item)) }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType }
            )
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
