package com.seerflix.tv.core.player

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Immutable
data class QueueEntry(
    val itemId: String,
    val title: String? = null,
    val mediaSourceId: String? = null,
    val startPositionMs: Long = 0L
)

@Immutable
data class PlaybackQueueState(
    val items: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffled: Boolean = false
) {
    val isActive: Boolean get() = items.isNotEmpty() && currentIndex in items.indices
    val current: QueueEntry? get() = items.getOrNull(currentIndex)
    val hasNext: Boolean get() = isActive && currentIndex + 1 < items.size
    val hasPrevious: Boolean get() = isActive && currentIndex > 0
    val nextEntry: QueueEntry? get() = if (hasNext) items[currentIndex + 1] else null
    val previousEntry: QueueEntry? get() = if (hasPrevious) items[currentIndex - 1] else null
    val positionLabel: String?
        get() = if (items.size > 1 && currentIndex in items.indices) {
            "${currentIndex + 1} / ${items.size}"
        } else null
}

/**
 * Shared playback queue for Play All, Shuffle (Series, Seasons, Collections, Playlists), and
 * multi-item remote control commands (`PlayNow`, `PlayNext`, `NextTrack`, `PreviousTrack`).
 */
@Singleton
class PlaybackQueueManager @Inject constructor() {

    private val _state = MutableStateFlow(PlaybackQueueState())
    val state: StateFlow<PlaybackQueueState> = _state.asStateFlow()

    /**
     * What the open player is playing (set by the player, null while no player is open), so a
     * remote `PlayNext` / `PlayLast` without an active queue can keep it as the current entry.
     */
    @Volatile
    var nowPlaying: QueueEntry? = null

    @Synchronized
    fun setQueue(
        entries: List<QueueEntry>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        random: Random = Random.Default
    ): QueueEntry? {
        if (entries.isEmpty()) {
            _state.value = PlaybackQueueState()
            return null
        }
        val ordered = if (shuffle && entries.size > 1) {
            entries.shuffled(random)
        } else {
            entries
        }
        val index = if (shuffle) 0 else startIndex.coerceIn(0, ordered.lastIndex)
        _state.value = PlaybackQueueState(
            items = ordered,
            currentIndex = index,
            isShuffled = shuffle
        )
        return ordered[index]
    }

    /**
     * Inserts [entries] right after the currently playing item (remote `PlayNext` command).
     * Without an active queue the queue becomes [current] followed by [entries], with [current]
     * (the item already playing) as the current entry; with no [current] the first entry is current.
     */
    @Synchronized
    fun enqueueNext(entries: List<QueueEntry>, current: QueueEntry? = null) {
        if (entries.isEmpty()) return
        val cur = _state.value
        if (!cur.isActive) {
            startQueueWith(current, entries)
            return
        }
        val insertAt = (cur.currentIndex + 1).coerceAtMost(cur.items.size)
        val updated = cur.items.toMutableList().apply { addAll(insertAt, entries) }
        _state.value = cur.copy(items = updated)
    }

    /** Appends [entries] to the end of the current queue (remote `PlayLast` command). See [enqueueNext]. */
    @Synchronized
    fun enqueueLast(entries: List<QueueEntry>, current: QueueEntry? = null) {
        if (entries.isEmpty()) return
        val cur = _state.value
        if (!cur.isActive) {
            startQueueWith(current, entries)
            return
        }
        _state.value = cur.copy(items = cur.items + entries)
    }

    private fun startQueueWith(current: QueueEntry?, entries: List<QueueEntry>) {
        val items = if (current != null) listOf(current) + entries else entries
        setQueue(items, startIndex = 0, shuffle = false)
    }

    /** Advances to the next entry and returns it, or null if at the end of the queue. */
    @Synchronized
    fun advanceNext(): QueueEntry? {
        val cur = _state.value
        if (!cur.hasNext) return null
        val nextIdx = cur.currentIndex + 1
        _state.value = cur.copy(currentIndex = nextIdx)
        return cur.items[nextIdx]
    }

    /** Steps back to the previous entry and returns it, or null if at the start of the queue. */
    @Synchronized
    fun stepPrevious(): QueueEntry? {
        val cur = _state.value
        if (!cur.hasPrevious) return null
        val prevIdx = cur.currentIndex - 1
        _state.value = cur.copy(currentIndex = prevIdx)
        return cur.items[prevIdx]
    }

    /** Syncs the queue cursor if an item in the queue was loaded directly (or clears if unrelated). */
    @Synchronized
    fun syncToItem(itemId: String) {
        val cur = _state.value
        if (!cur.isActive) return
        if (cur.current?.itemId == itemId) return
        val idx = cur.items.indexOfFirst { it.itemId == itemId }
        if (idx >= 0) {
            _state.value = cur.copy(currentIndex = idx)
        } else {
            _state.value = PlaybackQueueState()
        }
    }

    @Synchronized
    fun clear() {
        _state.value = PlaybackQueueState()
    }
}
