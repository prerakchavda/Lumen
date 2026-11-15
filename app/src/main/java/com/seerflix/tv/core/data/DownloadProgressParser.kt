package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.network.SeerrDownloadStatusDto
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

object DownloadProgressParser {

    fun parse(
        downloads: List<SeerrDownloadStatusDto>,
        is4k: Boolean,
        now: Instant = Instant.now()
    ): DownloadProgressInfo? {
        if (downloads.isEmpty()) return null

        val primary = downloads.first()
        val totalSize = downloads.sumOf { it.size.coerceAtLeast(0L) }
        val totalLeft = downloads.sumOf { it.sizeLeft.coerceAtLeast(0L) }

        val percentage = if (totalSize > 0L) {
            (((totalSize - totalLeft).toDouble() / totalSize.toDouble()) * 100.0)
                .toFloat()
                .coerceIn(0f, 100f)
        } else {
            0f
        }

        val etaText = formatEta(
            estimatedCompletionIso = primary.estimatedCompletionTime,
            rawTimeLeft = primary.timeLeft,
            now = now
        )

        return DownloadProgressInfo(
            is4k = is4k,
            status = primary.status ?: "downloading",
            title = primary.title,
            sizeBytes = totalSize,
            sizeLeftBytes = totalLeft,
            percentage = percentage,
            etaText = etaText,
            estimatedCompletionIso = primary.estimatedCompletionTime
        )
    }

    fun formatEta(
        estimatedCompletionIso: String?,
        rawTimeLeft: String?,
        now: Instant = Instant.now()
    ): String? {
        if (!estimatedCompletionIso.isNullOrBlank()) {
            val target = runCatching { Instant.parse(estimatedCompletionIso) }
                .recoverCatching { OffsetDateTime.parse(estimatedCompletionIso).toInstant() }
                .getOrNull()
            if (target != null) {
                val seconds = Duration.between(now, target).seconds
                if (seconds > 0) {
                    return formatSeconds(seconds)
                }
            }
        }
        if (!rawTimeLeft.isNullOrBlank()) {
            val seconds = parseTimeLeftSeconds(rawTimeLeft)
            if (seconds != null && seconds > 0) {
                return formatSeconds(seconds)
            }
        }
        return null
    }

    internal fun parseTimeLeftSeconds(raw: String): Long? {
        // Formats seen from Radarr/Sonarr/Seerr (.NET TimeSpan): "00:05:42", "01:23:10.500",
        // "1.02:15:00" (days.hours), "1.02:15:00.250".
        val parts = raw.trim().split(":")
        if (parts.size != 3) return null
        // Fractional seconds only ever follow the last colon.
        val seconds = parts[2].substringBefore(".").toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val head = parts[0]
        val days = if ('.' in head) head.substringBefore('.').toLongOrNull() ?: return null else 0L
        val hours = head.substringAfter('.').toLongOrNull() ?: return null
        return days * 86400L + hours * 3600L + minutes * 60L + seconds
    }

    private fun formatSeconds(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return when {
            totalSeconds < 60 -> "< 1m left"
            hours >= 24 -> "~${hours / 24}d ${hours % 24}h left"
            hours > 0 -> if (remMinutes > 0) "~${hours}h ${remMinutes}m left" else "~${hours}h left"
            else -> "~${minutes}m left"
        }
    }
}
