package com.seerflix.tv.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Live PCM audio sync offset processor (±[MAX_OFFSET_MS] ms in [STEP_MS] ms steps).
 *
 * Positive offset delays audio relative to video (inserts silence frames when offset increases or
 * after a seek/flush). Negative offset advances audio relative to video (drops leading PCM frames
 * when offset decreases or after a seek/flush). Zero offset passes samples straight through.
 *
 * Note: applies to PCM output (decoded audio); HDMI bitstream passthrough bypasses AudioProcessors.
 */
@OptIn(UnstableApi::class)
class AudioDelayProcessor : BaseAudioProcessor() {

    private val targetOffsetMs = AtomicLong(0L)
    private var appliedOffsetFrames: Long = 0L

    fun setDelayMs(offsetMs: Long) {
        targetOffsetMs.set(offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS))
    }

    fun getDelayMs(): Long = targetOffsetMs.get()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        appliedOffsetFrames = 0L
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        val sampleRate = inputAudioFormat.sampleRate
        if (bytesPerFrame <= 0 || sampleRate <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val remainingBytes = inputBuffer.remaining()
        val targetFrames = (targetOffsetMs.get() * sampleRate) / 1000L
        val deltaFrames = targetFrames - appliedOffsetFrames

        if (deltaFrames == 0L) {
            if (remainingBytes == 0) return
            val out = replaceOutputBuffer(remainingBytes)
            out.put(inputBuffer)
            out.flip()
            return
        }

        if (deltaFrames > 0L) {
            // Insert silent frames (bounded per call so we never allocate a giant buffer at once).
            val silenceFrames = deltaFrames.coerceAtMost(MAX_SILENCE_CHUNK_FRAMES)
            val silenceBytes = (silenceFrames * bytesPerFrame).toInt()
            val out = replaceOutputBuffer(silenceBytes + remainingBytes)
            for (i in 0 until silenceBytes) {
                out.put(0)
            }
            if (remainingBytes > 0) {
                out.put(inputBuffer)
            }
            out.flip()
            appliedOffsetFrames += silenceFrames
        } else {
            // Negative delta: drop leading frames from this buffer to advance audio.
            val framesToDrop = -deltaFrames
            val availableFrames = (remainingBytes / bytesPerFrame).toLong()
            val dropNow = framesToDrop.coerceAtMost(availableFrames)
            val dropBytes = (dropNow * bytesPerFrame).toInt()
            inputBuffer.position(inputBuffer.position() + dropBytes)
            appliedOffsetFrames -= dropNow

            val leftBytes = inputBuffer.remaining()
            if (leftBytes > 0) {
                val out = replaceOutputBuffer(leftBytes)
                out.put(inputBuffer)
                out.flip()
            }
        }
    }

    override fun onFlush() {
        appliedOffsetFrames = 0L
    }

    override fun onReset() {
        appliedOffsetFrames = 0L
    }

    companion object {
        const val MAX_OFFSET_MS = 1_000L
        const val STEP_MS = 50L
        private const val MAX_SILENCE_CHUNK_FRAMES = 48_000L
    }
}
