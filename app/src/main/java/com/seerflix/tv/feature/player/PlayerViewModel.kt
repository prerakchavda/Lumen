package com.seerflix.tv.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
import com.seerflix.tv.core.model.AssSubtitleMode
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.ExternalSubtitleFile
import com.seerflix.tv.core.model.FrameRateMatchMode
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.PlayMethod
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.QualityPreset
import com.seerflix.tv.core.model.RemoteSubtitleOption
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.SleepTimerOption
import com.seerflix.tv.core.model.SubtitleSelectionMode
import com.seerflix.tv.core.model.SyncPlayGroup
import com.seerflix.tv.core.model.VideoResizeMode
import com.seerflix.tv.core.network.MediaClient
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.AssCue
import com.seerflix.tv.core.player.AssSubtitleParser
import com.seerflix.tv.core.player.AudioDelayProcessor
import com.seerflix.tv.core.player.ExternalPlayerHelper
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.player.NextEpisodeInfo
import com.seerflix.tv.core.player.PlaybackQueueManager
import com.seerflix.tv.core.player.PlaybackReportState
import com.seerflix.tv.core.player.PlaybackSegments
import com.seerflix.tv.core.player.QueueEntry
import com.seerflix.tv.core.player.SubtitleTrackSelector
import com.seerflix.tv.core.player.SyncPlayAction
import com.seerflix.tv.core.player.SyncPlayManager
import com.seerflix.tv.core.network.JellyfinSocketClient
import com.seerflix.tv.core.network.ServerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import com.seerflix.tv.core.player.YouTubeTrailerResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class LiveStreamStats(
    val playMethod: PlayMethod,
    val transcodeReasons: List<String> = emptyList(),
    val videoCodec: String? = null,
    val videoResolution: String? = null,
    val videoFps: Float? = null,
    val videoBitrateBps: Long? = null,
    val hdrFormat: String? = null,
    val audioCodec: String? = null,
    val audioBitrateBps: Long? = null,
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val bufferedDurationMs: Long = 0L,
    val droppedFrames: Int = 0
)

/** Route / preselection values for the player's track arguments. */
object PlayerTrackSelection {
    /** No explicit choice: the profile's defaults pick the track. */
    const val AUTO = -1
    /** Subtitles explicitly turned off (e.g. "Off" in the pre-playback picker). */
    const val SUBTITLE_OFF = -2
}

data class PlayerUiState(
    val session: PlaybackSessionData? = null,
    val selectedSpeed: Float = 1.0f,
    val selectedQuality: QualityPreset = QualityPreset.AUTO,
    val selectedMediaSourceId: String? = null,
    val selectedAudioStreamIndex: Int? = null,
    val selectedSubtitleStreamIndex: Int? = null,
    /**
     * Subtitle to apply once when the new play session's tracks are known: a Jellyfin stream index,
     * [PlayerTrackSelection.SUBTITLE_OFF], or null for the profile's automatic selection.
     */
    val preselectedSubtitleStreamIndex: Int? = null,
    /** Jellyfin audio stream index explicitly asked for (picker / audio switch); applied to Direct Play once. */
    val requestedAudioStreamIndex: Int? = null,
    val subtitleFontScale: Float = 1.0f,
    val subtitleBackgroundAlpha: Float = 0.6f,
    val subtitleTextColor: com.seerflix.tv.core.model.SubtitleTextColor = com.seerflix.tv.core.model.SubtitleTextColor.WHITE,
    val subtitleEdgeStyle: com.seerflix.tv.core.model.SubtitleEdgeStyle = com.seerflix.tv.core.model.SubtitleEdgeStyle.OUTLINE,
    val subtitlePosition: com.seerflix.tv.core.model.SubtitlePosition = com.seerflix.tv.core.model.SubtitlePosition.BOTTOM,
    val subtitleOffsetMs: Long = 0L,
    val audioOffsetMs: Long = 0L,
    val videoResizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val subtitleSelectionMode: SubtitleSelectionMode = SubtitleSelectionMode.DEFAULT,
    val autoSkipIntro: Boolean = false,
    val autoSkipRecap: Boolean = false,
    val frameRateMatchMode: FrameRateMatchMode = FrameRateMatchMode.OFF,
    val audioPassthroughEnabled: Boolean = true,
    val stereoDownmixEnabled: Boolean = false,
    val assSubtitleMode: AssSubtitleMode = AssSubtitleMode.STYLED,
    val burnInSubtitleIndex: Int? = null,
    val activeAssCues: List<AssCue> = emptyList(),
    val activeAssSubtitleIndex: Int? = null,
    val showStreamInfo: Boolean = false,
    val autoplayNextEpisode: Boolean = true,
    val autoplayCancelled: Boolean = false,
    val bingeLimitEpisodes: Int = 0,
    val showStillWatchingPrompt: Boolean = false,
    val pendingStillWatchingNextId: String? = null,
    val pendingStillWatchingNextTitle: String? = null,
    val sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    val sleepTimerEndMs: Long? = null,
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val remoteSubtitles: List<RemoteSubtitleOption> = emptyList(),
    val remoteSubtitlesLoading: Boolean = false,
    val remoteSubtitleLanguage: String = "eng",
    val downloadingSubtitleId: String? = null,
    val subtitleSearchMessage: String? = null,
    val drawerSeasons: List<SeasonSummary> = emptyList(),
    val drawerSelectedSeason: Int = 1,
    val drawerEpisodes: List<EpisodeSummary> = emptyList(),
    val drawerEpisodesLoading: Boolean = false,
    val syncPlayGroup: SyncPlayGroup? = null,
    val hasQueueNext: Boolean = false,
    val hasQueuePrevious: Boolean = false,
    val queuePositionLabel: String? = null,
    val queueNextTitle: String? = null,
    val liveTvChannels: List<com.seerflix.tv.core.model.LiveTvChannel> = emptyList(),
    val screensaverTimeoutMinutes: Int = 0,
    val screensaverSlides: List<com.seerflix.tv.core.model.ScreensaverSlide> = emptyList(),
    val cinemaIntroIndex: Int = 0,
    val cinemaIntroTotal: Int = 0,
    val feedbackMessage: String? = null,
    /** Source frame rate from Jellyfin metadata, so the display mode can be switched before the first frame. */
    val videoFrameRateHint: Float? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val playbackManager: JellyfinPlaybackManager,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver,
    private val syncPlayManager: SyncPlayManager,
    private val playbackQueueManager: PlaybackQueueManager,
    private val prefs: SessionPreferences,
    socketClient: JellyfinSocketClient,
    // Honours the per-server self-signed certificate toggle; also handed to ExoPlayer so
    // streams and side-loaded subtitles work against homelab HTTPS servers.
    @MediaClient val mediaHttpClient: OkHttpClient
) : ViewModel() {

    val softwareAudioDecodingEnabled: Boolean
        get() = prefs.cachedSoftwareAudioDecoding

    private val initialItemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val initialMediaSourceId: String? = savedStateHandle.get<String>("mediaSourceId")?.takeIf { it.isNotBlank() }
    private val initialAudioStreamIndex: Int? = savedStateHandle.get<String>("audioIndex")?.toIntOrNull()?.takeIf { it >= 0 }
    /** A Jellyfin subtitle index, [PlayerTrackSelection.SUBTITLE_OFF], or null (automatic). */
    private val initialSubtitleSelection: Int? = savedStateHandle.get<String>("subtitleIndex")?.toIntOrNull()
        ?.takeIf { it >= 0 || it == PlayerTrackSelection.SUBTITLE_OFF }
    private val trailerYoutubeKey: String? = savedStateHandle.get<String>("youtubeKey")?.takeIf { it.isNotBlank() && it != "none" }
    private val localTrailerId: String? = savedStateHandle.get<String>("localTrailerId")?.takeIf { it.isNotBlank() && it != "none" }
    private val trailerTitle: String? = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }
    /** Start position handed over by "Play on" from another app or by a SyncPlay group. */
    private val initialStartMs: Long? = savedStateHandle.get<String>("startMs")?.toLongOrNull()?.takeIf { it >= 0L }
    private val startFromBeginning: Boolean = savedStateHandle.get<String>("fromStart") == "true"
    private val openedBySyncPlay: Boolean = savedStateHandle.get<String>("syncplay") == "true"

    /** Group commands for the player (only while in a SyncPlay group). */
    val syncPlayActions: SharedFlow<SyncPlayAction> = syncPlayManager.actions
    val syncPlayNotices: SharedFlow<String> = syncPlayManager.notices

    /** Remote-control commands from other Jellyfin apps that act on the player. */
    val remotePlaystate: Flow<ServerEvent.Playstate> = socketClient.events.filterIsInstance<ServerEvent.Playstate>()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** UI strings for the active profile's language, read when a message is produced. */
    private val strings: UiStrings
        get() = stringsForLanguage(prefs.cachedUiLanguage)

    private var activeUserId: String = ""
    private var activeSeriesTmdbId: Int? = null
    private var consecutiveAutoplayedEpisodes: Int = 0

    /** The in-flight item/trailer load; a newer load cancels it so a stale result can't win. */
    private var loadJob: Job? = null
    /** The item the last loadItem() asked for (differs from session.itemId while it loads or after it failed). */
    private var requestedItemId: String? = null
    /** True when onPlaybackError pinned a transcode quality; the next item starts at AUTO again. */
    private var qualityPinnedByError = false
    private var assLoadJob: Job? = null

    /** Episodes-drawer data is not needed to start playback; it loads a few seconds later or on open. */
    private var drawerJob: Job? = null
    private var pendingDrawerRequest: Pair<String, String?>? = null
    private val cinemaIntroQueue = com.seerflix.tv.core.player.CinemaIntroQueue()
    /** Season loads of the episodes drawer; a newer pick cancels the previous request. */
    private var drawerSeasonJob: Job? = null
    /** Remote subtitle search / download for the current item; cancelled when another item loads. */
    private var remoteSubtitleSearchJob: Job? = null
    private var remoteSubtitleDownloadJob: Job? = null
    /** Timed clear of [PlayerUiState.feedbackMessage]. */
    private var feedbackJob: Job? = null
    /** Play session already reported as stopped: no further progress / stop reports for it. */
    private var stoppedPlaySessionId: String? = null

    /**
     * Where playback was when the player screen left composition while this ViewModel stayed in
     * the back stack (a remote "show details" command or a deep link pushed a screen on top).
     */
    private var releasedAtPositionMs: Long? = null
    private var releasedPlaySessionId: String? = null

    fun onPlayerReleased(positionMs: Long) {
        releasedAtPositionMs = positionMs
        releasedPlaySessionId = _uiState.value.session?.playSessionId
    }

    /**
     * Called when the player screen sets up a session. If the screen is coming back after its
     * player was released, the old play session has already been reported stopped and replaying
     * it would seek to its original start and never report progress again. Start a fresh play
     * session at the position we left instead; returns true when that reload was started.
     */
    fun reloadAfterScreenReturn(): Boolean {
        val positionMs = releasedAtPositionMs ?: return false
        val releasedSession = releasedPlaySessionId
        releasedAtPositionMs = null
        releasedPlaySessionId = null
        val session = _uiState.value.session ?: return false
        // Only the session that was playing when the screen left needs a fresh start.
        if (session.isTrailer || session.playSessionId != releasedSession) return false
        loadItem(
            session.itemId,
            resumeOverrideMs = if (session.isLiveTv) null else positionMs,
            isCinemaIntroClip = session.isCinemaIntro
        )
        return true
    }
    /** Play session handed to an external player (reporting restarts if playback resumes here). */
    private var externalHandoffPlaySessionId: String? = null
    /** Whether the last loadItem() asked to skip Cinema Intros / was an intro clip (for Retry). */
    private var requestedSkipCinemaIntros = false
    private var requestedIsCinemaIntroClip = false
    /** Queue entry held back by the "Still watching?" prompt (replayed with its version / start). */
    private var pendingStillWatchingEntry: QueueEntry? = null
    /** "CH n · Name" banner shown once the tuned channel has loaded. */
    private var pendingChannelBanner: Pair<String, String>? = null
    /** Whether the current play session's subtitle pick has been applied to its tracks. */
    private var subtitlePickApplied = false
    /** The "now playing" entry this ViewModel published (a newer player may have replaced it). */
    private var ownNowPlaying: QueueEntry? = null

    init {
        viewModelScope.launch {
            syncPlayManager.activeGroup.collect { group ->
                _uiState.update { it.copy(syncPlayGroup = group) }
            }
        }
        // A trailer player has nothing to do with the app-wide play queue (an earlier Play All /
        // Shuffle may still be in it): no queue Next / Previous on trailers.
        val isTrailerPlayer = !trailerYoutubeKey.isNullOrBlank() || !localTrailerId.isNullOrBlank()
        if (!isTrailerPlayer) viewModelScope.launch {
            playbackQueueManager.state.collect { q ->
                _uiState.update {
                    it.copy(
                        hasQueueNext = q.hasNext,
                        hasQueuePrevious = q.hasPrevious,
                        queuePositionLabel = q.positionLabel,
                        queueNextTitle = q.nextEntry?.title
                    )
                }
            }
        }
        if (!trailerYoutubeKey.isNullOrBlank() || !localTrailerId.isNullOrBlank()) {
            loadTrailer(trailerTitle ?: strings.actionTrailer, localTrailerId, trailerYoutubeKey)
        } else {
            val effectiveStartMs = when {
                startFromBeginning -> 0L
                initialStartMs != null && initialStartMs > 0L -> initialStartMs
                else -> null
            }
            loadItem(
                initialItemId,
                mediaSourceIdOverride = initialMediaSourceId,
                audioStreamIndexOverride = initialAudioStreamIndex,
                resumeOverrideMs = effectiveStartMs,
                fromSyncPlay = openedBySyncPlay
            )
        }
    }

    fun loadTrailer(title: String, localTrailerId: String?, youtubeKey: String?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    subtitleOffsetMs = 0L,
                    activeAssCues = emptyList(),
                    videoFrameRateHint = null
                )
            }
            val resolved = trailerResolver.resolvePreview(
                localTrailerId = localTrailerId,
                youtubeKey = youtubeKey,
                timeoutMs = TRAILER_RESOLVE_TIMEOUT_MS
            )
            if (resolved == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = strings.playerTrailerResolveFailed)
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    session = PlaybackSessionData(
                        itemId = "trailer_${youtubeKey ?: localTrailerId ?: "clip"}",
                        title = title,
                        subtitle = strings.actionTrailer,
                        streamUrl = resolved.videoUrl,
                        isHls = false,
                        mediaSourceId = "trailer",
                        playSessionId = "trailer_${System.currentTimeMillis()}",
                        resumeTicks = 0L,
                        runtimeTicks = 0L,
                        isTrailer = true,
                        trailerAudioUrl = resolved.audioUrl
                    ),
                    isLoading = false
                )
            }
        }
    }

    fun loadItem(
        itemId: String,
        qualityOverride: QualityPreset = _uiState.value.selectedQuality,
        resumeOverrideMs: Long? = null,
        // KEEP_* = "carry the current choice over" — only ever for the SAME item. A different item
        // (next episode, drawer, remote, SyncPlay) starts from its own default version and no
        // burned-in subtitle: the previous episode's MediaSourceId / subtitle index don't exist on it.
        mediaSourceIdOverride: String? = KEEP_SOURCE,
        burnInSubtitleOverride: Int? = KEEP_BURN_IN,
        audioStreamIndexOverride: Int? = KEEP_AUDIO_INDEX,
        forceHls: Boolean = false,
        /** True when the group asked for this item; otherwise a new item becomes the group's queue. */
        fromSyncPlay: Boolean = false,
        /** True when triggered automatically by Up Next countdown / end-of-episode autoplay. */
        isAutoplayAdvance: Boolean = false,
        nextTitleForPrompt: String? = null,
        skipCinemaIntros: Boolean = false,
        isCinemaIntroClip: Boolean = false,
        /**
         * Subtitle to select once the item plays: a Jellyfin stream index,
         * [PlayerTrackSelection.SUBTITLE_OFF] or null (profile defaults). KEEP_SUBTITLE carries the
         * current selection over on a same-item reload (quality / audio / version switch, …).
         */
        subtitleSelection: Int? = KEEP_SUBTITLE,
        /** After a remote subtitle download: the subtitle indexes that existed before it. */
        preselectNewSubtitleExcluding: Set<Int>? = null,
        /** The queue entry being advanced to, kept if the "Still watching?" prompt holds it back. */
        pendingQueueEntry: QueueEntry? = null
    ) {
        if (itemId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = strings.playerInvalidItem) }
            return
        }
        // A quality / audio / subtitle change while a cinema intro plays reloads the same clip:
        // it must stay an intro (not reported, skippable, and followed by the main feature).
        @Suppress("NAME_SHADOWING")
        val isCinemaIntroClip = isCinemaIntroClip ||
            _uiState.value.session?.let { it.itemId == itemId && it.isCinemaIntro } == true
        if (!isCinemaIntroClip && !skipCinemaIntros && _uiState.value.session?.itemId != itemId) {
            cinemaIntroQueue.clear()
        }
        val isSameItem = _uiState.value.session?.itemId == itemId
        if (!isSameItem) {
            if (isAutoplayAdvance) {
                consecutiveAutoplayedEpisodes++
                val limit = _uiState.value.bingeLimitEpisodes
                if (limit > 0 && consecutiveAutoplayedEpisodes >= limit) {
                    pendingStillWatchingEntry = pendingQueueEntry?.takeIf { it.itemId == itemId }
                    _uiState.update {
                        it.copy(
                            showStillWatchingPrompt = true,
                            pendingStillWatchingNextId = itemId,
                            pendingStillWatchingNextTitle = nextTitleForPrompt ?: it.session?.nextEpisodeTitle
                        )
                    }
                    return
                }
            } else {
                consecutiveAutoplayedEpisodes = 0
            }
        }
        requestedItemId = itemId
        requestedSkipCinemaIntros = skipCinemaIntros
        requestedIsCinemaIntroClip = isCinemaIntroClip
        pendingStillWatchingEntry = null
        if (!isSameItem) {
            assLoadJob?.cancel()
            // A late search result / download must not act on the next item.
            remoteSubtitleSearchJob?.cancel()
            remoteSubtitleDownloadJob?.cancel()
            if (!isCinemaIntroClip) playbackQueueManager.syncToItem(itemId)
        }
        // Captured now: once the old stream is stopped its (empty) tracks no longer say what was on.
        val stateAtRequest = _uiState.value
        val keepAssOverlay = isSameItem && subtitleSelection == KEEP_SUBTITLE && preselectNewSubtitleExcluding == null &&
            stateAtRequest.activeAssSubtitleIndex != null && stateAtRequest.burnInSubtitleIndex == null
        val subtitleChoice: Int? = when {
            subtitleSelection != KEEP_SUBTITLE -> subtitleSelection
            isCinemaIntroClip -> null
            // The styled overlay stays; ExoPlayer's own text renderer stays off underneath it.
            keepAssOverlay -> PlayerTrackSelection.SUBTITLE_OFF
            isSameItem -> when {
                stateAtRequest.burnInSubtitleIndex != null -> null
                // Reloaded before the first pick was applied (e.g. a Direct Play error before any
                // tracks were reported): keep the pending pick / automatic choice, not "off".
                !subtitlePickApplied -> stateAtRequest.preselectedSubtitleStreamIndex
                else -> stateAtRequest.selectedSubtitleStreamIndex ?: PlayerTrackSelection.SUBTITLE_OFF
            }
            itemId == initialItemId -> initialSubtitleSelection
            else -> null
        }
        // A quality forced by a playback error applies to that item only.
        val quality = if (!isSameItem && qualityPinnedByError && qualityOverride == _uiState.value.selectedQuality) {
            qualityPinnedByError = false
            QualityPreset.AUTO
        } else qualityOverride
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    feedbackMessage = if (isSameItem) it.feedbackMessage else null,
                    autoplayCancelled = false,
                    showStillWatchingPrompt = false,
                    pendingStillWatchingNextId = null,
                    pendingStillWatchingNextTitle = null,
                    subtitleOffsetMs = if (isSameItem) it.subtitleOffsetMs else 0L,
                    audioOffsetMs = if (isSameItem) it.audioOffsetMs else 0L,
                    remoteSubtitles = if (isSameItem) it.remoteSubtitles else emptyList(),
                    remoteSubtitlesLoading = if (isSameItem) it.remoteSubtitlesLoading else false,
                    downloadingSubtitleId = if (isSameItem) it.downloadingSubtitleId else null,
                    subtitleSearchMessage = if (isSameItem) it.subtitleSearchMessage else null,
                    activeAssCues = if (keepAssOverlay) it.activeAssCues else emptyList(),
                    activeAssSubtitleIndex = if (keepAssOverlay) it.activeAssSubtitleIndex else null,
                    cinemaIntroIndex = if (isCinemaIntroClip) it.cinemaIntroIndex else 0,
                    cinemaIntroTotal = if (isCinemaIntroClip) it.cinemaIntroTotal else 0
                )
            }
            if (!keepAssOverlay) assLoadJob?.cancel()
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null || profile.jellyfinUserId.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = strings.playerNoProfile) }
                return@launch
            }
            activeUserId = profile.jellyfinUserId
            // Restricted profiles: check the rating before anything plays, whatever opened the player.
            if (!isCinemaIntroClip && !catalogRepository.isPlayableForProfile(itemId, profile)) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = strings.detailsBlockedForProfile)
                }
                return@launch
            }
            val keptSource = _uiState.value.selectedMediaSourceId
            val resolvedSourceId = when {
                mediaSourceIdOverride !== KEEP_SOURCE -> mediaSourceIdOverride
                isSameItem -> keptSource
                else -> initialMediaSourceId?.takeIf { itemId == initialItemId }
            }
            val resolvedBurnIn = when {
                burnInSubtitleOverride != KEEP_BURN_IN -> burnInSubtitleOverride
                isSameItem -> _uiState.value.burnInSubtitleIndex
                else -> null
            }
            val resolvedAudioIndex = when {
                audioStreamIndexOverride != KEEP_AUDIO_INDEX -> audioStreamIndexOverride
                isSameItem -> _uiState.value.selectedAudioStreamIndex
                else -> initialAudioStreamIndex?.takeIf { itemId == initialItemId }
            }
            // Only the picker's choice for the item the player was opened with goes to PlaybackInfo.
            val resolvedInitialSubIndex = if (!isSameItem && itemId == initialItemId) {
                initialSubtitleSelection?.takeIf { it >= 0 }
            } else null
            _uiState.update {
                it.copy(
                    autoplayNextEpisode = profile.autoplayNextEpisode,
                    bingeLimitEpisodes = profile.bingeLimitEpisodes,
                    subtitleSelectionMode = profile.subtitleSelectionMode,
                    subtitleFontScale = profile.subtitleFontScale,
                    subtitleBackgroundAlpha = profile.subtitleBackgroundAlpha,
                    subtitleTextColor = profile.subtitleTextColor,
                    subtitleEdgeStyle = profile.subtitleEdgeStyle,
                    subtitlePosition = profile.subtitlePosition,
                    autoSkipIntro = profile.autoSkipIntro,
                    autoSkipRecap = profile.autoSkipRecap,
                    frameRateMatchMode = profile.frameRateMatchMode,
                    audioPassthroughEnabled = profile.audioPassthroughEnabled,
                    stereoDownmixEnabled = profile.stereoDownmixEnabled,
                    assSubtitleMode = profile.assSubtitleMode,
                    // A same-item reload keeps what this session already chose (e.g. a downloaded
                    // subtitle's language); a different item starts from the profile again.
                    preferredAudioLang = if (isSameItem) it.preferredAudioLang else profile.preferredAudioLang,
                    preferredSubtitleLang = if (isSameItem) it.preferredSubtitleLang else profile.preferredSubtitleLang,
                    screensaverTimeoutMinutes = profile.screensaverTimeoutMinutes
                )
            }
            if (profile.screensaverTimeoutMinutes > 0 && _uiState.value.screensaverSlides.isEmpty()) {
                launch(Dispatchers.IO) {
                    val slides = catalogRepository.loadScreensaverBackdrops(activeUserId, profile)
                    if (slides.isNotEmpty()) _uiState.update { it.copy(screensaverSlides = slides) }
                }
            }

            // Returns as soon as PlaybackInfo is known; segments (skip intro/recap, credits) and the
            // next episode are fetched in this job and merged in when they arrive. Until then the
            // skip / up-next UI simply doesn't trigger.
            playbackManager.preparePlaybackWithExtras(
                extrasScope = this,
                userId = activeUserId,
                itemId = itemId,
                maxBitrateBps = quality.maxBitrateBps,
                mediaSourceId = resolvedSourceId,
                audioPassthroughEnabled = profile.audioPassthroughEnabled,
                stereoDownmixEnabled = profile.stereoDownmixEnabled,
                assSubtitleMode = profile.assSubtitleMode,
                burnInSubtitleIndex = resolvedBurnIn,
                audioStreamIndex = resolvedAudioIndex,
                subtitleStreamIndex = resolvedInitialSubIndex,
                softwareAudioDecodingEnabled = prefs.cachedSoftwareAudioDecoding,
                forceHls = forceHls
            )
                .onSuccess { prepared ->
                    val previousSession = _uiState.value.session
                    var adjustedSession = if (resumeOverrideMs != null) {
                        prepared.session.copy(resumeTicks = resumeOverrideMs * 10_000L)
                    } else prepared.session
                    if (isCinemaIntroClip) {
                        adjustedSession = adjustedSession.copy(
                            isCinemaIntro = true,
                            resumeTicks = 0L
                        )
                    }

                    // Optional Jellyfin Cinema Intros: when starting a movie from the beginning,
                    // query /Users/{userId}/Items/{itemId}/Intros and play pre-rolls first.
                    // Not while watching together: the group plays one shared queue.
                    if (!isSameItem && !syncPlayManager.isActive && cinemaIntroQueue.shouldQueryIntros(
                            cinemaIntrosEnabled = profile.cinemaIntrosEnabled,
                            session = adjustedSession,
                            resumeOverrideMs = resumeOverrideMs,
                            fromSyncPlay = fromSyncPlay,
                            skipCinemaIntros = skipCinemaIntros,
                            isCinemaIntroClip = isCinemaIntroClip
                        )
                    ) {
                        // Pre-rolls follow the same rating restriction as everything else.
                        val introIds = playbackManager.loadCinemaIntros(activeUserId, itemId)
                            .filter { introId -> catalogRepository.isPlayableForProfile(introId, profile) }
                        if (introIds.isNotEmpty()) {
                            val step = cinemaIntroQueue.arm(
                                introIds = introIds,
                                main = com.seerflix.tv.core.player.CinemaIntroQueue.PendingMainItem(
                                    itemId = itemId,
                                    mediaSourceId = resolvedSourceId,
                                    audioStreamIndex = resolvedAudioIndex,
                                    subtitleStreamIndex = subtitleChoice,
                                    fromSyncPlay = fromSyncPlay
                                )
                            )
                            if (step is com.seerflix.tv.core.player.CinemaIntroQueue.NextStep.PlayIntro) {
                                val entry = QueueEntry(
                                    itemId = itemId,
                                    title = adjustedSession.title,
                                    mediaSourceId = resolvedSourceId
                                )
                                ownNowPlaying = entry
                                playbackQueueManager.nowPlaying = entry
                                _uiState.update {
                                    it.copy(
                                        cinemaIntroIndex = step.index,
                                        cinemaIntroTotal = step.total
                                    )
                                }
                                loadItem(
                                    itemId = step.introItemId,
                                    mediaSourceIdOverride = null,
                                    burnInSubtitleOverride = null,
                                    audioStreamIndexOverride = null,
                                    resumeOverrideMs = 0L,
                                    skipCinemaIntros = true,
                                    isCinemaIntroClip = true
                                )
                                return@onSuccess
                            }
                        }
                    }

                    // Reloading the same item (quality / version / audio switch): keep the markers we
                    // already had until the fresh lookups complete.
                    if (previousSession != null && !previousSession.isTrailer && previousSession.itemId == adjustedSession.itemId) {
                        adjustedSession = PlaybackSegments.of(previousSession).applyTo(adjustedSession).copy(
                            nextEpisodeId = previousSession.nextEpisodeId,
                            nextEpisodeTitle = previousSession.nextEpisodeTitle
                        )
                    }
                    // After a remote subtitle download: select the stream that was just added.
                    val downloadedSubtitle = preselectNewSubtitleExcluding?.let { before ->
                        val added = adjustedSession.subtitleTracks.filter { it.index !in before }
                        added.firstOrNull { it.isExternal } ?: added.firstOrNull()
                    }
                    val effectiveSubtitleChoice = downloadedSubtitle?.index ?: subtitleChoice
                    // The episodes drawer belongs to the previous series: drop it before it can be opened.
                    if (!isSameItem && previousSession?.seriesId != adjustedSession.seriesId) {
                        drawerJob?.cancel()
                        drawerSeasonJob?.cancel()
                        pendingDrawerRequest = null
                        activeSeriesTmdbId = null
                    }
                    _uiState.update {
                        val seriesChanged = !isSameItem && previousSession?.seriesId != adjustedSession.seriesId
                        it.copy(
                            session = adjustedSession,
                            selectedQuality = quality,
                            selectedMediaSourceId = adjustedSession.mediaSourceId,
                            selectedAudioStreamIndex = resolvedAudioIndex ?: adjustedSession.activeAudioStreamIndex,
                            requestedAudioStreamIndex = resolvedAudioIndex,
                            selectedSubtitleStreamIndex = resolvedBurnIn ?: effectiveSubtitleChoice?.takeIf { idx -> idx >= 0 },
                            preselectedSubtitleStreamIndex = effectiveSubtitleChoice,
                            burnInSubtitleIndex = resolvedBurnIn,
                            videoFrameRateHint = prepared.videoFrameRate,
                            drawerSeasons = if (seriesChanged) emptyList() else it.drawerSeasons,
                            drawerEpisodes = if (seriesChanged) emptyList() else it.drawerEpisodes,
                            drawerEpisodesLoading = if (seriesChanged) false else it.drawerEpisodesLoading,
                            isLoading = false
                        )
                    }
                    // A new play session: its subtitle pick hasn't been applied to the tracks yet.
                    subtitlePickApplied = false
                    if (!isCinemaIntroClip) {
                        val entry = QueueEntry(
                            itemId = adjustedSession.itemId,
                            title = adjustedSession.title,
                            mediaSourceId = adjustedSession.mediaSourceId
                        )
                        ownNowPlaying = entry
                        playbackQueueManager.nowPlaying = entry
                    }
                    pendingChannelBanner?.let { (channelId, banner) ->
                        pendingChannelBanner = null
                        if (channelId == adjustedSession.itemId) showFeedback(banner, CHANNEL_BANNER_MS)
                    }
                    if (adjustedSession.isLiveTv && _uiState.value.liveTvChannels.isEmpty()) {
                        launch(Dispatchers.IO) {
                            val channels = catalogRepository.loadLiveTvChannelsAndPrograms(activeUserId)
                            if (channels.isNotEmpty()) {
                                _uiState.update { it.copy(liveTvChannels = channels) }
                            }
                        }
                    }
                    val playSessionId = adjustedSession.playSessionId
                    syncPlayManager.localPlayingItemId = SyncPlayManager.normalizeId(adjustedSession.itemId)
                    // Watch Together runs on the home server, which only knows its own items.
                    val onHomeServer = com.seerflix.tv.core.model.ServerScopedId.isHome(adjustedSession.itemId)
                    if (!isSameItem && !fromSyncPlay && !isCinemaIntroClip && syncPlayManager.isActive && onHomeServer) {
                        // Watching together: make this the group's item; the server then tells every
                        // member (us included) to load it and starts playback once all are ready.
                        launch {
                            syncPlayManager.startQueue(adjustedSession.itemId, adjustedSession.resumeTicks)
                        }
                    }
                    prepared.segments?.let { deferred ->
                        launch {
                            val segments = deferred.await() ?: return@launch
                            updateSessionIfCurrent(playSessionId) { segments.applyTo(it) }
                        }
                    }
                    prepared.nextEpisode?.let { deferred ->
                        launch {
                            val lookup = deferred.await() ?: return@launch
                            updateSessionIfCurrent(playSessionId) { it.withNextEpisode(lookup.next) }
                        }
                    }
                    if (!adjustedSession.seriesId.isNullOrBlank()) {
                        scheduleDrawerLoad(adjustedSession.seriesId, adjustedSession.seasonId)
                    }
                    if (profile.assSubtitleMode == AssSubtitleMode.STYLED && resolvedBurnIn == null && !keepAssOverlay) {
                        val selectedSub = when {
                            effectiveSubtitleChoice == PlayerTrackSelection.SUBTITLE_OFF -> null
                            effectiveSubtitleChoice != null ->
                                adjustedSession.subtitleTracks.firstOrNull { it.index == effectiveSubtitleChoice }
                            profile.subtitleSelectionMode == SubtitleSelectionMode.NONE ||
                                _uiState.value.preferredSubtitleLang == "off" -> null
                            else -> {
                                val activeAudio = adjustedSession.audioTracks.firstOrNull {
                                    it.index == (resolvedAudioIndex ?: adjustedSession.activeAudioStreamIndex)
                                } ?: adjustedSession.audioTracks.firstOrNull { it.isDefault } ?: adjustedSession.audioTracks.firstOrNull()
                                SubtitleTrackSelector.selectSubtitleTrack(
                                    mode = profile.subtitleSelectionMode,
                                    preferredSubtitleLang = _uiState.value.preferredSubtitleLang,
                                    preferredAudioLang = _uiState.value.preferredAudioLang,
                                    activeAudioLang = activeAudio?.language,
                                    subtitleTracks = adjustedSession.subtitleTracks
                                )
                            }
                        }
                        // Only the selected track itself goes to the styled overlay: an ASS overlay
                        // loaded for a different (e.g. SRT) selection would hide that subtitle.
                        if (selectedSub != null && isAssCodec(selectedSub.codec)) {
                            adjustedSession.externalSubtitleFiles.firstOrNull { it.index == selectedSub.index }?.let { ext ->
                                loadAssSubtitleFile(ext)
                            }
                        }
                    }
                }
                .onFailure { err ->
                    if (isCinemaIntroClip && executeCinemaIntroStep(cinemaIntroQueue.advance())) {
                        // A pre-roll that can't be played is skipped, not shown as an error.
                        return@onFailure
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // A different item failed: drop the previous episode's session so the
                            // External Player button and Retry don't act on the old episode.
                            session = if (isSameItem) it.session else null,
                            errorMessage = UnifiedCatalogRepository.formatHttpError(err, strings.playerInitFailed)
                        )
                    }
                }
        }
    }

    private fun isAssCodec(codec: String?): Boolean =
        codec.equals("ass", ignoreCase = true) || codec.equals("ssa", ignoreCase = true)

    /** Shows [message] in the player HUD and clears it after [durationMs] (unless replaced). */
    private fun showFeedback(message: String, durationMs: Long = FEEDBACK_MS) {
        feedbackJob?.cancel()
        _uiState.update { it.copy(feedbackMessage = message) }
        feedbackJob = viewModelScope.launch {
            delay(durationMs)
            _uiState.update { if (it.feedbackMessage == message) it.copy(feedbackMessage = null) else it }
        }
    }

    /** Applies late-arriving playback data only if [playSessionId] is still the active session. */
    private fun updateSessionIfCurrent(
        playSessionId: String,
        transform: (PlaybackSessionData) -> PlaybackSessionData
    ) {
        _uiState.update { state ->
            val current = state.session
            if (current != null && current.playSessionId == playSessionId) {
                state.copy(session = transform(current))
            } else {
                state
            }
        }
    }

    private fun PlaybackSessionData.withNextEpisode(next: NextEpisodeInfo?): PlaybackSessionData =
        copy(nextEpisodeId = next?.id, nextEpisodeTitle = next?.title)

    fun retryPlayback(currentPosMs: Long = 0L) {
        val currentSession = _uiState.value.session
        if (!trailerYoutubeKey.isNullOrBlank() || !localTrailerId.isNullOrBlank()) {
            // A cached YouTube stream URL may have expired (403) — resolve a fresh one.
            trailerResolver.evict(trailerYoutubeKey)
            loadTrailer(trailerTitle ?: strings.actionTrailer, localTrailerId, trailerYoutubeKey)
        } else {
            // A failed pre-roll is skipped (reloading it as a normal item would drop the intro
            // queue and report / mark the clip as played).
            if (requestedIsCinemaIntroClip && executeCinemaIntroStep(cinemaIntroQueue.advance())) return
            val retryId = requestedItemId ?: currentSession?.itemId ?: initialItemId
            // The failed session was reported as started once playback got going; stop it so its
            // server transcode doesn't keep running next to the retry's. (Not at 0: that would
            // wipe the resume point of a session that never really played.)
            if (currentPosMs > 0L && currentSession?.itemId == retryId) reportStopped(currentPosMs)
            loadItem(
                itemId = retryId,
                qualityOverride = _uiState.value.selectedQuality,
                resumeOverrideMs = currentPosMs.takeIf { it > 0L && currentSession?.itemId == retryId },
                // The movie after its intros: don't play the intros again.
                skipCinemaIntros = requestedSkipCinemaIntros && !requestedIsCinemaIntroClip
            )
        }
    }

    fun onPlaybackError(errorDetail: String?, currentPosMs: Long) {
        val currentState = _uiState.value
        val session = currentState.session ?: return
        if (session.isCinemaIntro && executeCinemaIntroStep(cinemaIntroQueue.advance())) {
            // A pre-roll that fails to play is skipped: on to the next intro or the movie.
            return
        }
        if (!session.isTrailer && !session.isCinemaIntro && !session.isHls && currentState.selectedQuality == QualityPreset.AUTO) {
            val fallbackQuality = if (session.resolutionLabel?.contains("4K", ignoreCase = true) == true) {
                QualityPreset.UHD_4K
            } else {
                QualityPreset.HD_1080P
            }
            reportStopped(currentPosMs)
            qualityPinnedByError = true
            loadItem(
                itemId = session.itemId,
                qualityOverride = fallbackQuality,
                resumeOverrideMs = currentPosMs.takeIf { it > 0L }
            )
            showFeedback(strings.playerTranscodeFallback.format(fallbackQuality.label), 4000L)
        } else {
            val detail = errorDetail?.takeIf { it.isNotBlank() } ?: strings.playerStreamError
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = UnifiedCatalogRepository.formatHttpError(
                        IllegalStateException(detail),
                        strings.playerPlaybackError.format(detail)
                    )
                )
            }
        }
    }

    private fun autoLoadMatchingAssTrack(session: PlaybackSessionData, preferredLang: String?) {
        val assFiles = session.externalSubtitleFiles.filter {
            it.codec.equals("ass", ignoreCase = true) || it.codec.equals("ssa", ignoreCase = true)
        }
        if (assFiles.isEmpty()) return
        val lang = preferredLang?.takeIf { it.isNotBlank() }
        val preferredMatch = if (lang != null) {
            // A preferred language with no matching track: don't force a foreign-language track on.
            assFiles.firstOrNull { it.language?.equals(lang, ignoreCase = true) == true } ?: return
        } else {
            assFiles.firstOrNull() ?: return
        }
        loadAssSubtitleFile(preferredMatch)
    }

    private fun loadAssSubtitleFile(extFile: ExternalSubtitleFile) {
        assLoadJob?.cancel()
        assLoadJob = viewModelScope.launch {
            val cues = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(extFile.url).build()
                    mediaHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) emptyList()
                        else AssSubtitleParser.parse(resp.body?.string().orEmpty())
                    }
                }.getOrDefault(emptyList())
            }
            _uiState.update {
                it.copy(
                    activeAssCues = cues,
                    activeAssSubtitleIndex = if (cues.isNotEmpty()) extFile.index else null
                )
            }
        }
    }

    fun selectExternalAssTrack(extFile: ExternalSubtitleFile?, currentPosMs: Long) {
        val session = _uiState.value.session ?: return
        if (extFile == null) {
            assLoadJob?.cancel()
            val wasBurnIn = _uiState.value.burnInSubtitleIndex != null
            _uiState.update {
                it.copy(activeAssCues = emptyList(), activeAssSubtitleIndex = null, burnInSubtitleIndex = null)
            }
            if (wasBurnIn) {
                reportStopped(currentPosMs)
                loadItem(
                    session.itemId,
                    resumeOverrideMs = currentPosMs,
                    burnInSubtitleOverride = null,
                    subtitleSelection = PlayerTrackSelection.SUBTITLE_OFF
                )
            }
            return
        }
        if (_uiState.value.assSubtitleMode == AssSubtitleMode.BURN_IN) {
            reportStopped(currentPosMs)
            loadItem(session.itemId, resumeOverrideMs = currentPosMs, burnInSubtitleOverride = extFile.index)
        } else {
            loadAssSubtitleFile(extFile)
        }
    }

    fun setAssSubtitleMode(mode: AssSubtitleMode, currentPosMs: Long) {
        val session = _uiState.value.session
        // The styled track in use, so the new mode keeps showing the same subtitle.
        val previousAssIndex = _uiState.value.activeAssSubtitleIndex
        if (mode != AssSubtitleMode.STYLED) assLoadJob?.cancel()
        _uiState.update {
            it.copy(
                assSubtitleMode = mode,
                activeAssCues = if (mode != AssSubtitleMode.STYLED) emptyList() else it.activeAssCues,
                activeAssSubtitleIndex = if (mode != AssSubtitleMode.STYLED) null else it.activeAssSubtitleIndex
            )
        }
        viewModelScope.launch {
            authRepository.updatePlaybackPreferences(assSubtitleMode = mode)
            if (session == null || session.isTrailer) return@launch
            applyAssSubtitleMode(session, mode, currentPosMs, previousAssIndex)
        }
    }

    private fun applyAssSubtitleMode(
        session: PlaybackSessionData,
        mode: AssSubtitleMode,
        currentPosMs: Long,
        previousAssIndex: Int?
    ) {
        val firstAss = session.externalSubtitleFiles.firstOrNull { isAssCodec(it.codec) }
        val burnIn = _uiState.value.burnInSubtitleIndex
        when (mode) {
            AssSubtitleMode.BURN_IN -> {
                val idx = previousAssIndex ?: firstAss?.index
                if (idx != null) {
                    reportStopped(currentPosMs)
                    loadItem(session.itemId, resumeOverrideMs = currentPosMs, burnInSubtitleOverride = idx)
                }
            }
            AssSubtitleMode.STYLED -> {
                if (burnIn != null) {
                    reportStopped(currentPosMs)
                    // The burned-in track comes back as the styled overlay (if it is ASS/SSA).
                    loadItem(
                        session.itemId,
                        resumeOverrideMs = currentPosMs,
                        burnInSubtitleOverride = null,
                        subtitleSelection = burnIn
                    )
                } else {
                    autoLoadMatchingAssTrack(session, _uiState.value.preferredSubtitleLang)
                }
            }
            AssSubtitleMode.PLAIN -> {
                // ASS/SSA files are only side-loaded into ExoPlayer as plain text tracks when the
                // media item is built in PLAIN mode, so the item has to be reloaded for them to appear.
                if (burnIn != null || firstAss != null) {
                    reportStopped(currentPosMs)
                    val keepTrack = previousAssIndex ?: burnIn
                    loadItem(
                        session.itemId,
                        resumeOverrideMs = currentPosMs,
                        burnInSubtitleOverride = null,
                        subtitleSelection = keepTrack ?: KEEP_SUBTITLE
                    )
                }
            }
        }
    }

    fun switchMediaSource(mediaSourceId: String, currentPosMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer || mediaSourceId == session.mediaSourceId) return
        // A version that lives on another server is a different item there: from here on that
        // copy is what plays (and what progress is reported for).
        val otherCopyId = session.mediaSources.firstOrNull { it.id == mediaSourceId }?.itemId
        reportStopped(currentPosMs)
        loadItem(
            itemId = otherCopyId ?: session.itemId,
            mediaSourceIdOverride = mediaSourceId,
            resumeOverrideMs = currentPosMs
        )
    }

    fun toggleAudioPassthrough(currentPosMs: Long) {
        val next = !_uiState.value.audioPassthroughEnabled
        _uiState.update { it.copy(audioPassthroughEnabled = next) }
        viewModelScope.launch {
            authRepository.updatePlaybackPreferences(audioPassthroughEnabled = next)
            val session = _uiState.value.session
            if (session != null && !session.isTrailer) {
                reportStopped(currentPosMs)
                loadItem(session.itemId, resumeOverrideMs = currentPosMs)
            }
        }
    }

    fun toggleStereoDownmix(currentPosMs: Long) {
        val next = !_uiState.value.stereoDownmixEnabled
        _uiState.update { it.copy(stereoDownmixEnabled = next) }
        viewModelScope.launch {
            authRepository.updatePlaybackPreferences(stereoDownmixEnabled = next)
            val session = _uiState.value.session
            if (session != null && !session.isTrailer) {
                reportStopped(currentPosMs)
                loadItem(session.itemId, resumeOverrideMs = currentPosMs)
            }
        }
    }

    fun openInExternalPlayer(context: Context, currentPosMs: Long) {
        val session = _uiState.value.session ?: return
        reportStopped(currentPosMs)
        val launched = ExternalPlayerHelper.launchExternalPlayer(
            context = context,
            streamUrl = session.directPlayUrl ?: session.streamUrl,
            title = session.title,
            positionMs = currentPosMs,
            externalSubtitles = session.externalSubtitleFiles,
            chooserTitle = strings.playerOpenWithExternal
        )
        if (!launched) {
            // Playback goes on here: let progress reports for this session resume.
            if (stoppedPlaySessionId == session.playSessionId) stoppedPlaySessionId = null
            showFeedback(strings.detailsNoExternalPlayer)
        } else {
            externalHandoffPlaySessionId = session.playSessionId
        }
    }

    /** In a SyncPlay group play/pause are requests to the group, applied when the server says so. */
    fun isSyncPlayActive(): Boolean = syncPlayManager.isActive

    fun requestGroupPause() {
        viewModelScope.launch { syncPlayManager.requestPause() }
    }

    fun requestGroupUnpause() {
        viewModelScope.launch { syncPlayManager.requestUnpause() }
    }

    fun notifySyncPlaySeek(positionMs: Long) {
        if (!syncPlayManager.isActive) return
        viewModelScope.launch { syncPlayManager.requestSeek(positionMs.coerceAtLeast(0L) * 10_000L) }
    }

    fun reportSyncPlayState(ready: Boolean, positionMs: Long, isPlaying: Boolean) {
        if (!syncPlayManager.isActive) return
        val ticks = positionMs.coerceAtLeast(0L) * 10_000L
        viewModelScope.launch {
            if (ready) syncPlayManager.reportReady(ticks, isPlaying) else syncPlayManager.reportBuffering(ticks, isPlaying)
        }
    }

    fun isCurrentItem(itemId: String): Boolean {
        val current = _uiState.value.session?.itemId ?: return false
        return SyncPlayManager.normalizeId(current) == SyncPlayManager.normalizeId(itemId)
    }

    override fun onCleared() {
        syncPlayManager.localPlayingItemId = null
        // The next player (remote Play Now) may already have published its own entry.
        if (playbackQueueManager.nowPlaying === ownNowPlaying) playbackQueueManager.nowPlaying = null
        super.onCleared()
    }

    /**
     * The drawer pulls full series details (seasons, possibly TMDB metadata); doing that while the
     * stream starts competes with it for network and CPU on low-end sticks, so it is deferred.
     */
    private fun scheduleDrawerLoad(seriesId: String, currentSeasonId: String?) {
        drawerJob?.cancel()
        pendingDrawerRequest = seriesId to currentSeasonId
        drawerJob = viewModelScope.launch {
            delay(DRAWER_PRELOAD_DELAY_MS)
            loadPendingDrawer()
        }
    }

    /** Loads the episodes drawer right away if it hasn't been loaded yet (called when it opens). */
    fun ensureDrawerLoaded() {
        if (pendingDrawerRequest == null) return
        drawerJob?.cancel()
        drawerJob = viewModelScope.launch { loadPendingDrawer() }
    }

    private suspend fun loadPendingDrawer() {
        val (seriesId, currentSeasonId) = pendingDrawerRequest ?: return
        pendingDrawerRequest = null
        _uiState.update { it.copy(drawerEpisodesLoading = true) }
        val details = catalogRepository.loadFullDetails(
            userId = activeUserId,
            tmdbId = null,
            jellyfinId = seriesId,
            mediaType = MediaType.SERIES
        ).getOrNull()
        // The queue / autoplay moved to another series while this loaded.
        if (_uiState.value.session?.seriesId != seriesId) return
        if (details == null) {
            _uiState.update { it.copy(drawerEpisodesLoading = false) }
            return
        }
        activeSeriesTmdbId = details.tmdbId
        val targetSeason = details.seasons.firstOrNull { it.jellyfinSeasonId == currentSeasonId }?.seasonNumber
            ?: details.seasons.firstOrNull()?.seasonNumber
            ?: 1
        _uiState.update {
            it.copy(
                drawerSeasons = details.seasons,
                drawerSelectedSeason = targetSeason
            )
        }
        selectDrawerSeason(targetSeason)
    }

    fun selectDrawerSeason(seasonNumber: Int) {
        val seriesId = _uiState.value.session?.seriesId ?: return
        val season = _uiState.value.drawerSeasons.firstOrNull { it.seasonNumber == seasonNumber }
        drawerSeasonJob?.cancel()
        drawerSeasonJob = viewModelScope.launch {
            _uiState.update { it.copy(drawerSelectedSeason = seasonNumber, drawerEpisodesLoading = true) }
            val eps = catalogRepository.loadSeasonEpisodes(
                userId = activeUserId,
                tmdbId = activeSeriesTmdbId,
                jellyfinSeriesId = seriesId,
                jellyfinSeasonId = season?.jellyfinSeasonId,
                seasonNumber = seasonNumber
            )
            // Another series started meanwhile: these episodes belong to the previous one.
            if (_uiState.value.session?.seriesId != seriesId) return@launch
            _uiState.update { it.copy(drawerEpisodes = eps, drawerEpisodesLoading = false) }
        }
    }

    fun selectQuality(preset: QualityPreset, currentPosMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer || preset == _uiState.value.selectedQuality) return
        reportStopped(currentPosMs)
        loadItem(session.itemId, qualityOverride = preset, resumeOverrideMs = currentPosMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(selectedSpeed = speed) }
    }

    fun selectServerAudioTrack(audioStreamIndex: Int, currentPosMs: Long, forceHls: Boolean = false) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        reportStopped(currentPosMs)
        loadItem(
            itemId = session.itemId,
            resumeOverrideMs = currentPosMs,
            audioStreamIndexOverride = audioStreamIndex,
            forceHls = forceHls || session.isHls
        )
    }

    /**
     * Burns [subtitleIndex] in on the server, or (null) stops burning in; [textSubtitleIndex] is
     * then the subtitle to show instead: a Jellyfin index, [PlayerTrackSelection.SUBTITLE_OFF], or
     * null for the profile's automatic choice.
     */
    fun selectBurnInSubtitleTrack(
        subtitleIndex: Int?,
        currentPosMs: Long,
        textSubtitleIndex: Int? = PlayerTrackSelection.SUBTITLE_OFF
    ) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        assLoadJob?.cancel()
        _uiState.update { it.copy(activeAssCues = emptyList(), activeAssSubtitleIndex = null) }
        if (subtitleIndex == _uiState.value.burnInSubtitleIndex) return
        reportStopped(currentPosMs)
        loadItem(
            itemId = session.itemId,
            resumeOverrideMs = currentPosMs,
            burnInSubtitleOverride = subtitleIndex,
            subtitleSelection = if (subtitleIndex == null) textSubtitleIndex else KEEP_SUBTITLE
        )
    }

    fun updateActiveTrackIndexes(audioIndex: Int?, subtitleIndex: Int?) {
        _uiState.update {
            it.copy(
                selectedAudioStreamIndex = audioIndex ?: it.selectedAudioStreamIndex,
                selectedSubtitleStreamIndex = subtitleIndex
            )
        }
    }

    fun setVideoResizeMode(mode: VideoResizeMode) {
        _uiState.update { it.copy(videoResizeMode = mode) }
    }

    fun adjustAudioOffset(deltaMs: Long) {
        _uiState.update {
            val next = (it.audioOffsetMs + deltaMs).coerceIn(-AudioDelayProcessor.MAX_OFFSET_MS, AudioDelayProcessor.MAX_OFFSET_MS)
            it.copy(audioOffsetMs = next)
        }
    }

    fun resetAudioOffset() {
        _uiState.update { it.copy(audioOffsetMs = 0L) }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        val endMs = option.minutes?.takeIf { it > 0 }?.let { mins ->
            System.currentTimeMillis() + mins * 60_000L
        }
        _uiState.update { it.copy(sleepTimerOption = option, sleepTimerEndMs = endMs) }
    }

    fun clearSleepTimer() {
        _uiState.update { it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerEndMs = null) }
    }

    fun confirmStillWatching() {
        val nextId = _uiState.value.pendingStillWatchingNextId
        val queued = pendingStillWatchingEntry?.takeIf { it.itemId == nextId }
        pendingStillWatchingEntry = null
        consecutiveAutoplayedEpisodes = 0
        _uiState.update {
            it.copy(
                showStillWatchingPrompt = false,
                pendingStillWatchingNextId = null,
                pendingStillWatchingNextTitle = null
            )
        }
        if (queued != null) {
            // The queue already moved to this entry; play it with its version / start position.
            loadItem(
                itemId = queued.itemId,
                mediaSourceIdOverride = queued.mediaSourceId,
                resumeOverrideMs = queued.startPositionMs.takeIf { it > 0L }
            )
        } else if (!nextId.isNullOrBlank()) {
            loadItem(nextId)
        }
    }

    fun dismissStillWatching() {
        pendingStillWatchingEntry = null
        _uiState.update {
            it.copy(
                showStillWatchingPrompt = false,
                pendingStillWatchingNextId = null,
                pendingStillWatchingNextTitle = null,
                autoplayCancelled = true
            )
        }
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        _uiState.update {
            val next = (it.subtitleOffsetMs + deltaMs).coerceIn(-60_000L, 60_000L)
            it.copy(subtitleOffsetMs = next)
        }
    }

    fun resetSubtitleOffset() {
        _uiState.update { it.copy(subtitleOffsetMs = 0L) }
    }

    fun toggleAutoSkipIntro() {
        val next = !_uiState.value.autoSkipIntro
        _uiState.update { it.copy(autoSkipIntro = next) }
        viewModelScope.launch {
            authRepository.updatePlaybackPreferences(autoSkipIntro = next)
        }
    }

    fun toggleAutoSkipRecap() {
        val next = !_uiState.value.autoSkipRecap
        _uiState.update { it.copy(autoSkipRecap = next) }
        viewModelScope.launch {
            authRepository.updatePlaybackPreferences(autoSkipRecap = next)
        }
    }

    fun setFrameRateMatchMode(mode: FrameRateMatchMode) {
        _uiState.update { it.copy(frameRateMatchMode = mode) }
        viewModelScope.launch {
            authRepository.updatePlaybackPreferences(frameRateMatchMode = mode)
        }
    }

    fun searchRemoteSubtitles(language: String = _uiState.value.remoteSubtitleLanguage) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        remoteSubtitleSearchJob?.cancel()
        remoteSubtitleSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    remoteSubtitleLanguage = language,
                    remoteSubtitlesLoading = true,
                    subtitleSearchMessage = null
                )
            }
            val result = playbackManager.searchRemoteSubtitles(session.itemId, language)
            // The player moved on to another item meanwhile: these results are for the old one.
            if (_uiState.value.session?.itemId != session.itemId) return@launch
            result
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            remoteSubtitles = results,
                            remoteSubtitlesLoading = false,
                            subtitleSearchMessage = if (results.isEmpty()) strings.playerNoRemoteSubtitles.format(language) else null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            remoteSubtitlesLoading = false,
                            subtitleSearchMessage = strings.playerSubtitleSearchFailed.format(err.message ?: strings.commonUnknownError)
                        )
                    }
                }
        }
    }

    fun downloadRemoteSubtitle(subtitleId: String, currentPosMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        val searchLang = _uiState.value.remoteSubtitleLanguage
        remoteSubtitleDownloadJob?.cancel()
        remoteSubtitleDownloadJob = viewModelScope.launch {
            _uiState.update { it.copy(downloadingSubtitleId = subtitleId, subtitleSearchMessage = null) }
            val result = playbackManager.downloadRemoteSubtitle(session.itemId, subtitleId)
            // Autoplay / the drawer moved on meanwhile: don't reload the old episode.
            val current = _uiState.value.session
            if (current == null || current.itemId != session.itemId) return@launch
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            downloadingSubtitleId = null,
                            preferredSubtitleLang = searchLang,
                            subtitleSearchMessage = strings.playerSubtitleDownloaded
                        )
                    }
                    reportStopped(currentPosMs)
                    loadItem(
                        session.itemId,
                        resumeOverrideMs = currentPosMs,
                        preselectNewSubtitleExcluding = current.subtitleTracks.mapTo(HashSet()) { it.index }
                    )
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            downloadingSubtitleId = null,
                            subtitleSearchMessage = strings.playerSubtitleDownloadFailed.format(err.message ?: strings.commonUnknownError)
                        )
                    }
                }
        }
    }

    fun setSubtitleAppearance(fontScale: Float, bgAlpha: Float) {
        _uiState.update { it.copy(subtitleFontScale = fontScale, subtitleBackgroundAlpha = bgAlpha) }
        viewModelScope.launch {
            authRepository.updateSubtitleAppearance(fontScale, bgAlpha)
        }
    }

    fun setSubtitleStyle(
        textColor: com.seerflix.tv.core.model.SubtitleTextColor? = null,
        edgeStyle: com.seerflix.tv.core.model.SubtitleEdgeStyle? = null,
        position: com.seerflix.tv.core.model.SubtitlePosition? = null
    ) {
        _uiState.update {
            it.copy(
                subtitleTextColor = textColor ?: it.subtitleTextColor,
                subtitleEdgeStyle = edgeStyle ?: it.subtitleEdgeStyle,
                subtitlePosition = position ?: it.subtitlePosition
            )
        }
        viewModelScope.launch {
            authRepository.updateSubtitleStyle(textColor, edgeStyle, position)
        }
    }

    fun toggleStreamInfo() {
        _uiState.update { it.copy(showStreamInfo = !it.showStreamInfo) }
    }

    fun cancelAutoplayNext() {
        _uiState.update { it.copy(autoplayCancelled = true) }
    }

    fun consumePreselectedSubtitle(): Int? {
        subtitlePickApplied = true
        val idx = _uiState.value.preselectedSubtitleStreamIndex
        if (idx != null) {
            _uiState.update { it.copy(preselectedSubtitleStreamIndex = null) }
        }
        return idx
    }

    /**
     * Advances to the next queued item if a queue is active, otherwise falls back to the current
     * session's [PlaybackSessionData.nextEpisodeId]. Returns true if navigation was triggered.
     */
    fun playNextInQueueOrEpisode(
        currentPosMs: Long,
        reportState: PlaybackReportState? = null,
        fromAutoplay: Boolean = false
    ): Boolean {
        val nextQueue = playbackQueueManager.advanceNext()
        if (nextQueue != null) {
            reportStopped(currentPosMs, reportState)
            loadItem(
                itemId = nextQueue.itemId,
                mediaSourceIdOverride = nextQueue.mediaSourceId,
                resumeOverrideMs = nextQueue.startPositionMs.takeIf { it > 0L },
                isAutoplayAdvance = fromAutoplay,
                nextTitleForPrompt = nextQueue.title,
                pendingQueueEntry = nextQueue
            )
            return true
        }
        val nextEpId = _uiState.value.session?.nextEpisodeId ?: return false
        reportStopped(currentPosMs, reportState)
        loadItem(
            nextEpId,
            isAutoplayAdvance = fromAutoplay,
            nextTitleForPrompt = _uiState.value.session?.nextEpisodeTitle
        )
        return true
    }

    /** Steps back to the previous item in the active queue. Returns true if handled. */
    fun playPreviousInQueue(
        currentPosMs: Long,
        reportState: PlaybackReportState? = null
    ): Boolean {
        val prevQueue = playbackQueueManager.stepPrevious() ?: return false
        reportStopped(currentPosMs, reportState)
        loadItem(
            itemId = prevQueue.itemId,
            mediaSourceIdOverride = prevQueue.mediaSourceId,
            resumeOverrideMs = prevQueue.startPositionMs.takeIf { it > 0L }
        )
        return true
    }

    /** Advances from a finished Cinema Intro clip to the next intro or the main movie. */
    fun advanceCinemaIntro(): Boolean {
        if (_uiState.value.session?.isCinemaIntro != true && !cinemaIntroQueue.hasPendingMain) return false
        return executeCinemaIntroStep(cinemaIntroQueue.advance())
    }

    /** Skips all remaining Cinema Intro clips and starts the main movie immediately. */
    fun skipCinemaIntros(): Boolean {
        if (_uiState.value.session?.isCinemaIntro != true && !cinemaIntroQueue.hasPendingMain) return false
        return executeCinemaIntroStep(cinemaIntroQueue.skipToMain())
    }

    private fun executeCinemaIntroStep(step: com.seerflix.tv.core.player.CinemaIntroQueue.NextStep): Boolean {
        return when (step) {
            is com.seerflix.tv.core.player.CinemaIntroQueue.NextStep.PlayIntro -> {
                _uiState.update { it.copy(cinemaIntroIndex = step.index, cinemaIntroTotal = step.total) }
                loadItem(
                    itemId = step.introItemId,
                    mediaSourceIdOverride = null,
                    burnInSubtitleOverride = null,
                    audioStreamIndexOverride = null,
                    resumeOverrideMs = 0L,
                    skipCinemaIntros = true,
                    isCinemaIntroClip = true
                )
                true
            }
            is com.seerflix.tv.core.player.CinemaIntroQueue.NextStep.PlayMain -> {
                _uiState.update { it.copy(cinemaIntroIndex = 0, cinemaIntroTotal = 0) }
                loadItem(
                    itemId = step.main.itemId,
                    mediaSourceIdOverride = step.main.mediaSourceId,
                    burnInSubtitleOverride = null,
                    audioStreamIndexOverride = step.main.audioStreamIndex,
                    resumeOverrideMs = 0L,
                    fromSyncPlay = step.main.fromSyncPlay,
                    skipCinemaIntros = true,
                    isCinemaIntroClip = false,
                    subtitleSelection = step.main.subtitleStreamIndex
                )
                true
            }
            com.seerflix.tv.core.player.CinemaIntroQueue.NextStep.None -> false
        }
    }

    /** Surfs to the next (+1) or previous (-1) Live TV channel in-place. */
    fun surfChannel(delta: Int, currentPosMs: Long, reportState: PlaybackReportState? = null): Boolean {
        val state = _uiState.value
        val session = state.session ?: return false
        if (!session.isLiveTv) return false
        val next = com.seerflix.tv.core.data.LiveTvEpgHelper.nextChannelId(
            channels = state.liveTvChannels,
            currentChannelId = session.itemId,
            delta = delta
        ) ?: return false
        tuneChannel(next, currentPosMs, reportState)
        return true
    }

    fun tuneChannel(
        channel: com.seerflix.tv.core.model.LiveTvChannel,
        currentPosMs: Long,
        reportState: PlaybackReportState? = null
    ) {
        val label = if (!channel.number.isNullOrBlank()) strings.playerChannelBanner.format(channel.number, channel.name) else channel.name
        // Shown once the channel has loaded (loading a different item clears the HUD message).
        pendingChannelBanner = channel.id to label
        // Ends the old channel's session so the server frees its tuner / live transcode.
        reportStopped(currentPosMs, reportState)
        loadItem(
            itemId = channel.id,
            mediaSourceIdOverride = null,
            burnInSubtitleOverride = null,
            audioStreamIndexOverride = null,
            resumeOverrideMs = 0L,
            skipCinemaIntros = true
        )
    }

    private fun buildDefaultReportState(override: PlaybackReportState?): PlaybackReportState {
        val state = _uiState.value
        val session = state.session
        return PlaybackReportState(
            playMethod = override?.playMethod ?: session?.playMethod?.jellyfinValue,
            audioStreamIndex = override?.audioStreamIndex ?: state.selectedAudioStreamIndex ?: session?.activeAudioStreamIndex,
            subtitleStreamIndex = override?.subtitleStreamIndex ?: state.burnInSubtitleIndex ?: state.activeAssSubtitleIndex ?: state.selectedSubtitleStreamIndex,
            isMuted = override?.isMuted,
            volumeLevel = override?.volumeLevel,
            playbackRate = override?.playbackRate ?: state.selectedSpeed
        )
    }

    fun reportStart(positionMs: Long, reportState: PlaybackReportState? = null) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer || session.isCinemaIntro) return
        val full = buildDefaultReportState(reportState)
        viewModelScope.launch {
            playbackManager.reportStart(session, positionMs * 10_000L, full)
        }
    }

    fun reportProgress(positionMs: Long, isPaused: Boolean, reportState: PlaybackReportState? = null) {
        val state = _uiState.value
        val session = state.session ?: return
        // While a new item/version is loading the old session has already been reported as stopped;
        // a late progress report would resurrect it on the server. Same while the "Still watching?"
        // prompt holds the next item back after this one was reported stopped.
        if (session.isTrailer || session.isCinemaIntro || state.isLoading || state.errorMessage != null) return
        if (state.showStillWatchingPrompt) return
        if (session.playSessionId == stoppedPlaySessionId) {
            // Handed to an external player, then resumed here: report this session again.
            if (externalHandoffPlaySessionId != session.playSessionId || isPaused) return
            externalHandoffPlaySessionId = null
            stoppedPlaySessionId = null
            reportStart(positionMs, reportState)
            return
        }
        val full = buildDefaultReportState(reportState)
        viewModelScope.launch {
            playbackManager.reportProgress(session, positionMs * 10_000L, isPaused, full)
        }
    }

    fun reportStopped(positionMs: Long, reportState: PlaybackReportState? = null) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer || session.isCinemaIntro) return
        // Once per play session (e.g. a channel change while the previous one is still loading).
        if (session.playSessionId == stoppedPlaySessionId) return
        stoppedPlaySessionId = session.playSessionId
        val full = buildDefaultReportState(reportState)
        // Not viewModelScope: this is called from the screen's onDispose right before the
        // ViewModel is cleared, which would cancel the request before it is sent.
        playbackManager.reportStoppedDetached(session, positionMs.coerceAtLeast(0L) * 10_000L, full)
        catalogRepository.invalidateDetailsCache()
    }

    private companion object {
        const val TRAILER_RESOLVE_TIMEOUT_MS = 15_000L
        const val DRAWER_PRELOAD_DELAY_MS = 6_000L
        /** Sentinel: keep the current version (same item only). Compared by identity. */
        val KEEP_SOURCE: String = String(charArrayOf('\u0000'))
        const val KEEP_BURN_IN = Int.MIN_VALUE
        const val KEEP_AUDIO_INDEX = Int.MIN_VALUE
        /** Sentinel: carry the current subtitle selection over (same item) / initial pick. */
        const val KEEP_SUBTITLE = Int.MIN_VALUE
        const val FEEDBACK_MS = 4_000L
        const val CHANNEL_BANNER_MS = 3_000L
    }
}

