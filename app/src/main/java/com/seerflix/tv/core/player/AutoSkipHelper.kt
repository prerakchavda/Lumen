package com.seerflix.tv.core.player

enum class SkipSegmentType(val label: String) {
    RECAP("Recap"),
    INTRO("Intro")
}

object AutoSkipHelper {
    /**
     * Evaluates whether an intro/recap segment should auto-skip right now.
     * Prevents skip-loops if the user already auto-skipped this segment for [itemId]
     * (e.g. if they manually undo or rewind back into the segment).
     */
    fun shouldAutoSkip(
        enabled: Boolean,
        isTrailer: Boolean,
        itemId: String?,
        currentTicks: Long,
        startTicks: Long?,
        endTicks: Long?,
        alreadySkippedItemId: String?
    ): Boolean {
        if (!enabled || isTrailer || itemId.isNullOrBlank()) return false
        if (alreadySkippedItemId == itemId) return false
        if (startTicks == null || endTicks == null || endTicks <= startTicks) return false
        return currentTicks in startTicks until endTicks
    }
}
