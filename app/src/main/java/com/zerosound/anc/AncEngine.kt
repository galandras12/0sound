package com.zerosound.anc

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.os.Build
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.tanh

internal class AudioIo(val recorders: List<AudioRecord>, val track: AudioTrack) {
    private val scratch = ShortArray(AncEngine.FRAME)

    /**
     * Reads up to one frame, averaging all active microphones into [mix] (values -1..1).
     * The first recorder paces the loop; extra ones are read for the same number of samples.
     */
    fun read(mix: FloatArray): Int {
        val n = recorders[0].read(scratch, 0, AncEngine.FRAME)
        if (n <= 0) return n
        for (i in 0 until n) mix[i] = scratch[i] / 32768f
        for (r in 1 until recorders.size) {
            var got = 0
            while (got < n) {
                val k = recorders[r].read(scratch, got, n - got)
                if (k <= 0) break // stream lost: treated as silence
                got += k
            }
            for (i in 0 until n) mix[i] += if (i < got) scratch[i] / 32768f else 0f
        }
        if (recorders.size > 1) {
            val inv = 1f / recorders.size
            for (i in 0 until n) mix[i] *= inv
        }
        return n
    }

    fun start() {
        recorders.forEach { it.startRecording() }
        track.play()
    }

    fun release() {
        recorders.forEach {
            runCatching { it.stop() }
            it.release()
        }
        runCatching { track.stop() }
        track.release()
    }
}

/** Opens the selected microphones + low-latency media output. Calibration and ANC share this. */
@SuppressLint("MissingPermission")
internal fun openAudioIo(): AudioIo? {
    val sr = AncEngine.SAMPLE_RATE
    val recMin = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val trkMin = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (recMin <= 0 || trkMin <= 0) return null

    fun recorder(source: Int, direction: Int? = null): AudioRecord? = try {
        AudioRecord(
            source, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(recMin, AncEngine.FRAME * 4),
        ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }?.also {
            if (direction != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { it.setPreferredMicrophoneDirection(direction) }
            }
        }
    } catch (e: Exception) {
        null
    }

    val recorders = mutableListOf<AudioRecord>()
    // UNPROCESSED avoids AGC / noise suppression; fall back to plain MIC when unsupported.
    if (Prefs.micMain.value || (!Prefs.micCameraBack.value && !Prefs.micCameraFront.value)) {
        (recorder(MediaRecorder.AudioSource.UNPROCESSED) ?: recorder(MediaRecorder.AudioSource.MIC))?.let { recorders += it }
    }
    // Camera (CAMCORDER) microphones; the direction hint is honoured only by some devices.
    if (Prefs.micCameraBack.value) {
        recorder(MediaRecorder.AudioSource.CAMCORDER, MicrophoneDirection.MIC_DIRECTION_AWAY_FROM_USER)?.let { recorders += it }
    }
    if (Prefs.micCameraFront.value) {
        recorder(MediaRecorder.AudioSource.CAMCORDER, MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)?.let { recorders += it }
    }
    if (recorders.isEmpty()) return null

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
        recorders.forEach { it.release() }
        return null
    }
    AncEngine.setMicCount(recorders.size)
    return AudioIo(recorders, track)
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

    /**
     * @param onRoute called (from the audio thread) with the device the output is actually routed to.
     * @param onRouteRejected called when that device is not a supported one (e.g. the phone speaker).
     */
    fun start(onRoute: (AudioDeviceInfo) -> Unit, onRouteRejected: () -> Unit): Boolean {
        if (running) return true
        val io = openAudioIo() ?: return false
        running = true
        thread = Thread({ loop(io, onRoute, onRouteRejected) }, "0sound-anc").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop(io: AudioIo, onRoute: (AudioDeviceInfo) -> Unit, onRouteRejected: () -> Unit) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mix = FloatArray(FRAME)
        val out = ShortArray(FRAME)
        val predictor = AdaptivePredictor()
        var xPrev = 0f
        var yPrev = 0f
        var l1 = 0f
        var l2 = 0f
        try {
            io.start()
            var routeCheck = 0
            var lastRoute: AudioDeviceInfo? = null
            while (running) {
                val n = io.read(mix)
                if (n <= 0) continue
                // Route watchdog (~every 0.5 s): the audio must really leave through a supported device.
                if (routeCheck-- <= 0) {
                    routeCheck = ROUTE_CHECK_FRAMES
                    val routed = io.track.routedDevice
                    if (routed != null) {
                        if (!AudioRoutes.isAllowed(routed)) {
                            running = false
                            onRouteRejected()
                            break
                        }
                        if (routed.id != lastRoute?.id) onRoute(routed)
                        lastRoute = routed
                    }
                }
                val gain = Prefs.strength.value * MAX_GAIN
                val adaptive = Prefs.adaptive.value
                val a = exp(-2.0 * PI * Prefs.cutoffHz.value / SAMPLE_RATE).toFloat()
                // Group delay of the two one-pole filters: the predictor must look this much further ahead.
                val groupDelay = (2f * a / (1f - a)).roundToInt()
                val latencyMs = Prefs.latencyMs.value.takeIf { it > 0f } ?: DEFAULT_LATENCY_MS
                val delay = ((latencyMs + Prefs.trimMs.value) * SAMPLE_RATE / 1000f).roundToInt() + groupDelay
                val horizon = delay.coerceIn(1, AdaptivePredictor.MAX_DELAY)
                for (i in 0 until n) {
                    val x = mix[i]
                    val y = x - xPrev + 0.995f * yPrev // DC blocker
                    xPrev = x
                    yPrev = y
                    l1 = (1 - a) * y + a * l1
                    l2 = (1 - a) * l1 + a * l2
                    val anti = if (adaptive) predictor.process(l2, horizon) else l2
                    out[i] = (softLimit(-anti * gain) * 0.9f * 32767f).toInt().toShort()
                }
                io.track.write(out, 0, n)
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
        private const val ROUTE_CHECK_FRAMES = 125 // 125 x 4 ms = 0.5 s

        private val _micCount = MutableStateFlow(0)

        /** Number of microphones actually opened by the last start. */
        val micCount: StateFlow<Int> = _micCount
        internal fun setMicCount(n: Int) {
            _micCount.value = n
        }
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
