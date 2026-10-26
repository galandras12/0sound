package com.zerosound.anc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CalibratorTest {
    private val sr = 48000

    /** Burst -> delayed, optionally polarity-inverted, band-limited, noisy "microphone" recording. */
    private fun run(lagSamples: Int, polarity: Float, gain: Float, noise: Float): Calibrator.Estimate {
        val warm = 3 * sr
        val burst = (0.6f * sr).toInt()
        val maxLag = 300 * sr / 1000
        val n = warm + burst + 20000 + maxLag
        val out = FloatArray(n)
        val rnd = Random(42)
        for (m in warm until warm + burst) out[m] = (rnd.nextFloat() * 2f - 1f) * 0.3f
        val mic = FloatArray(n)
        // crude band-pass (high-pass 150 Hz, low-pass ~4 kHz) = speaker/mic response
        var hp = 0f
        var lp = 0f
        var prev = 0f
        val noiseRnd = Random(7)
        for (i in 0 until n) {
            val x = if (i - lagSamples >= 0) out[i - lagSamples] else 0f
            hp = 0.98f * (hp + x - prev)
            prev = x
            lp += 0.4f * (hp - lp)
            mic[i] = polarity * gain * lp + noise * noiseRnd.nextGaussian().toFloat()
        }
        return Calibrator.estimateLatency(out, mic, warm, burst, maxLag)
    }

    @Test
    fun findsLatencyNormalPolarity() {
        val e = run(1790, 1f, 0.5f, 0.01f)
        assertTrue("confidence ${e.confidence}", e.confidence > 6f)
        assertEquals(1790f, e.lagSamples, 6f) // filter group delay only, < 0.15 ms
    }

    @Test
    fun findsLatencyInvertedPolarity() {
        val e = run(1790, -1f, 0.5f, 0.01f)
        assertTrue("confidence ${e.confidence}", e.confidence > 6f)
        assertEquals(1790f, e.lagSamples, 6f)
    }

    @Test
    fun findsLatencyWithWeakSignalAndNoise() {
        val e = run(1234, -1f, 0.05f, 0.05f)
        assertTrue("confidence ${e.confidence}", e.confidence > 6f)
    }

    @Test
    fun rejectsPureNoise() {
        val e = run(1000, 1f, 0f, 0.05f)
        assertTrue("confidence ${e.confidence}", e.confidence < 6f)
    }
}
