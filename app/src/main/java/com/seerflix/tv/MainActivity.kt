package com.seerflix.tv

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.feature.details.DetailsScreen
import com.seerflix.tv.feature.home.HomeScreen
import com.seerflix.tv.feature.player.PlayerScreen
import com.seerflix.tv.feature.profiles.ProfilePickerScreen
import com.seerflix.tv.feature.requests.RequestsScreen
import com.seerflix.tv.feature.search.SearchScreen
import com.seerflix.tv.feature.setup.SetupScreen
import com.seerflix.tv.ui.theme.SeerflixTheme
import com.seerflix.tv.ui.theme.SeerflixTokens
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    private var latestIntentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestIntentState = intent
        setContent {
            val activeProfile by authRepository.activeProfileFlow.collectAsState(initial = null)
            val activePreset = activeProfile?.uiPreset ?: UiPreset.NETFLIX
            val activeLanguage = activeProfile?.uiLanguage ?: "en"
            SeerflixTheme(preset = activePreset, uiLanguage = activeLanguage) {
                SeerflixNavGraph(
                    authRepository = authRepository,
                    incomingIntent = latestIntentState,
                    onIntentHandled = { latestIntentState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntentState = intent
    }
}

private object Routes {
    const val SETUP = "setup"
    const val PROFILES = "profiles"
    const val HOME = "home"
    const val SEARCH = "search?query={query}&personId={personId}&personName={personName}"
    const val REQUESTS = "requests"
    const val DETAILS = "details/{tmdbId}/{jellyfinId}/{mediaType}"
    const val PLAYER = "player/{itemId}?mediaSourceId={mediaSourceId}"
    const val TRAILER = "trailer?title={title}&youtubeKey={youtubeKey}&localTrailerId={localTrailerId}"

    fun search(query: String = "", personId: Int? = null, personName: String? = null): String {
        val encQ = Uri.encode(query)
        val pid = personId?.toString() ?: "0"
        val encName = Uri.encode(personName.orEmpty())
        return "search?query=$encQ&personId=$pid&personName=$encName"
    }

    fun details(item: UnifiedMediaItem): String {
        val tmdb = item.tmdbId ?: 0
        val jf = item.jellyfinId ?: "none"
        return "details/$tmdb/$jf/${item.mediaType.name}"
    }

    fun details(tmdbId: Int, mediaType: MediaType): String =
        "details/$tmdbId/none/${mediaType.name}"

    fun player(itemId: String, mediaSourceId: String? = null): String {
        val encItem = Uri.encode(itemId)
        val encSrc = Uri.encode(mediaSourceId.orEmpty())
        return "player/$encItem?mediaSourceId=$encSrc"
    }

    fun trailer(title: String, youtubeKey: String?, localTrailerId: String?): String {
        val encTitle = Uri.encode(title)
        val encKey = Uri.encode(youtubeKey ?: "none")
        val encLocal = Uri.encode(localTrailerId ?: "none")
        return "trailer?title=$encTitle&youtubeKey=$encKey&localTrailerId=$encLocal"
    }
}

private fun resolveDeepLinkRoute(intent: Intent?): String? {
    if (intent == null) return null
    if (intent.action == Intent.ACTION_SEARCH) {
        val q = intent.getStringExtra(SearchManager.QUERY).orEmpty().trim()
        if (q.isNotBlank()) return Routes.search(query = q)
    }
    val data = intent.data ?: return null
    if (data.scheme != "seerflix") return null
    val segments = data.pathSegments
    return when (data.host) {
        "player" -> segments.firstOrNull()?.let { Routes.player(it) }
        "details" -> {
            val tmdb = segments.getOrNull(0)?.toIntOrNull() ?: 0
            val jf = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "none"
            val type = segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: MediaType.MOVIE.name
            "details/$tmdb/$jf/$type"
        }
        "search" -> Routes.search(query = data.getQueryParameter("q").orEmpty())
        "home" -> Routes.HOME
        else -> null
    }
}

@Composable
private fun SeerflixNavGraph(
    authRepository: AuthRepository,
    incomingIntent: Intent?,
    onIntentHandled: () -> Unit
) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        authRepository.warmup()
        val config = authRepository.serverConfigFlow.first()
        val activeProfile = authRepository.activeProfileFlow.first()
        startDestination = when {
            !config.isConfigured -> Routes.SETUP
            activeProfile == null || activeProfile.hasPin -> Routes.PROFILES
            else -> Routes.HOME
        }
    }

    LaunchedEffect(startDestination, incomingIntent) {
        if (startDestination == Routes.HOME && incomingIntent != null) {
            val route = resolveDeepLinkRoute(incomingIntent)
            onIntentHandled()
            if (route != null && route != Routes.HOME) {
                navController.navigate(route)
            }
        }
    }

    if (startDestination == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SeerflixTokens.colors.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SeerflixTokens.colors.accent)
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
                onOpenSearch = { navController.navigate(Routes.search()) },
                onOpenRequests = { navController.navigate(Routes.REQUESTS) },
                onSwitchProfile = { navController.navigate(Routes.PROFILES) },
                onOpenSettings = { navController.navigate(Routes.SETUP) }
            )
        }

        composable(
            route = Routes.SEARCH,
            arguments = listOf(
                navArgument("query") { type = NavType.StringType; defaultValue = "" },
                navArgument("personId") { type = NavType.StringType; defaultValue = "0" },
                navArgument("personName") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
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
                onPlayItem = { itemId, mediaSourceId ->
                    navController.navigate(Routes.player(itemId, mediaSourceId))
                },
                onPlayTrailer = { title, ytKey, localId ->
                    navController.navigate(Routes.trailer(title, ytKey, localId))
                },
                onOpenDetails = { item -> navController.navigate(Routes.details(item)) },
                onSearchActor = { name, personId ->
                    navController.navigate(
                        Routes.search(
                            query = if (personId == null) name else "",
                            personId = personId,
                            personName = name
                        )
                    )
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("mediaSourceId") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.TRAILER,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType; defaultValue = "Trailer" },
                navArgument("youtubeKey") { type = NavType.StringType; defaultValue = "none" },
                navArgument("localTrailerId") { type = NavType.StringType; defaultValue = "none" }
            )
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}

