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
import kotlin.math.roundToInt
import kotlin.math.tanh

internal class AudioIo(val recorder: AudioRecord, val track: AudioTrack) {
    fun release() {
        runCatching { recorder.stop() }
        runCatching { track.stop() }
        recorder.release()
        track.release()
    }
}

/** Opens the mic + low-latency media output. Calibration and ANC use the same configuration. */
@SuppressLint("MissingPermission")
internal fun openAudioIo(): AudioIo? {
    val sr = AncEngine.SAMPLE_RATE
    val recMin = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val trkMin = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (recMin <= 0 || trkMin <= 0) return null

    fun recorder(source: Int): AudioRecord? = try {
        AudioRecord(
            source, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(recMin, AncEngine.FRAME * 4),
        ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    } catch (e: Exception) {
        null
    }

    // UNPROCESSED avoids AGC / noise suppression; fall back to plain MIC when unsupported.
    val rec = recorder(MediaRecorder.AudioSource.UNPROCESSED)
        ?: recorder(MediaRecorder.AudioSource.MIC)
        ?: return null

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
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(trkMin, AncEngine.FRAME * 2 * 2))
            .build()
            .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
    } catch (e: Exception) {
        null
    }
    if (track == null) {
        rec.release()
        return null
    }
    return AudioIo(rec, track)
}

/**
 * Feed-forward ANC.
 *
 * Phone mic -> DC blocker -> 2x one-pole low-pass -> predictor (or plain inversion) -> inversion
 * -> soft limiter -> media output -> headphones.
 */
class AncEngine {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(): Boolean {
        if (running) return true
        val io = openAudioIo() ?: return false
        running = true
        thread = Thread({ loop(io) }, "0sound-anc").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop(io: AudioIo) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = ShortArray(FRAME)
        val predictor = AdaptivePredictor()
        var xPrev = 0f
        var yPrev = 0f
        var l1 = 0f
        var l2 = 0f
        try {
            io.recorder.startRecording()
            io.track.play()
            while (running) {
                val n = io.recorder.read(buf, 0, FRAME)
                if (n <= 0) continue
                val gain = Prefs.strength.value * MAX_GAIN
                val adaptive = Prefs.adaptive.value
                val a = exp(-2.0 * PI * Prefs.cutoffHz.value / SAMPLE_RATE).toFloat()
                // Group delay of the two one-pole filters: the predictor must look this much further ahead.
                val groupDelay = (2f * a / (1f - a)).roundToInt()
                val latencyMs = Prefs.latencyMs.value.takeIf { it > 0f } ?: DEFAULT_LATENCY_MS
                val delay = ((latencyMs + Prefs.trimMs.value) * SAMPLE_RATE / 1000f).roundToInt() + groupDelay
                val horizon = delay.coerceIn(1, AdaptivePredictor.MAX_DELAY)
                for (i in 0 until n) {
                    val x = buf[i] / 32768f
                    val y = x - xPrev + 0.995f * yPrev // DC blocker
                    xPrev = x
                    yPrev = y
                    l1 = (1 - a) * y + a * l1
                    l2 = (1 - a) * l1 + a * l2
                    val anti = if (adaptive) predictor.process(l2, horizon) else l2
                    buf[i] = (softLimit(-anti * gain) * 0.9f * 32767f).toInt().toShort()
                }
                io.track.write(buf, 0, n)
            }
        } catch (_: Exception) {
            // device went away; fall through to cleanup
        } finally {
            io.release()
            running = false
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val FRAME = 192 // 4 ms
        const val DEFAULT_LATENCY_MS = 30f
        private const val MAX_GAIN = 6f

        /** Linear up to 0.6, smooth saturation above it. */
        fun softLimit(v: Float): Float {
            val m = abs(v)
            if (m < 0.6f) return v
            val s = 0.6f + 0.4f * tanh((m - 0.6f) / 0.4f)
            return if (v < 0f) -s else s
        }
    }
}
