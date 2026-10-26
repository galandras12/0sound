package com.zerosound.anc

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * Feed-forward ANC prototype.
 *
 * Phone microphone -> DC blocker -> 2x one-pole low-pass -> phase inversion -> soft limiter
 * -> AudioTrack (USAGE_MEDIA) -> headphones.
 *
 * Only low frequencies can realistically be cancelled because the loop has latency,
 * hence the low-pass stage.
 */
class AncEngine {
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true

        val recMin = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val trkMin = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (recMin <= 0 || trkMin <= 0) return false

        // UNPROCESSED avoids AGC / noise suppression; fall back to plain MIC when unsupported.
        val recorder = createRecorder(MediaRecorder.AudioSource.UNPROCESSED, recMin)
            ?: createRecorder(MediaRecorder.AudioSource.MIC, recMin)
            ?: return false

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(trkMin, FRAME * 2 * 2))
                .build()
        } catch (e: Exception) {
            recorder.release()
            return false
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            recorder.release()
            return false
        }

        running = true
        thread = Thread({ loop(recorder, track) }, "0sound-anc").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(source: Int, minBuf: Int): AudioRecord? = try {
        AudioRecord(
            source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, FRAME * 4),
        ).takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: null
    } catch (e: Exception) {
        null
    }

    private fun loop(recorder: AudioRecord, track: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = ShortArray(FRAME)
        var xPrev = 0f
        var yPrev = 0f
        var l1 = 0f
        var l2 = 0f
        try {
            recorder.startRecording()
            track.play()
            while (running) {
                val n = recorder.read(buf, 0, FRAME)
                if (n <= 0) continue
                val strength = Prefs.strength.value * MAX_GAIN
                val a = exp(-2.0 * PI * Prefs.cutoffHz.value / SAMPLE_RATE).toFloat()
                for (i in 0 until n) {
                    val x = buf[i] / 32768f
                    // DC blocker
                    val y = x - xPrev + 0.995f * yPrev
                    xPrev = x
                    yPrev = y
                    // 2-pole low-pass
                    l1 = (1 - a) * y + a * l1
                    l2 = (1 - a) * l1 + a * l2
                    // phase inversion + gain + soft limiter (unity slope at 0)
                    val o = -l2 * strength
                    val limited = o / (1f + abs(o))
                    buf[i] = (limited * 0.9f * 32767f).toInt().toShort()
                }
                track.write(buf, 0, n)
            }
        } catch (_: Exception) {
            // device went away; fall through to cleanup
        } finally {
            runCatching { recorder.stop() }
            runCatching { track.stop() }
            recorder.release()
            track.release()
            running = false
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000
        private const val FRAME = 192 // 4 ms
        private const val MAX_GAIN = 4f
    }
}
