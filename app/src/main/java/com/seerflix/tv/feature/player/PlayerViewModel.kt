package com.seerflix.tv.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
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
import com.seerflix.tv.core.player.PlaybackReportState
import com.seerflix.tv.core.player.PlaybackSegments
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

data class PlayerUiState(
    val session: PlaybackSessionData? = null,
    val selectedSpeed: Float = 1.0f,
    val selectedQuality: QualityPreset = QualityPreset.AUTO,
    val selectedMediaSourceId: String? = null,
    val selectedAudioStreamIndex: Int? = null,
    val selectedSubtitleStreamIndex: Int? = null,
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
    private val initialSubtitleStreamIndex: Int? = savedStateHandle.get<String>("subtitleIndex")?.toIntOrNull()?.takeIf { it >= 0 }
    private val trailerYoutubeKey: String? = savedStateHandle.get<String>("youtubeKey")?.takeIf { it.isNotBlank() && it != "none" }
    private val localTrailerId: String? = savedStateHandle.get<String>("localTrailerId")?.takeIf { it.isNotBlank() && it != "none" }
    private val trailerTitle: String? = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }
    /** Start position handed over by "Play on" from another app or by a SyncPlay group. */
    private val initialStartMs: Long? = savedStateHandle.get<String>("startMs")?.toLongOrNull()?.takeIf { it >= 0L }
    private val startFromBeginning: Boolean = savedStateHandle.get<String>("startMs") == "0" &&
        savedStateHandle.get<String>("fromStart") == "true"
    private val openedBySyncPlay: Boolean = savedStateHandle.get<String>("syncplay") == "true"

    /** Group commands for the player (only while in a SyncPlay group). */
    val syncPlayActions: SharedFlow<SyncPlayAction> = syncPlayManager.actions
    val syncPlayNotices: SharedFlow<String> = syncPlayManager.notices

    /** Remote-control commands from other Jellyfin apps that act on the player. */
    val remotePlaystate: Flow<ServerEvent.Playstate> = socketClient.events.filterIsInstance<ServerEvent.Playstate>()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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

    init {
        viewModelScope.launch {
            syncPlayManager.activeGroup.collect { group ->
                _uiState.update { it.copy(syncPlayGroup = group) }
            }
        }
        if (!trailerYoutubeKey.isNullOrBlank() || !localTrailerId.isNullOrBlank()) {
            loadTrailer(trailerTitle ?: "Trailer", localTrailerId, trailerYoutubeKey)
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
                    it.copy(isLoading = false, errorMessage = "Unable to resolve trailer stream")
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    session = PlaybackSessionData(
                        itemId = "trailer_${youtubeKey ?: localTrailerId ?: "clip"}",
                        title = title,
                        subtitle = "Trailer",
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
        nextTitleForPrompt: String? = null
    ) {
        if (itemId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid media item ID") }
            return
        }
        val isSameItem = _uiState.value.session?.itemId == itemId
        if (!isSameItem) {
            if (isAutoplayAdvance) {
                consecutiveAutoplayedEpisodes++
                val limit = _uiState.value.bingeLimitEpisodes
                if (limit > 0 && consecutiveAutoplayedEpisodes >= limit) {
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
        if (!isSameItem) assLoadJob?.cancel()
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
                    feedbackMessage = null,
                    autoplayCancelled = false,
                    showStillWatchingPrompt = false,
                    pendingStillWatchingNextId = null,
                    pendingStillWatchingNextTitle = null,
                    subtitleOffsetMs = if (isSameItem) it.subtitleOffsetMs else 0L,
                    audioOffsetMs = if (isSameItem) it.audioOffsetMs else 0L,
                    remoteSubtitles = if (isSameItem) it.remoteSubtitles else emptyList(),
                    subtitleSearchMessage = if (isSameItem) it.subtitleSearchMessage else null,
                    activeAssCues = if (isSameItem) it.activeAssCues else emptyList(),
                    activeAssSubtitleIndex = if (isSameItem) it.activeAssSubtitleIndex else null
                )
            }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null || profile.jellyfinUserId.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active Jellyfin profile") }
                return@launch
            }
            activeUserId = profile.jellyfinUserId
            // Restricted profiles: check the rating before anything plays, whatever opened the player.
            if (!catalogRepository.isPlayableForProfile(itemId, profile)) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = BLOCKED_FOR_PROFILE_MESSAGE)
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
            val resolvedInitialSubIndex = if (!isSameItem && itemId == initialItemId) initialSubtitleStreamIndex else null
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
                    preferredAudioLang = profile.preferredAudioLang,
                    preferredSubtitleLang = profile.preferredSubtitleLang
                )
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
                    // Reloading the same item (quality / version / audio switch): keep the markers we
                    // already had until the fresh lookups complete.
                    if (previousSession != null && !previousSession.isTrailer && previousSession.itemId == adjustedSession.itemId) {
                        adjustedSession = PlaybackSegments.of(previousSession).applyTo(adjustedSession).copy(
                            nextEpisodeId = previousSession.nextEpisodeId,
                            nextEpisodeTitle = previousSession.nextEpisodeTitle
                        )
                    }
                    _uiState.update {
                        it.copy(
                            session = adjustedSession,
                            selectedQuality = quality,
                            selectedMediaSourceId = adjustedSession.mediaSourceId,
                            selectedAudioStreamIndex = resolvedAudioIndex ?: adjustedSession.activeAudioStreamIndex,
                            selectedSubtitleStreamIndex = resolvedBurnIn ?: resolvedInitialSubIndex,
                            burnInSubtitleIndex = resolvedBurnIn,
                            videoFrameRateHint = prepared.videoFrameRate,
                            isLoading = false
                        )
                    }
                    val playSessionId = adjustedSession.playSessionId
                    syncPlayManager.localPlayingItemId = SyncPlayManager.normalizeId(adjustedSession.itemId)
                    // Watch Together runs on the home server, which only knows its own items.
                    val onHomeServer = com.seerflix.tv.core.model.ServerScopedId.isHome(adjustedSession.itemId)
                    if (!isSameItem && !fromSyncPlay && syncPlayManager.isActive && onHomeServer) {
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
                    if (profile.assSubtitleMode == AssSubtitleMode.STYLED &&
                        resolvedBurnIn == null &&
                        profile.subtitleSelectionMode != SubtitleSelectionMode.NONE &&
                        profile.preferredSubtitleLang != "off"
                    ) {
                        val activeAudio = adjustedSession.audioTracks.firstOrNull {
                            it.index == (resolvedAudioIndex ?: adjustedSession.activeAudioStreamIndex)
                        } ?: adjustedSession.audioTracks.firstOrNull { it.isDefault } ?: adjustedSession.audioTracks.firstOrNull()
                        val selectedSub = SubtitleTrackSelector.selectSubtitleTrack(
                            mode = profile.subtitleSelectionMode,
                            preferredSubtitleLang = profile.preferredSubtitleLang,
                            preferredAudioLang = profile.preferredAudioLang,
                            activeAudioLang = activeAudio?.language,
                            subtitleTracks = adjustedSession.subtitleTracks
                        )
                        if (selectedSub != null &&
                            (selectedSub.codec.equals("ass", ignoreCase = true) || selectedSub.codec.equals("ssa", ignoreCase = true))
                        ) {
                            adjustedSession.externalSubtitleFiles.firstOrNull { it.index == selectedSub.index }?.let { ext ->
                                loadAssSubtitleFile(ext)
                            }
                        } else if (profile.subtitleSelectionMode == SubtitleSelectionMode.DEFAULT) {
                            autoLoadMatchingAssTrack(adjustedSession, profile.preferredSubtitleLang)
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // A different item failed: drop the previous episode's session so the
                            // External Player button and Retry don't act on the old episode.
                            session = if (isSameItem) it.session else null,
                            errorMessage = UnifiedCatalogRepository.formatHttpError(err, "Playback initialization failed")
                        )
                    }
                }
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
            loadTrailer(trailerTitle ?: "Trailer", localTrailerId, trailerYoutubeKey)
        } else {
            val retryId = requestedItemId ?: currentSession?.itemId ?: initialItemId
            loadItem(
                itemId = retryId,
                qualityOverride = _uiState.value.selectedQuality,
                resumeOverrideMs = currentPosMs.takeIf { it > 0L && currentSession?.itemId == retryId }
            )
        }
    }

    fun onPlaybackError(errorDetail: String?, currentPosMs: Long) {
        val currentState = _uiState.value
        val session = currentState.session ?: return
        if (!session.isTrailer && !session.isHls && currentState.selectedQuality == QualityPreset.AUTO) {
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
            viewModelScope.launch {
                _uiState.update {
                    it.copy(feedbackMessage = "Direct Play unsupported — switched to HLS transcode (${fallbackQuality.label})")
                }
                kotlinx.coroutines.delay(4000L)
                _uiState.update { it.copy(feedbackMessage = null) }
            }
        } else {
            val detail = errorDetail?.takeIf { it.isNotBlank() } ?: "Stream playback error"
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = UnifiedCatalogRepository.formatHttpError(
                        IllegalStateException(detail),
                        "Playback error: $detail"
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
                loadItem(session.itemId, resumeOverrideMs = currentPosMs, burnInSubtitleOverride = null)
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
        _uiState.update {
            it.copy(
                assSubtitleMode = mode,
                activeAssCues = if (mode != AssSubtitleMode.STYLED) emptyList() else it.activeAssCues
            )
        }
        viewModelScope.launch {
            authRepository.updatePlaybackPreferences(assSubtitleMode = mode)
            if (session == null || session.isTrailer) return@launch
            applyAssSubtitleMode(session, mode, currentPosMs)
        }
    }

    private fun applyAssSubtitleMode(session: PlaybackSessionData, mode: AssSubtitleMode, currentPosMs: Long) {
        val firstAss = session.externalSubtitleFiles.firstOrNull {
            it.codec.equals("ass", ignoreCase = true) || it.codec.equals("ssa", ignoreCase = true)
        }
        when (mode) {
            AssSubtitleMode.BURN_IN -> {
                val idx = _uiState.value.activeAssSubtitleIndex ?: firstAss?.index
                if (idx != null) {
                    reportStopped(currentPosMs)
                    loadItem(session.itemId, resumeOverrideMs = currentPosMs, burnInSubtitleOverride = idx)
                }
            }
            AssSubtitleMode.STYLED -> {
                if (_uiState.value.burnInSubtitleIndex != null) {
                    reportStopped(currentPosMs)
                    loadItem(session.itemId, resumeOverrideMs = currentPosMs, burnInSubtitleOverride = null)
                } else {
                    autoLoadMatchingAssTrack(session, _uiState.value.preferredSubtitleLang)
                }
            }
            AssSubtitleMode.PLAIN -> {
                // ASS/SSA files are only side-loaded into ExoPlayer as plain text tracks when the
                // media item is built in PLAIN mode, so the item has to be reloaded for them to appear.
                if (_uiState.value.burnInSubtitleIndex != null || firstAss != null) {
                    reportStopped(currentPosMs)
                    loadItem(session.itemId, resumeOverrideMs = currentPosMs, burnInSubtitleOverride = null)
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
            externalSubtitles = session.externalSubtitleFiles
        )
        if (!launched) {
            _uiState.update { it.copy(feedbackMessage = "No compatible external video player installed") }
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
        viewModelScope.launch {
            _uiState.update { it.copy(drawerSelectedSeason = seasonNumber, drawerEpisodesLoading = true) }
            val eps = catalogRepository.loadSeasonEpisodes(
                userId = activeUserId,
                tmdbId = activeSeriesTmdbId,
                jellyfinSeriesId = seriesId,
                jellyfinSeasonId = season?.jellyfinSeasonId,
                seasonNumber = seasonNumber
            )
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

    fun selectBurnInSubtitleTrack(subtitleIndex: Int?, currentPosMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        assLoadJob?.cancel()
        _uiState.update { it.copy(activeAssCues = emptyList(), activeAssSubtitleIndex = null) }
        if (subtitleIndex == _uiState.value.burnInSubtitleIndex) return
        reportStopped(currentPosMs)
        loadItem(
            itemId = session.itemId,
            resumeOverrideMs = currentPosMs,
            burnInSubtitleOverride = subtitleIndex
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
        consecutiveAutoplayedEpisodes = 0
        _uiState.update {
            it.copy(
                showStillWatchingPrompt = false,
                pendingStillWatchingNextId = null,
                pendingStillWatchingNextTitle = null
            )
        }
        if (!nextId.isNullOrBlank()) {
            loadItem(nextId)
        }
    }

    fun dismissStillWatching() {
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
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    remoteSubtitleLanguage = language,
                    remoteSubtitlesLoading = true,
                    subtitleSearchMessage = null
                )
            }
            playbackManager.searchRemoteSubtitles(session.itemId, language)
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            remoteSubtitles = results,
                            remoteSubtitlesLoading = false,
                            subtitleSearchMessage = if (results.isEmpty()) "No remote subtitles found for $language" else null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            remoteSubtitlesLoading = false,
                            subtitleSearchMessage = "Subtitle search failed: ${err.message ?: "Unknown error"}"
                        )
                    }
                }
        }
    }

    fun downloadRemoteSubtitle(subtitleId: String, currentPosMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        val searchLang = _uiState.value.remoteSubtitleLanguage
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingSubtitleId = subtitleId, subtitleSearchMessage = null) }
            playbackManager.downloadRemoteSubtitle(session.itemId, subtitleId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            downloadingSubtitleId = null,
                            preferredSubtitleLang = searchLang,
                            subtitleSearchMessage = "Subtitle downloaded — reloading track…"
                        )
                    }
                    reportStopped(currentPosMs)
                    loadItem(session.itemId, resumeOverrideMs = currentPosMs)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            downloadingSubtitleId = null,
                            subtitleSearchMessage = "Download failed: ${err.message ?: "Unknown error"}"
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
        if (session.isTrailer) return
        val full = buildDefaultReportState(reportState)
        viewModelScope.launch {
            playbackManager.reportStart(session, positionMs * 10_000L, full)
        }
    }

    fun reportProgress(positionMs: Long, isPaused: Boolean, reportState: PlaybackReportState? = null) {
        val state = _uiState.value
        val session = state.session ?: return
        // While a new item/version is loading the old session has already been reported as stopped;
        // a late progress report would resurrect it on the server.
        if (session.isTrailer || state.isLoading || state.errorMessage != null) return
        val full = buildDefaultReportState(reportState)
        viewModelScope.launch {
            playbackManager.reportProgress(session, positionMs * 10_000L, isPaused, full)
        }
    }

    fun reportStopped(positionMs: Long, reportState: PlaybackReportState? = null) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        val full = buildDefaultReportState(reportState)
        // Not viewModelScope: this is called from the screen's onDispose right before the
        // ViewModel is cleared, which would cancel the request before it is sent.
        playbackManager.reportStoppedDetached(session, positionMs.coerceAtLeast(0L) * 10_000L, full)
    }

    private companion object {
        const val TRAILER_RESOLVE_TIMEOUT_MS = 15_000L
        const val DRAWER_PRELOAD_DELAY_MS = 6_000L
        /** Sentinel: keep the current version (same item only). Compared by identity. */
        val KEEP_SOURCE: String = String(charArrayOf('\u0000'))
        const val KEEP_BURN_IN = Int.MIN_VALUE
        const val KEEP_AUDIO_INDEX = Int.MIN_VALUE
        const val BLOCKED_FOR_PROFILE_MESSAGE = "This title isn't available on this profile."
    }
}

