package com.seerflix.tv

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.network.JellyfinSocketClient
import com.seerflix.tv.core.network.ServerEvent
import com.seerflix.tv.core.player.SyncPlayAction
import com.seerflix.tv.core.player.SyncPlayManager
import com.seerflix.tv.ui.components.ServerMessageOverlay
import com.seerflix.tv.core.data.DetailsSeedCache
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
import com.seerflix.tv.feature.settings.SettingsScreen
import com.seerflix.tv.feature.servers.AddServerScreen
import com.seerflix.tv.feature.servers.ServerStatusOverlay
import com.seerflix.tv.feature.library.LibraryScreen
import com.seerflix.tv.core.diagnostics.DiagnosticsRepository
import com.seerflix.tv.ui.components.CrashReportPrompt
import com.seerflix.tv.ui.theme.SeerflixTheme
import com.seerflix.tv.ui.theme.SeerflixTokens
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var socketClient: JellyfinSocketClient

    // Injected so it is created up front and listens for group messages from the first connect.
    @Inject
    lateinit var syncPlayManager: SyncPlayManager

    @Inject
    lateinit var diagnostics: DiagnosticsRepository

    private var latestIntentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch carries a new deep link; after process death the restored back stack
        // already reflects it, and replaying it would open the link a second time.
        if (savedInstanceState == null) latestIntentState = intent
        setContent {
            val activeProfile by authRepository.activeProfileFlow.collectAsState(initial = null)
            val activePreset = activeProfile?.uiPreset ?: UiPreset.NETFLIX
            val activeLanguage = activeProfile?.uiLanguage ?: "en"
            SeerflixTheme(preset = activePreset, uiLanguage = activeLanguage) {
                // (Re)connect the live server connection whenever the signed-in profile changes.
                LaunchedEffect(activeProfile?.jellyfinToken) {
                    if (activeProfile?.jellyfinToken.isNullOrBlank()) socketClient.stop() else socketClient.start()
                }
                SeerflixNavGraph(
                    authRepository = authRepository,
                    socketClient = socketClient,
                    syncPlayManager = syncPlayManager,
                    diagnostics = diagnostics,
                    incomingIntent = latestIntentState,
                    onIntentHandled = { latestIntentState = null }
                )
            }
        }
    }

    // Connected only while Lumen is on screen: a TV app shouldn't hold a socket in the background.
    override fun onStart() {
        super.onStart()
        if (::socketClient.isInitialized) socketClient.start()
    }

    override fun onStop() {
        super.onStop()
        socketClient.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntentState = intent
    }
}

private const val NAV_FADE_IN_MS = 180
private const val NAV_FADE_OUT_MS = 120

private object Routes {
    const val SETUP = "setup"
    const val PROFILES = "profiles"
    const val HOME = "home"
    const val SEARCH = "search?query={query}&personId={personId}&personName={personName}"
    const val REQUESTS = "requests"
    const val DETAILS = "details/{tmdbId}/{jellyfinId}/{mediaType}"
    const val PLAYER = "player/{itemId}?mediaSourceId={mediaSourceId}&startMs={startMs}&syncplay={syncplay}"
    const val SETTINGS = "settings"
    /** Add another Jellyfin server; with a server id, sign in again to that one. */
    const val ADD_SERVER = "servers/add?serverId={serverId}"
    const val LIBRARY = "library?parentId={parentId}&title={title}&types={types}"
    const val TRAILER = "trailer?title={title}&youtubeKey={youtubeKey}&localTrailerId={localTrailerId}"

    fun search(query: String = "", personId: Int? = null, personName: String? = null): String {
        val encQ = Uri.encode(query)
        val pid = personId?.toString() ?: "0"
        val encName = Uri.encode(personName.orEmpty())
        return "search?query=$encQ&personId=$pid&personName=$encName"
    }

    fun details(item: UnifiedMediaItem): String {
        // Hand the clicked card to Details so it can render instantly while the full load runs.
        DetailsSeedCache.put(item)
        val tmdb = item.tmdbId ?: 0
        val jf = item.jellyfinId ?: "none"
        return "details/$tmdb/$jf/${item.mediaType.name}"
    }

    fun details(tmdbId: Int, mediaType: MediaType): String =
        "details/$tmdbId/none/${mediaType.name}"

    fun player(
        itemId: String,
        mediaSourceId: String? = null,
        startMs: Long? = null,
        fromSyncPlay: Boolean = false
    ): String {
        val encItem = Uri.encode(itemId)
        val encSrc = Uri.encode(mediaSourceId.orEmpty())
        return "player/$encItem?mediaSourceId=$encSrc&startMs=${startMs ?: 0L}&syncplay=$fromSyncPlay"
    }

    fun library(parentId: String?, title: String, types: String): String =
        "library?parentId=${Uri.encode(parentId.orEmpty())}&title=${Uri.encode(title)}&types=${Uri.encode(types)}"

    fun addServer(serverId: String? = null): String = "servers/add?serverId=${Uri.encode(serverId.orEmpty())}"

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
            // Decoded segments are re-encoded: "a%2Fb" would otherwise become an extra path level,
            // an unknown route, and a crash in navigate() (the activity is exported / BROWSABLE).
            val jf = segments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(Uri::encode) ?: "none"
            val type = segments.getOrNull(2)
                ?.let { t -> MediaType.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } }
                ?: MediaType.MOVIE
            "details/$tmdb/$jf/${type.name}"
        }
        "search" -> Routes.search(query = data.getQueryParameter("q").orEmpty())
        "home" -> Routes.HOME
        else -> null
    }
}

@Composable
private fun SeerflixNavGraph(
    authRepository: AuthRepository,
    socketClient: JellyfinSocketClient,
    syncPlayManager: SyncPlayManager,
    diagnostics: DiagnosticsRepository,
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

    // Deep links (Watch Next, voice search, seerflix:// URIs) are held until the user is past
    // setup and the profile gate, so a PIN-locked profile can't be bypassed and links that
    // arrive while the picker is showing aren't silently dropped.
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    LaunchedEffect(currentRoute, incomingIntent) {
        if (incomingIntent == null || currentRoute == null) return@LaunchedEffect
        if (currentRoute == Routes.SETUP || currentRoute == Routes.PROFILES) return@LaunchedEffect
        val route = resolveDeepLinkRoute(incomingIntent)
        onIntentHandled()
        if (route == Routes.HOME) {
            navController.popBackStack(Routes.HOME, inclusive = false)
        } else if (route != null) {
            // An unknown or malformed route must never crash the app from outside.
            runCatching { navController.navigate(route) }
        }
    }

    // Messages pushed by the server: "Play on" from another Jellyfin app, remote navigation, admin
    // messages, and SyncPlay switching the group to an item while no player is open here.
    var serverMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(navController) {
        socketClient.events.collect { event ->
            val route = navController.currentBackStackEntry?.destination?.route
            val signedIn = route != null && route != Routes.SETUP && route != Routes.PROFILES
            when (event) {
                is ServerEvent.Play -> {
                    if (!signedIn) return@collect
                    val itemId = event.itemIds.getOrNull(event.startIndex) ?: event.itemIds.firstOrNull() ?: return@collect
                    if (route == Routes.PLAYER) navController.popBackStack()
                    navController.navigate(
                        Routes.player(itemId, event.mediaSourceId, startMs = event.startPositionTicks / 10_000L)
                    )
                }
                is ServerEvent.GeneralCommand -> when (event.name) {
                    "DisplayMessage" -> {
                        val text = event.arguments["Text"].orEmpty()
                        if (text.isNotBlank()) serverMessage = event.arguments["Header"].orEmpty() to text
                    }
                    "DisplayContent" -> if (signedIn) {
                        val id = event.arguments["ItemId"] ?: return@collect
                        val type = when (event.arguments["ItemType"]) {
                            "Series", "Season", "Episode" -> MediaType.SERIES
                            else -> MediaType.MOVIE
                        }
                        navController.navigate("details/0/$id/${type.name}")
                    }
                    "GoHome" -> if (signedIn) navController.popBackStack(Routes.HOME, inclusive = false)
                    "Back" -> if (signedIn && route != Routes.HOME) navController.popBackStack()
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(navController) {
        syncPlayManager.actions.collect { action ->
            // With a player open, the player itself handles the group's item changes.
            if (action !is SyncPlayAction.LoadItem || syncPlayManager.localPlayingItemId != null) return@collect
            val route = navController.currentBackStackEntry?.destination?.route
            if (route == null || route == Routes.SETUP || route == Routes.PROFILES) return@collect
            navController.navigate(
                Routes.player(action.itemId, startMs = action.startPositionTicks / 10_000L, fromSyncPlay = true)
            )
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

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = startDestination!!,
        // Navigation's default is a 700 ms crossfade on every screen change, which made every
        // click feel laggy on a TV. Keep a short fade so screens still don't pop harshly.
        enterTransition = { fadeIn(animationSpec = tween(NAV_FADE_IN_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(NAV_FADE_OUT_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(NAV_FADE_IN_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(NAV_FADE_OUT_MS)) }
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSetupComplete = {
                    // Server settings may have changed: drop everything (incl. an old Home) and
                    // start fresh from the profile picker.
                    navController.navigate(Routes.PROFILES) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.PROFILES) {
            // If the active profile was deleted from the picker, Back must not return to a Home
            // that has no profile behind it; leave the app instead. (ProfilePickerScreen's own
            // BackHandler, e.g. for Manage mode, is registered later and still takes priority.)
            val activity = LocalContext.current as? ComponentActivity
            val currentProfile by authRepository.activeProfileFlow.collectAsState(initial = null)
            BackHandler(enabled = currentProfile == null) { activity?.finish() }
            ProfilePickerScreen(
                onProfileSelected = {
                    // Clear the whole stack so switching profiles never leaves the previous
                    // profile's Home (or a stale picker) underneath.
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETUP) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.HOME) {
            val strings = com.seerflix.tv.core.i18n.AppStrings.current
            HomeScreen(
                onOpenLibrary = { type ->
                    val movies = type == MediaType.MOVIE
                    navController.navigate(
                        Routes.library(
                            parentId = null,
                            title = if (movies) strings.libraryAllMovies else strings.libraryAllShows,
                            types = if (movies) "Movie" else "Series"
                        )
                    )
                },
                onOpenDetails = { item -> navController.navigate(Routes.details(item)) },
                onPlayDirect = { itemId -> navController.navigate(Routes.player(itemId)) },
                onOpenSearch = { navController.navigate(Routes.search()) { launchSingleTop = true } },
                onOpenRequests = { navController.navigate(Routes.REQUESTS) { launchSingleTop = true } },
                onSwitchProfile = { navController.navigate(Routes.PROFILES) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
            // Drawn over Home, outside its focus handling: offline note / sign-in-again prompt
            // for the extra servers.
            ServerStatusOverlay(
                onSignInAgain = { serverId -> navController.navigate(Routes.addServer(serverId)) { launchSingleTop = true } }
            )
        }

        composable(
            route = Routes.LIBRARY,
            arguments = listOf(
                navArgument("parentId") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("types") { type = NavType.StringType; defaultValue = "Movie,Series" }
            )
        ) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onOpenDetails = { item -> navController.navigate(Routes.details(item)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenServerSettings = { navController.navigate(Routes.SETUP) { launchSingleTop = true } },
                onAddServer = { navController.navigate(Routes.addServer()) { launchSingleTop = true } },
                onSignInToServer = { serverId -> navController.navigate(Routes.addServer(serverId)) { launchSingleTop = true } }
            )
        }

        composable(
            route = Routes.ADD_SERVER,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType; defaultValue = "" })
        ) {
            AddServerScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
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
                navArgument("mediaSourceId") { type = NavType.StringType; defaultValue = "" },
                navArgument("startMs") { type = NavType.StringType; defaultValue = "0" },
                navArgument("syncplay") { type = NavType.StringType; defaultValue = "false" }
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

    // After a crash: offer once (on Home) to store the report on the user's Jellyfin server.
    var crashReport by remember { mutableStateOf<String?>(null) }
    val crashScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.HOME && crashReport == null) {
            crashReport = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { diagnostics.pendingCrash() }
        }
    }
    crashReport?.let {
        CrashReportPrompt(
            onSend = {
                crashReport = null
                crashScope.launch {
                    val result = diagnostics.upload()
                    serverMessage = "" to when (result) {
                        is com.seerflix.tv.core.diagnostics.LogUploadResult.Uploaded -> "Report saved on your server as ${result.fileName}"
                        com.seerflix.tv.core.diagnostics.LogUploadResult.DisabledOnServer -> "Your server doesn't accept app logs (Dashboard > General > Allow client log upload)."
                        is com.seerflix.tv.core.diagnostics.LogUploadResult.Failed -> "Couldn't send the report: ${result.message}"
                    }
                }
            },
            onDismiss = {
                crashReport = null
                diagnostics.dismissCrash()
            }
        )
    }

    serverMessage?.let { (header, text) ->
        ServerMessageOverlay(
            header = header,
            text = text,
            onTimeout = { serverMessage = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    }
}
