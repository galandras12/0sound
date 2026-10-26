package com.zerosound.anc

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sin

class AdaptivePredictorTest {
    private val sr = 48000

    /** Fan-like hum (118 Hz + harmonics, slow RPM wobble, broadband floor) through a 40 ms loop. */
    private fun reductionDb(delayMs: Float, assumedMs: Float): Double {
        val n = sr * 12
        val rnd = Random(1)
        val noise = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            phase += 2 * PI * 118.0 * (1 + 0.002 * sin(2 * PI * 0.3 * i / sr)) / sr
            var v = 0.0
            for (k in 1..8) v += 0.8 / k * sin(k * phase + k)
            noise[i] = (v + rnd.nextGaussian() * 0.15).toFloat()
        }
        val a = exp(-2.0 * PI * 500 / sr).toFloat()
        var l1 = 0f
        var l2 = 0f
        val gd = Math.round(2 * a / (1 - a))
        val horizon = Math.round(assumedMs * sr / 1000f) + gd
        val p = AdaptivePredictor()
        val anti = FloatArray(n)
        for (i in 0 until n) {
            l1 = (1 - a) * noise[i] + a * l1
            l2 = (1 - a) * l1 + a * l2
            anti[i] = -p.process(l2, horizon)
        }
        val d = Math.round(delayMs * sr / 1000f)
        var pn = 0.0
        var pr = 0.0
        for (i in n - 3 * sr until n) {
            val r = noise[i] + (if (i - d >= 0) anti[i - d] else 0f)
            pn += noise[i] * noise[i]
            pr += r * r
        }
        return 10 * log10(pn / pr)
    }

    @Test
    fun cancelsHumWhenLatencyIsKnown() {
        val db = reductionDb(40f, 40f)
        println("reduction with exact latency: $db dB")
        assertTrue("expected > 5 dB, got $db", db > 5.0)
    }

    @Test
    fun stillHelpsWithSmallLatencyError() {
        val db = reductionDb(40f, 40.2f)
        println("reduction with 0.2 ms error: $db dB")
        assertTrue("expected > 2 dB, got $db", db > 2.0)
    }
}
