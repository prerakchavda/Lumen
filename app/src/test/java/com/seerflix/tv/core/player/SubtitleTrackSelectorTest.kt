package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.SubtitleSelectionMode
import com.seerflix.tv.core.model.TrackOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTrackSelectorTest {

    private val enFull = TrackOption(index = 2, label = "English", language = "eng", codec = "srt", isDefault = false, isForced = false)
    private val enForced = TrackOption(index = 3, label = "English (Forced)", language = "eng", codec = "srt", isDefault = false, isForced = true)
    private val enSdh = TrackOption(index = 4, label = "English (SDH)", language = "eng", codec = "srt", isDefault = false, isForced = false, isHearingImpaired = true)
    private val esFull = TrackOption(index = 5, label = "Spanish", language = "spa", codec = "srt", isDefault = true, isForced = false)
    private val jaForced = TrackOption(index = 6, label = "Japanese (Forced)", language = "jpn", codec = "pgs", isDefault = false, isForced = true)

    @Test
    fun `normalizeLanguage maps ISO 639-1 and 639-2B to canonical ISO 639-2T`() {
        assertEquals("eng", SubtitleTrackSelector.normalizeLanguage("en"))
        assertEquals("eng", SubtitleTrackSelector.normalizeLanguage("en-US"))
        assertEquals("eng", SubtitleTrackSelector.normalizeLanguage("ENG"))
        assertEquals("fra", SubtitleTrackSelector.normalizeLanguage("fre"))
        assertEquals("fra", SubtitleTrackSelector.normalizeLanguage("fr_CA"))
        assertEquals("deu", SubtitleTrackSelector.normalizeLanguage("ger"))
        assertEquals("jpn", SubtitleTrackSelector.normalizeLanguage("ja"))
        assertNull(SubtitleTrackSelector.normalizeLanguage("und"))
        assertNull(SubtitleTrackSelector.normalizeLanguage("off"))
    }

    @Test
    fun `fromJellyfinSubtitleMode parses Jellyfin server strings`() {
        assertEquals(SubtitleSelectionMode.SMART, SubtitleTrackSelector.fromJellyfinSubtitleMode("Smart"))
        assertEquals(SubtitleSelectionMode.ONLY_FORCED, SubtitleTrackSelector.fromJellyfinSubtitleMode("OnlyForced"))
        assertEquals(SubtitleSelectionMode.ALWAYS, SubtitleTrackSelector.fromJellyfinSubtitleMode("Always"))
        assertEquals(SubtitleSelectionMode.NONE, SubtitleTrackSelector.fromJellyfinSubtitleMode("None"))
        assertEquals(SubtitleSelectionMode.DEFAULT, SubtitleTrackSelector.fromJellyfinSubtitleMode("Default"))
        assertEquals(SubtitleSelectionMode.DEFAULT, SubtitleTrackSelector.fromJellyfinSubtitleMode(null))
    }

    @Test
    fun `NONE mode always disables subtitles`() {
        val picked = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.NONE,
            preferredSubtitleLang = "en",
            preferredAudioLang = "en",
            activeAudioLang = "jpn",
            subtitleTracks = listOf(enFull, enForced, esFull)
        )
        assertNull(picked)
    }

    @Test
    fun `ONLY_FORCED selects forced subtitle matching preferred language or audio language`() {
        val pickedPreferred = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.ONLY_FORCED,
            preferredSubtitleLang = "en",
            preferredAudioLang = "en",
            activeAudioLang = "jpn",
            subtitleTracks = listOf(enFull, enForced, jaForced)
        )
        assertEquals(enForced.index, pickedPreferred?.index)

        val pickedAudioForced = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.ONLY_FORCED,
            preferredSubtitleLang = null,
            preferredAudioLang = "ja",
            activeAudioLang = "jpn",
            subtitleTracks = listOf(enFull, jaForced)
        )
        assertEquals(jaForced.index, pickedAudioForced?.index)

        val noneWhenNoForced = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.ONLY_FORCED,
            preferredSubtitleLang = "en",
            preferredAudioLang = "en",
            activeAudioLang = "eng",
            subtitleTracks = listOf(enFull, esFull)
        )
        assertNull(noneWhenNoForced)
    }

    @Test
    fun `SMART mode enables full subtitle on foreign audio and forced-only on matching audio`() {
        // Foreign audio (Japanese) with English user -> picks non-SDH full English track
        val foreignPicked = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.SMART,
            preferredSubtitleLang = "en",
            preferredAudioLang = "en",
            activeAudioLang = "jpn",
            subtitleTracks = listOf(enSdh, enForced, enFull, esFull)
        )
        assertEquals(enFull.index, foreignPicked?.index)

        // Matching audio (English) with English user -> falls back to English forced only
        val nativePicked = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.SMART,
            preferredSubtitleLang = "en",
            preferredAudioLang = "en",
            activeAudioLang = "eng",
            subtitleTracks = listOf(enFull, enForced, esFull)
        )
        assertEquals(enForced.index, nativePicked?.index)

        // Matching audio with no forced subtitle -> off
        val nativeNoForced = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.SMART,
            preferredSubtitleLang = "en",
            preferredAudioLang = "en",
            activeAudioLang = "eng",
            subtitleTracks = listOf(enFull, esFull)
        )
        assertNull(nativeNoForced)
    }

    @Test
    fun `ALWAYS mode picks preferred full subtitle even on matching audio`() {
        val picked = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.ALWAYS,
            preferredSubtitleLang = "en",
            preferredAudioLang = "en",
            activeAudioLang = "eng",
            subtitleTracks = listOf(enForced, enSdh, enFull)
        )
        assertEquals(enFull.index, picked?.index)
    }

    @Test
    fun `DEFAULT mode respects default flag and preferred language`() {
        val picked = SubtitleTrackSelector.selectSubtitleTrack(
            mode = SubtitleSelectionMode.DEFAULT,
            preferredSubtitleLang = "es",
            preferredAudioLang = "en",
            activeAudioLang = "eng",
            subtitleTracks = listOf(enFull, esFull)
        )
        assertEquals(esFull.index, picked?.index)
    }
}
