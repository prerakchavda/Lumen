package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.PlaybackSessionData

/**
 * Pure state machine for optional Jellyfin Cinema Intros:
 * plays pre-roll clips before starting a movie from the beginning without polluting resume/watched state.
 */
class CinemaIntroQueue {

    data class PendingMainItem(
        val itemId: String,
        val mediaSourceId: String?,
        val audioStreamIndex: Int?,
        val subtitleStreamIndex: Int?,
        val fromSyncPlay: Boolean
    )

    sealed class NextStep {
        data class PlayIntro(val introItemId: String, val index: Int, val total: Int) : NextStep()
        data class PlayMain(val main: PendingMainItem) : NextStep()
        data object None : NextStep()
    }

    private val introQueue = ArrayDeque<String>()
    private var totalIntros: Int = 0
    private var currentIntroIndex: Int = 0
    private var pendingMain: PendingMainItem? = null

    val hasPendingMain: Boolean get() = pendingMain != null

    /**
     * Cinema intros only run for movies starting from the beginning (not resuming, not episodes,
     * not Live TV, not trailers, not SyncPlay, not already inside an intro clip).
     */
    fun shouldQueryIntros(
        cinemaIntrosEnabled: Boolean,
        session: PlaybackSessionData,
        resumeOverrideMs: Long?,
        fromSyncPlay: Boolean,
        skipCinemaIntros: Boolean,
        isCinemaIntroClip: Boolean
    ): Boolean {
        if (!cinemaIntrosEnabled || skipCinemaIntros || isCinemaIntroClip || fromSyncPlay) return false
        if (session.isLiveTv || session.isTrailer || !session.seriesId.isNullOrBlank()) return false
        val effectiveResumeTicks = resumeOverrideMs?.let { it * 10_000L } ?: session.resumeTicks
        return effectiveResumeTicks <= 0L
    }

    /** Arms [introIds] ahead of [main] and returns the first intro step (or [NextStep.None] if empty). */
    fun arm(introIds: List<String>, main: PendingMainItem): NextStep {
        clear()
        val valid = introIds.filter { it.isNotBlank() }
        if (valid.isEmpty()) return NextStep.None
        introQueue.addAll(valid)
        totalIntros = valid.size
        pendingMain = main
        return advance()
    }

    /** Pops the next intro clip, or hands back the main movie once all intros are done. */
    fun advance(): NextStep {
        val nextIntro = introQueue.removeFirstOrNull()
        if (nextIntro != null) {
            currentIntroIndex++
            return NextStep.PlayIntro(nextIntro, currentIntroIndex, totalIntros)
        }
        val main = pendingMain ?: return NextStep.None
        clear()
        return NextStep.PlayMain(main)
    }

    /** Immediately skips all remaining cinema intros and transitions to the main movie. */
    fun skipToMain(): NextStep {
        val main = pendingMain ?: return NextStep.None
        clear()
        return NextStep.PlayMain(main)
    }

    fun clear() {
        introQueue.clear()
        totalIntros = 0
        currentIntroIndex = 0
        pendingMain = null
    }
}
