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
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.QualityPreset
import com.seerflix.tv.core.model.RemoteSubtitleOption
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.SyncPlayGroup
import com.seerflix.tv.core.network.MediaClient
import com.seerflix.tv.core.player.AssCue
import com.seerflix.tv.core.player.AssSubtitleParser
import com.seerflix.tv.core.player.ExternalPlayerHelper
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.player.NextEpisodeInfo
import com.seerflix.tv.core.player.PlaybackSegments
import com.seerflix.tv.core.player.SyncPlayManager
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

data class PlayerUiState(
    val session: PlaybackSessionData? = null,
    val selectedSpeed: Float = 1.0f,
    val selectedQuality: QualityPreset = QualityPreset.AUTO,
    val selectedMediaSourceId: String? = null,
    val subtitleFontScale: Float = 1.0f,
    val subtitleBackgroundAlpha: Float = 0.6f,
    val subtitleOffsetMs: Long = 0L,
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
    // Honours the per-server self-signed certificate toggle; also handed to ExoPlayer so
    // streams and side-loaded subtitles work against homelab HTTPS servers.
    @MediaClient val mediaHttpClient: OkHttpClient
) : ViewModel() {

    private val initialItemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val initialMediaSourceId: String? = savedStateHandle.get<String>("mediaSourceId")?.takeIf { it.isNotBlank() }
    private val trailerYoutubeKey: String? = savedStateHandle.get<String>("youtubeKey")?.takeIf { it.isNotBlank() && it != "none" }
    private val localTrailerId: String? = savedStateHandle.get<String>("localTrailerId")?.takeIf { it.isNotBlank() && it != "none" }
    private val trailerTitle: String? = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var activeUserId: String = ""
    private var activeSeriesTmdbId: Int? = null

    /** The in-flight item/trailer load; a newer load cancels it so a stale result can't win. */
    private var loadJob: Job? = null
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
            loadItem(initialItemId, mediaSourceIdOverride = initialMediaSourceId)
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
        mediaSourceIdOverride: String? = _uiState.value.selectedMediaSourceId,
        burnInSubtitleOverride: Int? = _uiState.value.burnInSubtitleIndex
    ) {
        if (itemId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid media item ID") }
            return
        }
        val isSameItem = _uiState.value.session?.itemId == itemId
        if (!isSameItem) assLoadJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    feedbackMessage = null,
                    autoplayCancelled = false,
                    subtitleOffsetMs = if (isSameItem) it.subtitleOffsetMs else 0L,
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
            val resolvedSourceId = if (isSameItem) mediaSourceIdOverride else (mediaSourceIdOverride ?: initialMediaSourceId?.takeIf { itemId == initialItemId })
            _uiState.update {
                it.copy(
                    autoplayNextEpisode = profile.autoplayNextEpisode,
                    subtitleFontScale = profile.subtitleFontScale,
                    subtitleBackgroundAlpha = profile.subtitleBackgroundAlpha,
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
                maxBitrateBps = qualityOverride.maxBitrateBps,
                mediaSourceId = resolvedSourceId,
                audioPassthroughEnabled = profile.audioPassthroughEnabled,
                stereoDownmixEnabled = profile.stereoDownmixEnabled,
                assSubtitleMode = profile.assSubtitleMode,
                burnInSubtitleIndex = burnInSubtitleOverride
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
                            selectedQuality = qualityOverride,
                            selectedMediaSourceId = adjustedSession.mediaSourceId,
                            burnInSubtitleIndex = burnInSubtitleOverride,
                            videoFrameRateHint = prepared.videoFrameRate,
                            isLoading = false
                        )
                    }
                    val playSessionId = adjustedSession.playSessionId
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
                    if (profile.assSubtitleMode == AssSubtitleMode.STYLED && profile.preferredSubtitleLang != "off") {
                        autoLoadMatchingAssTrack(adjustedSession, profile.preferredSubtitleLang)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
            loadItem(
                itemId = currentSession?.itemId ?: initialItemId,
                qualityOverride = _uiState.value.selectedQuality,
                resumeOverrideMs = currentPosMs.takeIf { it > 0L && currentSession != null },
                mediaSourceIdOverride = _uiState.value.selectedMediaSourceId ?: initialMediaSourceId
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
        val preferredMatch = preferredLang?.takeIf { it.isNotBlank() }?.let { lang ->
            assFiles.firstOrNull { it.language?.equals(lang, ignoreCase = true) == true }
        } ?: assFiles.firstOrNull() ?: return
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
        }
        if (session == null || session.isTrailer) return
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
        reportStopped(currentPosMs)
        loadItem(
            itemId = session.itemId,
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

    fun notifySyncPlayPause() {
        viewModelScope.launch { syncPlayManager.notifyPause() }
    }

    fun notifySyncPlayUnpause() {
        viewModelScope.launch { syncPlayManager.notifyUnpause() }
    }

    fun notifySyncPlaySeek(positionMs: Long) {
        viewModelScope.launch { syncPlayManager.notifySeek(positionMs.coerceAtLeast(0L) * 10_000L) }
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

    fun toggleStreamInfo() {
        _uiState.update { it.copy(showStreamInfo = !it.showStreamInfo) }
    }

    fun cancelAutoplayNext() {
        _uiState.update { it.copy(autoplayCancelled = true) }
    }

    fun reportStart(positionMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        viewModelScope.launch {
            playbackManager.reportStart(session, positionMs * 10_000L)
        }
    }

    fun reportProgress(positionMs: Long, isPaused: Boolean) {
        val state = _uiState.value
        val session = state.session ?: return
        // While a new item/version is loading the old session has already been reported as stopped;
        // a late progress report would resurrect it on the server.
        if (session.isTrailer || state.isLoading || state.errorMessage != null) return
        viewModelScope.launch {
            playbackManager.reportProgress(session, positionMs * 10_000L, isPaused)
        }
    }

    fun reportStopped(positionMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        // Not viewModelScope: this is called from the screen's onDispose right before the
        // ViewModel is cleared, which would cancel the request before it is sent.
        playbackManager.reportStoppedDetached(session, positionMs.coerceAtLeast(0L) * 10_000L)
    }

    private companion object {
        const val TRAILER_RESOLVE_TIMEOUT_MS = 15_000L
        const val DRAWER_PRELOAD_DELAY_MS = 6_000L
    }
}

