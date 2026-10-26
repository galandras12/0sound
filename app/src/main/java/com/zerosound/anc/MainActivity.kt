package com.zerosound.anc

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.zerosound.anc.ui.MainScreen
import com.zerosound.anc.ui.SettingsScreen
import com.zerosound.anc.ui.ZeroSoundTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        // Fresh launch: the switch must always start in the OFF position.
        if (savedInstanceState == null) AncService.stop(this)
        enableEdgeToEdge()

        setContent {
            val theme by Prefs.themeMode.collectAsState()
            val running by AncService.running.collectAsState()
            val message by AncService.message.collectAsState()
            var inSettings by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(message) {
                message?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    AncService.clearMessage()
                }
            }

            var pendingCalibration by rememberSaveable { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                if (result[Manifest.permission.RECORD_AUDIO] == true) {
                    if (pendingCalibration) tryCalibrate() else tryStart()
                    pendingCalibration = false
                } else {
                    pendingCalibration = false
                    Toast.makeText(this, R.string.msg_need_mic, Toast.LENGTH_LONG).show()
                }
            }

            ZeroSoundTheme(theme) {
                if (inSettings) {
                    BackHandler { inSettings = false }
                    SettingsScreen(
                        onBack = { inSettings = false },
                        onCalibrate = {
                            if (hasMicPermission()) {
                                tryCalibrate()
                            } else {
                                pendingCalibration = true
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                    )
                } else {
                    MainScreen(
                        running = running,
                        onToggle = { on ->
                            if (!on) {
                                AncService.stop(this)
                            } else if (hasMicPermission()) {
                                tryStart()
                            } else {
                                val perms = buildList {
                                    add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            }
                        },
                        onOpenSettings = { inSettings = true },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !Prefs.runInBackground.value && AncService.running.value) {
            AncService.stop(this)
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun tryCalibrate() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (!AncService.hasHeadphones(am)) {
            Toast.makeText(this, R.string.msg_need_headphones, Toast.LENGTH_LONG).show()
            return
        }
        AncService.stop(this) // free the audio path
        Calibrator.run()
    }

    private fun tryStart() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (!AncService.hasHeadphones(am)) {
            Toast.makeText(this, R.string.msg_need_headphones, Toast.LENGTH_LONG).show()
            return
        }
        AncService.start(this)
    }
}
