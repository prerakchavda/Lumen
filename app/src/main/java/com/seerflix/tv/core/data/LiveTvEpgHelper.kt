package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.LiveTvProgram

/**
 * Pure time-grid math and channel-surfing helpers for Live TV EPG & in-player channel switching.
 */
object LiveTvEpgHelper {

    const val HALF_HOUR_MS: Long = 30L * 60_000L
    const val GRID_SLOTS: Int = 6 // 3 hours visible per page (6 × 30 min)
    const val GRID_WINDOW_MS: Long = HALF_HOUR_MS * GRID_SLOTS

    data class EpgCell(
        val program: LiveTvProgram?,
        val startMs: Long,
        val endMs: Long,
        val weightFraction: Float,
        val isCurrent: Boolean
    )

    /**
     * Floor [epochMs] down to the nearest half-hour boundary in local time (e.g. 14:22 -> 14:00,
     * 14:45 -> 14:30), so zones with a :15/:45 UTC offset still get :00/:30 slots.
     */
    fun floorToHalfHour(epochMs: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long {
        if (epochMs <= 0L) return 0L
        val offset = zone.getOffset(epochMs).toLong()
        return Math.floorDiv(epochMs + offset, HALF_HOUR_MS) * HALF_HOUR_MS - offset
    }

    /**
     * "start - end" of [program] in [timeFmt] (the guide passes one that follows the TV's
     * 12/24-hour setting and the app language); the programme's preformatted label when it has
     * no times.
     */
    fun timeRangeLabel(program: LiveTvProgram, timeFmt: java.time.format.DateTimeFormatter): String =
        if (program.startMs > 0L && program.endMs > 0L) {
            "${timeFmt.format(java.time.Instant.ofEpochMilli(program.startMs))} - " +
                timeFmt.format(java.time.Instant.ofEpochMilli(program.endMs))
        } else {
            program.timeRangeLabel
        }

    /** Returns the [GRID_SLOTS] half-hour start timestamps starting at [windowStartMs]. */
    fun timeSlots(windowStartMs: Long): List<Long> =
        (0 until GRID_SLOTS).map { windowStartMs + it * HALF_HOUR_MS }

    /**
     * Projects [channel]'s chronological programs onto the `[windowStartMs, windowStartMs + GRID_WINDOW_MS]`
     * time window, filling gaps with placeholder cells (`program == null`) so every row spans 100% width.
     */
    /**
     * What is on [channel] now and next at [nowMs]. The channel's currentProgram / nextProgram
     * are fixed when the guide loads, so a guide left open would keep showing (and recording)
     * a programme that already ended.
     */
    fun nowAndNext(channel: LiveTvChannel, nowMs: Long): Pair<LiveTvProgram?, LiveTvProgram?> {
        val current = channel.programs.firstOrNull { nowMs >= it.startMs && nowMs < it.endMs }
            ?: channel.currentProgram?.takeIf { it.endMs <= 0L || it.endMs > nowMs }
        val nextStart = current?.endMs?.takeIf { it > 0L } ?: nowMs
        val next = channel.programs.firstOrNull { it.startMs >= nextStart && it.id != current?.id }
            ?: channel.nextProgram?.takeIf { it.id != current?.id && (it.endMs <= 0L || it.endMs > nowMs) }
        return current to next
    }

    fun cellsForWindow(
        channel: LiveTvChannel,
        windowStartMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<EpgCell> {
        val windowEndMs = windowStartMs + GRID_WINDOW_MS
        val totalSpan = GRID_WINDOW_MS.toFloat()
        val source = if (channel.programs.isNotEmpty()) {
            channel.programs
        } else {
            listOfNotNull(channel.currentProgram, channel.nextProgram)
        }
        val overlapping = source
            .filter { it.endMs > windowStartMs && it.startMs < windowEndMs && it.endMs > it.startMs }
            .sortedBy { it.startMs }

        if (overlapping.isEmpty()) {
            return listOf(
                EpgCell(
                    program = channel.currentProgram?.takeIf { nowMs in windowStartMs..windowEndMs },
                    startMs = windowStartMs,
                    endMs = windowEndMs,
                    weightFraction = 1f,
                    isCurrent = nowMs in windowStartMs until windowEndMs
                )
            )
        }

        val cells = mutableListOf<EpgCell>()
        var cursor = windowStartMs
        for (prog in overlapping) {
            val clippedStart = prog.startMs.coerceAtLeast(windowStartMs)
            val clippedEnd = prog.endMs.coerceAtMost(windowEndMs)
            if (clippedEnd <= cursor) continue
            if (clippedStart > cursor) {
                val gapWeight = ((clippedStart - cursor) / totalSpan).coerceAtLeast(0.04f)
                cells += EpgCell(
                    program = null,
                    startMs = cursor,
                    endMs = clippedStart,
                    weightFraction = gapWeight,
                    isCurrent = false
                )
            }
            val effStart = clippedStart.coerceAtLeast(cursor)
            val progWeight = ((clippedEnd - effStart) / totalSpan).coerceAtLeast(0.06f)
            cells += EpgCell(
                program = prog,
                startMs = effStart,
                endMs = clippedEnd,
                weightFraction = progWeight,
                isCurrent = nowMs in prog.startMs until prog.endMs
            )
            cursor = clippedEnd
        }
        if (cursor < windowEndMs) {
            val tailWeight = ((windowEndMs - cursor) / totalSpan).coerceAtLeast(0.04f)
            cells += EpgCell(
                program = null,
                startMs = cursor,
                endMs = windowEndMs,
                weightFraction = tailWeight,
                isCurrent = false
            )
        }
        return cells
    }

    /**
     * Wraps channel surfing by [delta] (+1 for channel up/next, -1 for channel down/previous).
     * Returns `null` if [channels] is empty or has only the current channel.
     */
    fun nextChannelId(
        channels: List<LiveTvChannel>,
        currentChannelId: String?,
        delta: Int
    ): LiveTvChannel? {
        if (channels.size <= 1) return null
        val curIndex = channels.indexOfFirst { it.id == currentChannelId }
        val targetIndex = if (curIndex < 0) {
            if (delta >= 0) 0 else channels.lastIndex
        } else {
            ((curIndex + delta) % channels.size + channels.size) % channels.size
        }
        return channels[targetIndex]
    }
}
