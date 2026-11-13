package com.seerflix.tv.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleParser

@OptIn(UnstableApi::class)
class OffsetSubtitleParserFactory(
    private val delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
    private val offsetUsProvider: () -> Long
) : SubtitleParser.Factory {

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        OffsetSubtitleParser(delegate.create(format), offsetUsProvider)

    companion object {
        fun shiftCuesWithTiming(cues: CuesWithTiming, offsetUs: Long): CuesWithTiming {
            if (offsetUs == 0L || cues.startTimeUs == C.TIME_UNSET) return cues
            val shiftedStart = (cues.startTimeUs + offsetUs).coerceAtLeast(0L)
            return CuesWithTiming(cues.cues, shiftedStart, cues.durationUs)
        }
    }
}

@OptIn(UnstableApi::class)
private class OffsetSubtitleParser(
    private val delegate: SubtitleParser,
    private val offsetUsProvider: () -> Long
) : SubtitleParser {

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        val currentOffsetUs = offsetUsProvider()
        delegate.parse(data, offset, length, outputOptions) { cuesWithTiming ->
            output.accept(OffsetSubtitleParserFactory.shiftCuesWithTiming(cuesWithTiming, currentOffsetUs))
        }
    }

    override fun parseToLegacySubtitle(data: ByteArray, offset: Int, length: Int): Subtitle {
        val inner = delegate.parseToLegacySubtitle(data, offset, length)
        return OffsetLegacySubtitle(inner, offsetUsProvider)
    }

    override fun reset() = delegate.reset()

    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior
}

@OptIn(UnstableApi::class)
private class OffsetLegacySubtitle(
    private val inner: Subtitle,
    private val offsetUsProvider: () -> Long
) : Subtitle {

    override fun getNextEventTimeIndex(timeUs: Long): Int =
        inner.getNextEventTimeIndex((timeUs - offsetUsProvider()).coerceAtLeast(0L))

    override fun getEventTimeCount(): Int = inner.eventTimeCount

    override fun getEventTime(index: Int): Long =
        (inner.getEventTime(index) + offsetUsProvider()).coerceAtLeast(0L)

    override fun getCues(timeUs: Long): MutableList<Cue> =
        inner.getCues((timeUs - offsetUsProvider()).coerceAtLeast(0L))
}
