package com.zerosound.anc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerosound.anc.BuildConfig
import com.zerosound.anc.CalState
import com.zerosound.anc.Calibrator
import com.zerosound.anc.Prefs
import com.zerosound.anc.R
import com.zerosound.anc.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, onCalibrate: () -> Unit) {
    val bg by Prefs.runInBackground.collectAsState()
    val theme by Prefs.themeMode.collectAsState()
    val strength by Prefs.strength.collectAsState()
    val cutoff by Prefs.cutoffHz.collectAsState()
    val micMain by Prefs.micMain.collectAsState()
    val micBack by Prefs.micCameraBack.collectAsState()
    val micFront by Prefs.micCameraFront.collectAsState()
    val adaptive by Prefs.adaptive.collectAsState()
    val latency by Prefs.latencyMs.collectAsState()
    val trim by Prefs.trimMs.collectAsState()
    val cal by Calibrator.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                stringResource(R.string.settings),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Section(R.string.section_background)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(stringResource(R.string.run_in_background), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    Text(stringResource(R.string.run_in_background_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Switch(checked = bg, onCheckedChange = Prefs::setRunInBackground)
            }

            Divider()
            Section(R.string.section_theme)
            ThemeOption(R.string.theme_system, theme == ThemeMode.SYSTEM) { Prefs.setThemeMode(ThemeMode.SYSTEM) }
            ThemeOption(R.string.theme_light, theme == ThemeMode.LIGHT) { Prefs.setThemeMode(ThemeMode.LIGHT) }
            ThemeOption(R.string.theme_dark, theme == ThemeMode.DARK) { Prefs.setThemeMode(ThemeMode.DARK) }

            Divider()
            Section(R.string.section_mics)
            val selected = listOf(micMain, micBack, micFront).count { it }
            MicOption(R.string.mic_main, R.string.mic_main_desc, micMain, enabled = !(micMain && selected == 1)) { Prefs.setMicMain(it) }
            MicOption(R.string.mic_cam_back, R.string.mic_cam_back_desc, micBack, enabled = !(micBack && selected == 1)) { Prefs.setMicCameraBack(it) }
            MicOption(R.string.mic_cam_front, R.string.mic_cam_front_desc, micFront, enabled = !(micFront && selected == 1)) { Prefs.setMicCameraFront(it) }
            Text(stringResource(R.string.mics_note), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            Divider()
            Section(R.string.section_algorithm)
            AlgoOption(R.string.algo_adaptive, R.string.algo_adaptive_desc, adaptive) { Prefs.setAdaptive(true) }
            AlgoOption(R.string.algo_basic, R.string.algo_basic_desc, !adaptive) { Prefs.setAdaptive(false) }

            Divider()
            Section(R.string.section_calibration)
            Text(stringResource(R.string.cal_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCalibrate, enabled = cal !is CalState.Running) {
                Text(stringResource(R.string.cal_button))
            }
            Spacer(Modifier.height(8.dp))
            val status = when (val c = cal) {
                is CalState.Running -> stringResource(R.string.cal_running)
                is CalState.Failed -> stringResource(c.reason)
                is CalState.Done -> stringResource(R.string.cal_result, c.latencyMs)
                CalState.Idle -> if (latency > 0f) stringResource(R.string.cal_result, latency) else stringResource(R.string.cal_none)
            }
            Text(status, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cal_trim, trim), color = MaterialTheme.colorScheme.onBackground)
            Slider(value = trim, onValueChange = { Prefs.setTrimMs((it * 20).roundToInt() / 20f) }, valueRange = -3f..3f)
            Text(stringResource(R.string.cal_trim_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            Divider()
            Section(R.string.section_tuning)
            Text(
                "${stringResource(R.string.tuning_strength)}: ${(strength * 100).roundToInt()}%",
                color = MaterialTheme.colorScheme.onBackground,
            )
            Slider(value = strength, onValueChange = Prefs::setStrength, valueRange = 0f..1f)
            Text(
                "${stringResource(R.string.tuning_cutoff)}: ${cutoff.roundToInt()} Hz",
                color = MaterialTheme.colorScheme.onBackground,
            )
            Slider(value = cutoff, onValueChange = Prefs::setCutoffHz, valueRange = 100f..2000f)
            Text(stringResource(R.string.tuning_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            Divider()
            Section(R.string.section_about)
            Text(
                stringResource(R.string.about_text, BuildConfig.VERSION_NAME),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun ThemeOption(label: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
    }
}

@Composable
private fun AlgoOption(title: Int, desc: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MicOption(title: Int, desc: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).selectable(
            selected = checked, enabled = enabled, role = Role.Checkbox, onClick = { onChange(!checked) },
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}
