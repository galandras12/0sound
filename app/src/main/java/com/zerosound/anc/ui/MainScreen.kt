package com.zerosound.anc.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.zerosound.anc.Prefs
import com.zerosound.anc.R
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    running: Boolean,
    outputLabel: String?,
    micCount: Int,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val latency by Prefs.latencyMs.collectAsState()
    val adaptive by Prefs.adaptive.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NoticeRow(color = WarnYellow, text = stringResource(R.string.warning_top))
            Spacer(Modifier.height(40.dp))
            BigSwitch(checked = running, onCheckedChange = onToggle)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (running) R.string.state_on else R.string.state_off),
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            if (outputLabel != null) {
                Text(
                    stringResource(R.string.output_detected, outputLabel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                if (running && micCount > 0) {
                    Text(
                        stringResource(R.string.mics_active, micCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.output_none),
                    color = WarnYellow,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (adaptive && latency <= 0f) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.hint_not_calibrated),
                    color = WarnYellow,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        NoticeRow(
            color = DangerRed,
            text = stringResource(R.string.warning_bottom),
            modifier = Modifier.padding(bottom = 16.dp),
            bold = true,
        )
    }
}

@Composable
private fun NoticeRow(color: Color, text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", color = Color.White.takeIf { color == DangerRed } ?: Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text,
            modifier = Modifier.weight(1f, fill = false),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Huge pill-shaped ON/OFF slider: tap it or drag the thumb. */
@Composable
fun BigSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackW = 300.dp
    val trackH = 150.dp
    val pad = 12.dp
    val thumb = trackH - pad * 2
    val density = LocalDensity.current
    val travel = with(density) { (trackW - thumb - pad * 2).toPx() }
    val padPx = with(density) { pad.toPx() }

    var drag by remember { mutableStateOf<Float?>(null) }
    val animated by animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    val fraction = drag?.let { (it / travel).coerceIn(0f, 1f) } ?: animated

    val offColor = MaterialTheme.colorScheme.surfaceVariant
    val label = if (checked) "ON" else "OFF"

    Box(
        Modifier
            .size(trackW, trackH)
            .clip(CircleShape)
            .background(lerp(offColor, OnGreen, fraction))
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = label
                onClick { onCheckedChange(!checked); true }
            }
            .pointerInput(checked) { detectTapGestures { onCheckedChange(!checked) } }
            .pointerInput(checked) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = if (checked) travel else 0f },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        drag = ((drag ?: 0f) + delta).coerceIn(0f, travel)
                    },
                    onDragEnd = {
                        val on = (drag ?: 0f) > travel / 2
                        drag = null
                        if (on != checked) onCheckedChange(on)
                    },
                    onDragCancel = { drag = null },
                )
            },
    ) {
        Text(
            "ON",
            Modifier.align(Alignment.CenterStart).padding(start = 44.dp),
            color = Color.White.copy(alpha = fraction),
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            "OFF",
            Modifier.align(Alignment.CenterEnd).padding(end = 36.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f - fraction),
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
        )
        Box(
            Modifier
                .offset { IntOffset((padPx + travel * fraction).roundToInt(), padPx.roundToInt()) }
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
