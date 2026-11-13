package com.seerflix.tv.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.FrameRateMatchMode
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.QualityPreset
import com.seerflix.tv.core.model.RemoteSubtitleOption
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.player.YouTubeTrailerResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val session: PlaybackSessionData? = null,
    val selectedSpeed: Float = 1.0f,
    val selectedQuality: QualityPreset = QualityPreset.AUTO,
    val subtitleFontScale: Float = 1.0f,
    val subtitleBackgroundAlpha: Float = 0.6f,
    val subtitleOffsetMs: Long = 0L,
    val autoSkipIntro: Boolean = false,
    val autoSkipRecap: Boolean = false,
    val frameRateMatchMode: FrameRateMatchMode = FrameRateMatchMode.OFF,
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val playbackManager: JellyfinPlaybackManager,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver
) : ViewModel() {

    private val initialItemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val trailerYoutubeKey: String? = savedStateHandle.get<String>("youtubeKey")?.takeIf { it.isNotBlank() && it != "none" }
    private val localTrailerId: String? = savedStateHandle.get<String>("localTrailerId")?.takeIf { it.isNotBlank() && it != "none" }
    private val trailerTitle: String? = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var activeUserId: String = ""
    private var activeSeriesTmdbId: Int? = null

    init {
        if (!trailerYoutubeKey.isNullOrBlank() || !localTrailerId.isNullOrBlank()) {
            loadTrailer(trailerTitle ?: "Trailer", localTrailerId, trailerYoutubeKey)
        } else {
            loadItem(initialItemId)
        }
    }

    fun loadTrailer(title: String, localTrailerId: String?, youtubeKey: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, subtitleOffsetMs = 0L) }
            val resolved = trailerResolver.resolvePreview(
                localTrailerId = localTrailerId,
                youtubeKey = youtubeKey
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
        resumeOverrideMs: Long? = null
    ) {
        if (itemId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid media item ID") }
            return
        }
        val isSameItem = _uiState.value.session?.itemId == itemId
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    autoplayCancelled = false,
                    subtitleOffsetMs = if (isSameItem) it.subtitleOffsetMs else 0L,
                    remoteSubtitles = if (isSameItem) it.remoteSubtitles else emptyList(),
                    subtitleSearchMessage = if (isSameItem) it.subtitleSearchMessage else null
                )
            }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null || profile.jellyfinUserId.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active Jellyfin profile") }
                return@launch
            }
            activeUserId = profile.jellyfinUserId
            _uiState.update {
                it.copy(
                    autoplayNextEpisode = profile.autoplayNextEpisode,
                    subtitleFontScale = profile.subtitleFontScale,
                    subtitleBackgroundAlpha = profile.subtitleBackgroundAlpha,
                    autoSkipIntro = profile.autoSkipIntro,
                    autoSkipRecap = profile.autoSkipRecap,
                    frameRateMatchMode = profile.frameRateMatchMode,
                    preferredAudioLang = profile.preferredAudioLang,
                    preferredSubtitleLang = profile.preferredSubtitleLang
                )
            }

            playbackManager.preparePlayback(
                userId = activeUserId,
                itemId = itemId,
                maxBitrateBps = qualityOverride.maxBitrateBps
            )
                .onSuccess { session ->
                    val adjustedSession = if (resumeOverrideMs != null) {
                        session.copy(resumeTicks = resumeOverrideMs * 10_000L)
                    } else session
                    _uiState.update {
                        it.copy(
                            session = adjustedSession,
                            selectedQuality = qualityOverride,
                            isLoading = false
                        )
                    }
                    if (!adjustedSession.seriesId.isNullOrBlank()) {
                        loadSeriesForDrawer(adjustedSession.seriesId, adjustedSession.seasonId)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Playback initialization failed")
                    }
                }
        }
    }

    private fun loadSeriesForDrawer(seriesId: String, currentSeasonId: String?) {
        viewModelScope.launch {
            val details = catalogRepository.loadFullDetails(
                userId = activeUserId,
                tmdbId = null,
                jellyfinId = seriesId,
                mediaType = MediaType.SERIES
            ).getOrNull() ?: return@launch
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
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        viewModelScope.launch {
            playbackManager.reportProgress(session, positionMs * 10_000L, isPaused)
        }
    }

    fun reportStopped(positionMs: Long) {
        val session = _uiState.value.session ?: return
        if (session.isTrailer) return
        viewModelScope.launch {
            playbackManager.reportStopped(session, positionMs * 10_000L)
        }
    }
}

