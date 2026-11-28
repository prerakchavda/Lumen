package com.seerflix.tv.core.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import com.seerflix.tv.core.model.FrameRateMatchMode
import kotlin.math.abs

data class DisplayModeCandidate(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float
)

object FrameRateMatcher {
    private const val MULTIPLE_TOLERANCE_HZ = 0.25f

    /**
     * Picks the best display mode matching [videoFrameRate] at the same physical resolution
     * as [currentMode]. Returns null if [matchMode] is OFF, frame rate is invalid, or no
     * clean match is available (or if the current mode is already optimal).
     */
    fun selectBestDisplayMode(
        videoFrameRate: Float,
        currentMode: DisplayModeCandidate,
        supportedModes: List<DisplayModeCandidate>,
        matchMode: FrameRateMatchMode
    ): DisplayModeCandidate? {
        if (matchMode == FrameRateMatchMode.OFF || videoFrameRate <= 1f) return null

        val sameResolution = supportedModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }
        if (sameResolution.isEmpty()) return null

        // Already showing a clean multiple (24fps at 48/120Hz, 30fps at 60Hz): judder-free, so a
        // switch would only cost an HDMI resync blackout.
        if ((2..5).any { abs(currentMode.refreshRate - videoFrameRate * it) <= MULTIPLE_TOLERANCE_HZ }) return null

        // Check exact/near-exact 1x match first (e.g. 23.976 -> 23.976 or 24.000)
        val exactMatch = sameResolution
            .filter { abs(it.refreshRate - videoFrameRate) <= 0.12f }
            .minByOrNull { abs(it.refreshRate - videoFrameRate) }
        if (exactMatch != null) {
            return exactMatch.takeIf { it.modeId != currentMode.modeId }
        }

        // Next prefer clean integer multiples (e.g. 23.976 * 5 = 119.88Hz, 24 * 2 = 48Hz, 24 * 5 = 120Hz)
        val multipleMatch = (2..5).firstNotNullOfOrNull { multiplier ->
            val targetHz = videoFrameRate * multiplier
            sameResolution
                .filter { abs(it.refreshRate - targetHz) <= MULTIPLE_TOLERANCE_HZ }
                .minByOrNull { abs(it.refreshRate - targetHz) }
        }
        return multipleMatch?.takeIf { it.modeId != currentMode.modeId }
    }

    fun findBestAndroidDisplayMode(
        display: Display,
        videoFrameRate: Float,
        matchMode: FrameRateMatchMode
    ): Display.Mode? {
        val current = display.mode ?: return null
        val supported = display.supportedModes.orEmpty().toList()
        if (supported.isEmpty()) return null
        val bestCandidate = selectBestDisplayMode(
            videoFrameRate = videoFrameRate,
            currentMode = DisplayModeCandidate(
                modeId = current.modeId,
                physicalWidth = current.physicalWidth,
                physicalHeight = current.physicalHeight,
                refreshRate = current.refreshRate
            ),
            supportedModes = supported.map {
                DisplayModeCandidate(
                    modeId = it.modeId,
                    physicalWidth = it.physicalWidth,
                    physicalHeight = it.physicalHeight,
                    refreshRate = it.refreshRate
                )
            },
            matchMode = matchMode
        ) ?: return null
        return supported.firstOrNull { it.modeId == bestCandidate.modeId }
    }

    tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}
