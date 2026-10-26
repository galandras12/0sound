package com.zerosound.anc

/**
 * Adaptive linear predictor (NLMS) that estimates the signal [delay] samples ahead.
 *
 * The loop latency (mic -> app -> headphone) is much longer than half a period of a hum, so a plain
 * phase inversion lands at a random phase. Steady, periodic noise (fan hum and its harmonics)
 * is predictable, so we play the *predicted* noise, inverted, to hit the right phase at the ear.
 *
 * Training: predict x[n] from the window x[n-delay-1 ... n-delay-taps]. The same weights are then
 * applied to the newest window to get an estimate of x[n+delay].
 */
class AdaptivePredictor(
    private val taps: Int = 128,
    private val mu: Float = 0.02f,
    private val leak: Float = 1e-6f,
) {
    private val mask = SIZE - 1
    private val hist = FloatArray(SIZE)
    private val w = FloatArray(taps)
    private var count = 0L

    /** Feeds one sample, returns the prediction of the signal [delay] samples in the future. */
    fun process(x: Float, delay: Int): Float {
        val n = count.toInt()
        hist[n and mask] = x
        count++

        var y = 0f
        for (k in 0 until taps) y += w[k] * hist[(n - k) and mask]

        val s = n - delay
        if (count > delay + taps) {
            var est = 0f
            var energy = 0f
            for (k in 0 until taps) {
                val u = hist[(s - k) and mask]
                est += w[k] * u
                energy += u * u
            }
            val g = mu * (x - est) / (1e-6f + energy)
            val keep = 1f - leak
            for (k in 0 until taps) w[k] = w[k] * keep + g * hist[(s - k) and mask]
        }

        if (!y.isFinite()) {
            w.fill(0f)
            return 0f
        }
        return y
    }

    fun reset() {
        w.fill(0f)
        hist.fill(0f)
        count = 0
    }

    companion object {
        const val SIZE = 1 shl 15
        const val MAX_DELAY = 12000
    }
}
