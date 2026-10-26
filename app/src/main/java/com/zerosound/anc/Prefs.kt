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
    private val _cutoffHz = MutableStateFlow(700f)

    val runInBackground: StateFlow<Boolean> = _runInBackground
    val themeMode: StateFlow<ThemeMode> = _themeMode
    val strength: StateFlow<Float> = _strength
    val cutoffHz: StateFlow<Float> = _cutoffHz

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences("0sound", Context.MODE_PRIVATE)
        sp = p
        _runInBackground.value = p.getBoolean("bg", false)
        _themeMode.value = runCatching { ThemeMode.valueOf(p.getString("theme", "")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        _strength.value = p.getFloat("strength", 0.6f)
        _cutoffHz.value = p.getFloat("cutoff", 700f)
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
}
