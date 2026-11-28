package com.seerflix.tv.core.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.seerflix.tv.core.model.ExternalSubtitleFile

object ExternalPlayerHelper {
    private val KNOWN_TV_PLAYER_PACKAGES = listOf(
        "org.videolan.vlc",
        "org.xbmc.kodi",
        "com.brouken.player",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "com.archos.mediacenter.videofree"
    )

    fun buildExternalPlayerIntent(
        streamUrl: String,
        title: String,
        positionMs: Long = 0L,
        externalSubtitles: List<ExternalSubtitleFile> = emptyList(),
        packageName: String? = null,
        /**
         * Started for a result (the player returns its position when it closes): no new task,
         * which would make Android cancel the result right away, and MX Player's opt-in extra.
         */
        forResult: Boolean = false
    ): Intent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(streamUrl), "video/*")
            putExtra("title", title)
            if (positionMs > 0L) {
                putExtra("position", positionMs.toInt())
            }
            if (externalSubtitles.isNotEmpty()) {
                val uris = externalSubtitles.map { Uri.parse(it.url) }.toTypedArray()
                val names = externalSubtitles.map { it.title }.toTypedArray()
                putExtra("subs", uris)
                putExtra("subs.name", names)
                putExtra("subs.filename", names)
                putExtra("subtitles_location", externalSubtitles.first().url)
            }
            if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
            if (forResult) {
                putExtra("return_result", true)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return intent
    }

    /**
     * The intent that opens [streamUrl] in an installed TV player, or the app chooser when none of
     * the known ones is installed. See [buildExternalPlayerIntent] for [forResult].
     */
    fun buildLaunchIntent(
        context: Context,
        streamUrl: String,
        title: String,
        positionMs: Long = 0L,
        externalSubtitles: List<ExternalSubtitleFile> = emptyList(),
        chooserTitle: String = "Open with External Player",
        forResult: Boolean = false
    ): Intent {
        val pm = context.packageManager
        val installedPackage = KNOWN_TV_PLAYER_PACKAGES.firstOrNull { pkg ->
            val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(streamUrl), "video/*").setPackage(pkg)
            probe.resolveActivity(pm) != null
        }
        val intent = buildExternalPlayerIntent(
            streamUrl = streamUrl,
            title = title,
            positionMs = positionMs,
            externalSubtitles = externalSubtitles,
            packageName = installedPackage,
            forResult = forResult
        )
        if (installedPackage != null) return intent
        val chooser = Intent.createChooser(intent, chooserTitle)
        return if (forResult) chooser else chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * The playback position an external player returned when it closed, in ms: MX Player and Just
     * Player send "position" (and "end_by" = "playback_completion" when played to the end), VLC
     * "extra_position". Null when there is none (Kodi returns nothing) or it is 0 / unknown, so an
     * ambiguous answer never resets the resume point.
     */
    fun resultPositionMs(data: Intent?, runtimeMs: Long = 0L): Long? {
        val extras = data?.extras ?: return null
        // Players send either an int or a long (Bundle.get is the only untyped read).
        @Suppress("DEPRECATION")
        fun longExtra(key: String): Long? = when (val value = extras.get(key)) {
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }
        if (extras.getString("end_by") == "playback_completion") {
            return (longExtra("duration") ?: runtimeMs).takeIf { it > 0L }
        }
        return (longExtra("position") ?: longExtra("extra_position"))?.takeIf { it > 0L }
    }

    fun launchExternalPlayer(
        context: Context,
        streamUrl: String,
        title: String,
        positionMs: Long = 0L,
        externalSubtitles: List<ExternalSubtitleFile> = emptyList(),
        /** Title of the app chooser shown when no known TV player is installed (UiStrings.playerOpenWithExternal). */
        chooserTitle: String = "Open with External Player"
    ): Boolean {
        return runCatching {
            context.startActivity(
                buildLaunchIntent(context, streamUrl, title, positionMs, externalSubtitles, chooserTitle)
            )
            true
        }.getOrDefault(false)
    }
}
