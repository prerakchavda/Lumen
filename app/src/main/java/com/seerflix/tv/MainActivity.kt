package com.seerflix.tv

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.repeatOnLifecycle
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
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.player.PlaybackQueueManager
import com.seerflix.tv.core.player.QueueEntry
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
    lateinit var playbackQueueManager: PlaybackQueueManager

    @Inject
    lateinit var catalogRepository: UnifiedCatalogRepository

    @Inject
    lateinit var diagnostics: DiagnosticsRepository

    private var latestIntentState by mutableStateOf<Intent?>(null)
    /** When the user last pressed a key / interacted ([android.os.SystemClock.elapsedRealtime]). */
    @Volatile
    private var lastUserInputAtMs: Long = android.os.SystemClock.elapsedRealtime()
    private var screensaverVisible by mutableStateOf(false)
    /** Key whose remaining events (repeats + key-up) belong to the press that woke the screensaver. */
    private var swallowedWakeKeyCode: Int = android.view.KeyEvent.KEYCODE_UNKNOWN

    private fun markUserInput() {
        lastUserInputAtMs = android.os.SystemClock.elapsedRealtime()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        markUserInput()
        // The screensaver never takes focus (so the card underneath keeps it and Back keeps
        // working); the key that wakes it is consumed here, including its repeats and key-up.
        if (event.keyCode == swallowedWakeKeyCode) {
            if (event.action == android.view.KeyEvent.ACTION_UP) swallowedWakeKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
            if (event.action != android.view.KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
            swallowedWakeKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
        }
        if (screensaverVisible && event.action == android.view.KeyEvent.ACTION_DOWN) {
            screensaverVisible = false
            swallowedWakeKeyCode = event.keyCode
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        markUserInput()
    }

    override fun onResume() {
        super.onResume()
        // Time spent in the background (remote Home button) is not idle time on our screen.
        markUserInput()
        screensaverVisible = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A dialog window took or returned focus: its keys never reach dispatchKeyEvent here.
        markUserInput()
    }

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
                // (Re)connect the live server connection and sync user defaults whenever the signed-in profile changes.
                LaunchedEffect(activeProfile?.jellyfinToken) {
                    if (activeProfile?.jellyfinToken.isNullOrBlank()) {
                        socketClient.stop()
                    } else {
                        socketClient.start()
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { authRepository.syncUserConfigurationFromServer() }
                        }
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.seerflix.tv.ui.components.LocalScreensaverVisible provides screensaverVisible
                ) {
                SeerflixNavGraph(
                    authRepository = authRepository,
                    socketClient = socketClient,
                    syncPlayManager = syncPlayManager,
                    playbackQueueManager = playbackQueueManager,
                    catalogRepository = catalogRepository,
                    diagnostics = diagnostics,
                    activeProfile = activeProfile,
                    lastUserInputAtMs = { lastUserInputAtMs },
                    hasWindowFocus = { hasWindowFocus() },
                    screensaverVisible = { screensaverVisible },
                    setScreensaverVisible = { visible ->
                        markUserInput()
                        screensaverVisible = visible
                    },
                    incomingIntent = latestIntentState,
                    onIntentHandled = { latestIntentState = null }
                )
                }
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
    const val SEARCH = "search?query={query}&personId={personId}&jfPersonId={jfPersonId}&personName={personName}"
    const val REQUESTS = "requests"
    const val DETAILS = "details/{tmdbId}/{jellyfinId}/{mediaType}"
    const val PLAYER = "player/{itemId}?mediaSourceId={mediaSourceId}&startMs={startMs}&fromStart={fromStart}&audioIndex={audioIndex}&subtitleIndex={subtitleIndex}&syncplay={syncplay}"
    const val SETTINGS = "settings"
    /** Add another Jellyfin server; with a server id, sign in again to that one. */
    const val ADD_SERVER = "servers/add?serverId={serverId}"
    const val LIBRARY = "library?parentId={parentId}&title={title}&types={types}&collectionType={collectionType}&folderMode={folderMode}"
    const val TRAILER = "trailer?title={title}&youtubeKey={youtubeKey}&localTrailerId={localTrailerId}"

    fun search(
        query: String = "",
        personId: Int? = null,
        jfPersonId: String? = null,
        personName: String? = null
    ): String {
        val encQ = Uri.encode(query)
        val pid = personId?.toString() ?: "0"
        val encJfPid = Uri.encode(jfPersonId.orEmpty())
        val encName = Uri.encode(personName.orEmpty())
        return "search?query=$encQ&personId=$pid&jfPersonId=$encJfPid&personName=$encName"
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
        fromStart: Boolean = false,
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
        fromSyncPlay: Boolean = false
    ): String {
        val encItem = Uri.encode(itemId)
        val encSrc = Uri.encode(mediaSourceId.orEmpty())
        val aIdx = audioIndex?.toString() ?: "-1"
        val sIdx = subtitleIndex?.toString() ?: "-1"
        return "player/$encItem?mediaSourceId=$encSrc&startMs=${startMs ?: 0L}&fromStart=$fromStart&audioIndex=$aIdx&subtitleIndex=$sIdx&syncplay=$fromSyncPlay"
    }

    fun library(
        parentId: String?,
        title: String,
        types: String,
        collectionType: String? = null,
        folderMode: Boolean = false
    ): String =
        "library?parentId=${Uri.encode(parentId.orEmpty())}&title=${Uri.encode(title)}&types=${Uri.encode(types)}&collectionType=${Uri.encode(collectionType.orEmpty())}&folderMode=$folderMode"

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
    playbackQueueManager: PlaybackQueueManager,
    catalogRepository: UnifiedCatalogRepository,
    diagnostics: DiagnosticsRepository,
    activeProfile: com.seerflix.tv.core.model.UserProfile?,
    lastUserInputAtMs: () -> Long,
    hasWindowFocus: () -> Boolean,
    screensaverVisible: () -> Boolean,
    setScreensaverVisible: (Boolean) -> Unit,
    incomingIntent: Intent?,
    onIntentHandled: () -> Unit
) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    val launchQueue: (List<QueueEntry>, Boolean) -> Unit = { entries, shuffle ->
        val first = playbackQueueManager.setQueue(entries, startIndex = 0, shuffle = shuffle)
        if (first != null) {
            navController.navigate(
                Routes.player(
                    itemId = first.itemId,
                    mediaSourceId = first.mediaSourceId,
                    startMs = first.startPositionMs.takeIf { it > 0L }
                )
            )
        }
    }

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
            // A new title from Watch Next while a player is open replaces that player: left under
            // the new one, Back would restart the previous title.
            if (currentRoute == Routes.PLAYER && route.startsWith("player/")) navController.popBackStack()
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
                    if (!signedIn || event.itemIds.isEmpty()) return@collect
                    val startMs = event.startPositionTicks / 10_000L
                    val entries = event.itemIds.mapIndexed { idx, id ->
                        QueueEntry(
                            itemId = id,
                            mediaSourceId = if (idx == event.startIndex) event.mediaSourceId else null,
                            startPositionMs = if (idx == event.startIndex) startMs else 0L
                        )
                    }
                    // PlayNext / PlayLast only add to what is playing. With no player open, a queue
                    // left over from an earlier playlist must not be resumed: they act as PlayNow.
                    val playerOpen = route == Routes.PLAYER
                    when {
                        playerOpen && event.playCommand == "PlayNext" -> {
                            playbackQueueManager.enqueueNext(entries, current = playbackQueueManager.nowPlaying)
                        }
                        playerOpen && event.playCommand == "PlayLast" -> {
                            playbackQueueManager.enqueueLast(entries, current = playbackQueueManager.nowPlaying)
                        }
                        else -> {
                            val first = if (entries.size > 1) {
                                playbackQueueManager.setQueue(entries, startIndex = event.startIndex, shuffle = false)
                            } else {
                                playbackQueueManager.clear()
                                entries.first()
                            } ?: return@collect
                            if (route == Routes.PLAYER) navController.popBackStack()
                            runCatching {
                                navController.navigate(
                                    Routes.player(first.itemId, first.mediaSourceId, startMs = first.startPositionMs)
                                )
                            }
                        }
                    }
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
                        // The id comes from another client: encode it, and never let a bad one
                        // crash the app (a '/' or '?' makes navigate() throw).
                        runCatching { navController.navigate("details/0/${Uri.encode(id)}/${type.name}") }
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
            // A player that is still loading hasn't set localPlayingItemId yet, but handles it.
            if (route == Routes.PLAYER) return@collect
            runCatching {
                navController.navigate(
                    Routes.player(action.itemId, startMs = action.startPositionTicks / 10_000L, fromSyncPlay = true)
                )
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
                            types = if (movies) "Movie" else "Series",
                            collectionType = if (movies) "movies" else "tvshows"
                        )
                    )
                },
                onOpenLibraryView = { view ->
                    navController.navigate(
                        Routes.library(
                            parentId = view.id,
                            title = view.displayName,
                            types = UnifiedCatalogRepository.includeTypesForCollectionType(view.collectionType, folderMode = false),
                            collectionType = view.collectionType
                        )
                    )
                },
                onPlayQueue = launchQueue,
                onOpenDetails = { item -> navController.navigate(Routes.details(item)) },
                onPlayDirect = { itemId ->
                    playbackQueueManager.clear()
                    navController.navigate(Routes.player(itemId))
                },
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
                navArgument("types") { type = NavType.StringType; defaultValue = "Movie,Series" },
                navArgument("collectionType") { type = NavType.StringType; defaultValue = "" },
                navArgument("folderMode") { type = NavType.BoolType; defaultValue = false }
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
                navArgument("jfPersonId") { type = NavType.StringType; defaultValue = "" },
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
                    playbackQueueManager.clear()
                    navController.navigate(Routes.player(itemId, mediaSourceId))
                },
                onPlayWithOptions = { itemId, mediaSourceId, fromStart, audioIndex, subtitleIndex ->
                    playbackQueueManager.clear()
                    navController.navigate(
                        Routes.player(
                            itemId = itemId,
                            mediaSourceId = mediaSourceId,
                            startMs = if (fromStart) 0L else null,
                            fromStart = fromStart,
                            audioIndex = audioIndex,
                            subtitleIndex = subtitleIndex
                        )
                    )
                },
                onPlayQueue = launchQueue,
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
                },
                onSearchPerson = { name, personId, jfPersonId ->
                    navController.navigate(
                        Routes.search(
                            query = if (personId == null && jfPersonId.isNullOrBlank()) name else "",
                            personId = personId,
                            jfPersonId = jfPersonId,
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
                navArgument("fromStart") { type = NavType.StringType; defaultValue = "false" },
                navArgument("audioIndex") { type = NavType.StringType; defaultValue = "-1" },
                navArgument("subtitleIndex") { type = NavType.StringType; defaultValue = "-1" },
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
    // Once per session: a failed upload (offline, logging disabled on the server) or one still
    // running used to bring the prompt back on every return to Home.
    var crashPrompted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val crashScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.HOME && crashReport == null && !crashPrompted) {
            crashReport = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { diagnostics.pendingCrash() }
            if (crashReport != null) crashPrompted = true
        }
    }
    crashReport?.let {
        val crashStrings = com.seerflix.tv.core.i18n.AppStrings.current
        CrashReportPrompt(
            onSend = {
                crashReport = null
                crashScope.launch {
                    val result = diagnostics.upload()
                    serverMessage = "" to when (result) {
                        is com.seerflix.tv.core.diagnostics.LogUploadResult.Uploaded -> crashStrings.settingsLogsSent.format(result.fileName)
                        com.seerflix.tv.core.diagnostics.LogUploadResult.DisabledOnServer -> {
                            // The server won't take it; asking again next launch can't help.
                            diagnostics.dismissCrash()
                            crashStrings.settingsLogsDisabled
                        }
                        is com.seerflix.tv.core.diagnostics.LogUploadResult.Failed -> crashStrings.settingsLogsFailed.format(result.message)
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

    // OLED Backdrop / Clock Screensaver on browse & details screens (PlayerScreen handles paused playback itself).
    val showScreensaver = screensaverVisible()
    var screensaverSlides by remember(activeProfile?.id) {
        mutableStateOf<List<com.seerflix.tv.core.model.ScreensaverSlide>>(emptyList())
    }
    val screensaverEligible = currentRoute != null &&
        currentRoute != Routes.SETUP &&
        currentRoute != Routes.PROFILES &&
        currentRoute != Routes.PLAYER &&
        currentRoute != Routes.TRAILER
    val screensaverTimeoutMin = activeProfile?.screensaverTimeoutMinutes ?: 0
    val screensaverLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(screensaverEligible, screensaverTimeoutMin, activeProfile?.id, showScreensaver) {
        if (!screensaverEligible || screensaverTimeoutMin <= 0 || activeProfile == null) {
            if (showScreensaver) setScreensaverVisible(false)
            return@LaunchedEffect
        }
        if (showScreensaver) return@LaunchedEffect
        val timeoutMs = screensaverTimeoutMin * 60_000L
        // Only counts while Lumen is on screen; idle time is measured from the last key press.
        screensaverLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                val idleMs = android.os.SystemClock.elapsedRealtime() - lastUserInputAtMs()
                if (idleMs < timeoutMs) {
                    kotlinx.coroutines.delay(timeoutMs - idleMs)
                    continue
                }
                // A dialog has the focus (its keys don't reach the activity): not idle.
                if (!hasWindowFocus()) {
                    kotlinx.coroutines.delay(5_000L)
                    continue
                }
                if (screensaverSlides.isEmpty()) {
                    screensaverSlides = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            catalogRepository.loadScreensaverBackdrops(activeProfile.jellyfinUserId, activeProfile)
                        }.getOrDefault(emptyList())
                    }
                }
                setScreensaverVisible(true)
                break
            }
        }
    }
    if (showScreensaver && screensaverEligible) {
        com.seerflix.tv.ui.components.ScreensaverOverlay(
            slides = screensaverSlides,
            onDismiss = { setScreensaverVisible(false) }
        )
    }
    }
}
