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
import com.zerosound.anc.Prefs
import com.zerosound.anc.R
import com.zerosound.anc.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val bg by Prefs.runInBackground.collectAsState()
    val theme by Prefs.themeMode.collectAsState()
    val strength by Prefs.strength.collectAsState()
    val cutoff by Prefs.cutoffHz.collectAsState()

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
