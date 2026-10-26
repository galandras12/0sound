package com.zerosound.anc

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Process-wide settings, shared by the UI and the ANC service. */
object Prefs {
    private var sp: SharedPreferences? = null

    private val _runInBackground = MutableStateFlow(false)
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val _strength = MutableStateFlow(0.6f)
    private val _cutoffHz = MutableStateFlow(500f)
    private val _micMain = MutableStateFlow(true)
    private val _micCameraBack = MutableStateFlow(false)
    private val _micCameraFront = MutableStateFlow(false)
    private val _adaptive = MutableStateFlow(true)
    private val _latencyMs = MutableStateFlow(0f)
    private val _trimMs = MutableStateFlow(0f)

    val runInBackground: StateFlow<Boolean> = _runInBackground
    val themeMode: StateFlow<ThemeMode> = _themeMode
    val strength: StateFlow<Float> = _strength
    val cutoffHz: StateFlow<Float> = _cutoffHz

    /** Microphone selection; several can be active at once and are averaged. */
    val micMain: StateFlow<Boolean> = _micMain
    val micCameraBack: StateFlow<Boolean> = _micCameraBack
    val micCameraFront: StateFlow<Boolean> = _micCameraFront

    /** true: adaptive predictor (hum / tonal noise); false: plain phase inversion. */
    val adaptive: StateFlow<Boolean> = _adaptive

    /** Measured loop latency in ms; 0 = not calibrated yet. */
    val latencyMs: StateFlow<Float> = _latencyMs
    val trimMs: StateFlow<Float> = _trimMs

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences("0sound", Context.MODE_PRIVATE)
        sp = p
        _runInBackground.value = p.getBoolean("bg", false)
        _themeMode.value = runCatching { ThemeMode.valueOf(p.getString("theme", "")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        _strength.value = p.getFloat("strength", 0.6f)
        _cutoffHz.value = p.getFloat("cutoff", 500f)
        _micMain.value = p.getBoolean("mic_main", true)
        _micCameraBack.value = p.getBoolean("mic_cam_back", false)
        _micCameraFront.value = p.getBoolean("mic_cam_front", false)
        _adaptive.value = p.getBoolean("adaptive", true)
        _latencyMs.value = p.getFloat("latency", 0f)
        _trimMs.value = p.getFloat("trim", 0f)
    }

    fun setRunInBackground(v: Boolean) {
        _runInBackground.value = v
        sp?.edit()?.putBoolean("bg", v)?.apply()
    }

    fun setThemeMode(v: ThemeMode) {
        _themeMode.value = v
        sp?.edit()?.putString("theme", v.name)?.apply()
    }

    fun setStrength(v: Float) {
        _strength.value = v
        sp?.edit()?.putFloat("strength", v)?.apply()
    }

    fun setCutoffHz(v: Float) {
        _cutoffHz.value = v
        sp?.edit()?.putFloat("cutoff", v)?.apply()
    }

    fun setAdaptive(v: Boolean) {
        _adaptive.value = v
        sp?.edit()?.putBoolean("adaptive", v)?.apply()
    }

    fun setLatencyMs(v: Float) {
        _latencyMs.value = v
        sp?.edit()?.putFloat("latency", v)?.apply()
    }

    fun setTrimMs(v: Float) {
        _trimMs.value = v
        sp?.edit()?.putFloat("trim", v)?.apply()
    }

    fun setMicMain(v: Boolean) {
        _micMain.value = v
        sp?.edit()?.putBoolean("mic_main", v)?.apply()
    }

    fun setMicCameraBack(v: Boolean) {
        _micCameraBack.value = v
        sp?.edit()?.putBoolean("mic_cam_back", v)?.apply()
    }

    fun setMicCameraFront(v: Boolean) {
        _micCameraFront.value = v
        sp?.edit()?.putBoolean("mic_cam_front", v)?.apply()
    }
}
