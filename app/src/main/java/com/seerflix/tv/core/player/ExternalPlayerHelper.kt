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
        packageName: String? = null
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
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
            packageName = installedPackage
        )
        return runCatching {
            val chooser = if (installedPackage == null) {
                Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                intent
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
