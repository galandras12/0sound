package com.zerosound.anc

import android.os.Process
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

sealed interface CalState {
    data object Idle : CalState
    data object Running : CalState
    data class Done(val latencyMs: Float) : CalState
    data class Failed(@StringRes val reason: Int) : CalState
}

/**
 * Measures the loop latency (sample written -> same sample heard by the phone mic).
 *
 * The user holds an ear cup against the phone microphone. A noise burst is played through the
 * exact same audio path and loop cadence as the ANC engine, and the delay is found by
 * cross-correlation, giving sample-level (~0.02 ms) accuracy.
 */
object Calibrator {
    private val _state = MutableStateFlow<CalState>(CalState.Idle)
    val state: StateFlow<CalState> = _state

    private const val WARMUP_S = 3.0f
    private const val BURST_S = 0.6f
    private const val TAIL_S = 0.4f
    private const val MAX_LAG_MS = 200
    private const val BURST_LEVEL = 0.3f

    fun reset() {
        if (_state.value !is CalState.Running) _state.value = CalState.Idle
    }

    fun run() {
        if (_state.value is CalState.Running) return
        _state.value = CalState.Running
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            _state.value = try {
                measure()
            } catch (e: Exception) {
                CalState.Failed(R.string.cal_failed_audio)
            }
        }, "0sound-cal").start()
    }

    private fun measure(): CalState {
        val sr = AncEngine.SAMPLE_RATE
        val frame = AncEngine.FRAME
        val io = openAudioIo() ?: return CalState.Failed(R.string.cal_failed_audio)

        val warm = (WARMUP_S * sr).toInt() / frame * frame
        val burstLen = (BURST_S * sr).toInt()
        val total = warm + burstLen + (TAIL_S * sr).toInt() + MAX_LAG_MS * sr / 1000
        val out = FloatArray(total / frame * frame + frame)
        val rnd = Random(42)
        for (m in warm until warm + burstLen) out[m] = (rnd.nextFloat() * 2f - 1f) * BURST_LEVEL
        val mic = FloatArray(out.size)

        val chunk = FloatArray(frame)
        try {
            io.start()
            var pos = 0
            while (pos + frame <= out.size) {
                val n = io.read(chunk)
                if (n <= 0) return CalState.Failed(R.string.cal_failed_audio)
                for (i in 0 until n) mic[pos + i] = chunk[i]
                // same cadence as the ANC loop: write the samples that belong to the ones just read
                val w = ShortArray(n) { (out[pos + it] * 32767f).toInt().toShort() }
                io.track.write(w, 0, n)
                pos += n
            }
        } finally {
            io.release()
        }

        // remove DC from the recording
        val dc = mic.average().toFloat()
        for (i in mic.indices) mic[i] -= dc

        val maxLag = MAX_LAG_MS * sr / 1000
        val corr = FloatArray(maxLag + 1)
        for (lag in 0..maxLag) {
            var s = 0f
            val end = warm + burstLen
            for (m in warm until end) s += out[m] * mic[m + lag]
            corr[lag] = s
        }

        var best = 0
        for (lag in 1..maxLag) if (corr[lag] > corr[best]) best = lag
        var sumSq = 0.0
        var cnt = 0
        for (lag in 0..maxLag) {
            if (abs(lag - best) > 40) {
                sumSq += corr[lag].toDouble() * corr[lag]
                cnt++
            }
        }
        val rms = sqrt(sumSq / cnt.coerceAtLeast(1)).toFloat()
        val confidence = if (rms > 0f) corr[best] / rms else 0f
        if (corr[best] <= 0f || confidence < MIN_CONFIDENCE) return CalState.Failed(R.string.cal_failed_weak)

        // parabolic interpolation for sub-sample accuracy
        var exact = best.toFloat()
        if (best in 1 until maxLag) {
            val a = corr[best - 1]
            val b = corr[best]
            val c = corr[best + 1]
            val d = a - 2 * b + c
            if (d != 0f) exact += 0.5f * (a - c) / d
        }
        val ms = exact * 1000f / sr
        Prefs.setLatencyMs(ms)
        return CalState.Done(ms)
    }

    private const val MIN_CONFIDENCE = 6f
}
