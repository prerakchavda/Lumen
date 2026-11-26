package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.SubtitleSelectionMode
import com.seerflix.tv.core.model.TrackOption

/**
 * Pure subtitle auto-selection rules matching Jellyfin's subtitle modes (`Default`, `Smart`,
 * `OnlyForced`, `Always`, `None`) plus ISO 639-1 / 639-2 language normalization.
 */
object SubtitleTrackSelector {

    /**
     * Maps Jellyfin's `UserConfiguration.SubtitleMode` string to [SubtitleSelectionMode].
     */
    fun fromJellyfinSubtitleMode(raw: String?): SubtitleSelectionMode = when (raw?.trim()?.lowercase()) {
        "smart" -> SubtitleSelectionMode.SMART
        "onlyforced", "only_forced" -> SubtitleSelectionMode.ONLY_FORCED
        "always" -> SubtitleSelectionMode.ALWAYS
        "none", "nosubtitles" -> SubtitleSelectionMode.NONE
        else -> SubtitleSelectionMode.DEFAULT
    }

    /**
     * Normalizes ISO 639-1 / 639-2B / 639-2T / BCP-47 tags (e.g., `"en"`, `"en-US"`, `"eng"`)
     * to a canonical 3-letter lowercase key so Jellyfin metadata and Media3 track formats match.
     */
    fun normalizeLanguage(raw: String?): String? {
        val base = raw?.trim()?.lowercase()
            ?.substringBefore('-')
            ?.substringBefore('_')
            ?.takeIf { it.isNotEmpty() && it != "und" && it != "off" }
            ?: return null
        return LANG_ALIASES[base] ?: base
    }

    fun languagesMatch(a: String?, b: String?): Boolean {
        val na = normalizeLanguage(a) ?: return false
        val nb = normalizeLanguage(b) ?: return false
        return na == nb
    }

    /**
     * Selects the initial subtitle track (or `null` for subtitles off) given the profile's
     * [mode], preferred languages, active audio language, and available [subtitleTracks].
     */
    fun selectSubtitleTrack(
        mode: SubtitleSelectionMode,
        preferredSubtitleLang: String?,
        preferredAudioLang: String?,
        activeAudioLang: String?,
        subtitleTracks: List<TrackOption>
    ): TrackOption? {
        if (subtitleTracks.isEmpty()) return null
        if (mode == SubtitleSelectionMode.NONE) return null

        val prefSub = preferredSubtitleLang?.takeIf { it.isNotBlank() && !it.equals("off", ignoreCase = true) }
        val explicitlyOff = preferredSubtitleLang.equals("off", ignoreCase = true)
        if (explicitlyOff && mode != SubtitleSelectionMode.ALWAYS) return null

        return when (mode) {
            SubtitleSelectionMode.NONE -> null
            SubtitleSelectionMode.ONLY_FORCED -> selectForced(subtitleTracks, prefSub, activeAudioLang)
            SubtitleSelectionMode.SMART -> {
                val targetAudio = preferredAudioLang?.takeIf { it.isNotBlank() } ?: prefSub
                val subLang = prefSub ?: targetAudio
                val isForeignAudio = !activeAudioLang.isNullOrBlank() &&
                    !targetAudio.isNullOrBlank() &&
                    !languagesMatch(activeAudioLang, targetAudio)
                if (isForeignAudio) {
                    selectFullSubtitle(subtitleTracks, subLang)
                } else {
                    selectForced(subtitleTracks, subLang, activeAudioLang)
                }
            }
            SubtitleSelectionMode.ALWAYS -> {
                selectFullSubtitle(subtitleTracks, prefSub)
                    ?: subtitleTracks.firstOrNull { it.isDefault }
                    ?: subtitleTracks.firstOrNull()
            }
            SubtitleSelectionMode.DEFAULT -> {
                if (prefSub != null) {
                    subtitleTracks.firstOrNull { it.isDefault && languagesMatch(it.language, prefSub) }
                        ?: subtitleTracks.firstOrNull { it.isForced && languagesMatch(it.language, prefSub) }
                        ?: selectForced(subtitleTracks, null, activeAudioLang)
                } else {
                    subtitleTracks.firstOrNull { it.isDefault }
                        ?: selectForced(subtitleTracks, null, activeAudioLang)
                }
            }
        }
    }

    private fun selectForced(
        tracks: List<TrackOption>,
        preferredSubLang: String?,
        activeAudioLang: String?
    ): TrackOption? {
        val forced = tracks.filter { it.isForced }
        if (forced.isEmpty()) return null
        if (!preferredSubLang.isNullOrBlank()) {
            forced.firstOrNull { languagesMatch(it.language, preferredSubLang) }?.let { return it }
        }
        if (!activeAudioLang.isNullOrBlank()) {
            forced.firstOrNull { languagesMatch(it.language, activeAudioLang) }?.let { return it }
        }
        return if (preferredSubLang.isNullOrBlank() && activeAudioLang.isNullOrBlank()) {
            forced.firstOrNull()
        } else {
            null
        }
    }

    private fun selectFullSubtitle(
        tracks: List<TrackOption>,
        lang: String?
    ): TrackOption? {
        val matching = if (!lang.isNullOrBlank()) {
            tracks.filter { languagesMatch(it.language, lang) }
        } else {
            tracks
        }
        if (matching.isEmpty()) return null
        // Prefer standard (non-forced, non-SDH) dialogue tracks first, then default, then any match.
        return matching.firstOrNull { !it.isForced && !it.isHearingImpaired && it.isDefault }
            ?: matching.firstOrNull { !it.isForced && !it.isHearingImpaired }
            ?: matching.firstOrNull { !it.isForced }
            ?: matching.firstOrNull()
    }

    private val LANG_ALIASES = mapOf(
        "en" to "eng", "eng" to "eng",
        "es" to "spa", "spa" to "spa",
        "fr" to "fra", "fre" to "fra", "fra" to "fra",
        "de" to "deu", "ger" to "deu", "deu" to "deu",
        "ja" to "jpn", "jpn" to "jpn",
        "it" to "ita", "ita" to "ita",
        "pt" to "por", "por" to "por",
        "ko" to "kor", "kor" to "kor",
        "zh" to "zho", "chi" to "zho", "zho" to "zho",
        "ru" to "rus", "rus" to "rus",
        "nl" to "nld", "dut" to "nld", "nld" to "nld",
        "pl" to "pol", "pol" to "pol",
        "sv" to "swe", "swe" to "swe",
        "ar" to "ara", "ara" to "ara",
        "hi" to "hin", "hin" to "hin",
        "tr" to "tur", "tur" to "tur",
        "no" to "nor", "nb" to "nor", "nn" to "nor", "nob" to "nor", "nno" to "nor", "nor" to "nor",
        "da" to "dan", "dan" to "dan",
        "fi" to "fin", "fin" to "fin",
        "cs" to "ces", "cze" to "ces", "ces" to "ces",
        "he" to "heb", "iw" to "heb", "heb" to "heb",
        "th" to "tha", "tha" to "tha",
        "vi" to "vie", "vie" to "vie",
        "id" to "ind", "in" to "ind", "ind" to "ind",
        "uk" to "ukr", "ukr" to "ukr",
        "el" to "ell", "gre" to "ell", "ell" to "ell",
        "ro" to "ron", "rum" to "ron", "ron" to "ron",
        "hu" to "hun", "hun" to "hun"
    )
}
