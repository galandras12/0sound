package com.zerosound.anc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Foreground service that owns the [AncEngine]. Required to keep the mic alive in the background. */
class AncService : Service() {
    private val engine = AncEngine()
    private lateinit var audioManager: AudioManager

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (!hasHeadphones(audioManager)) {
                _message.value = R.string.msg_headphones_removed
                shutDown()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (!hasHeadphones(audioManager)) {
            _message.value = R.string.msg_need_headphones
            shutDown()
        } else if (!engine.start()) {
            _message.value = R.string.msg_engine_failed
            shutDown()
        } else {
            _message.value = null
            _running.value = true
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        engine.stop()
        _running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun shutDown() {
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, AncService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val ACTION_STOP = "com.zerosound.anc.STOP"
        private const val CHANNEL_ID = "anc"
        private const val NOTIF_ID = 1

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        /** String resource id of the last user-facing problem, or null. */
        private val _message = MutableStateFlow<Int?>(null)
        val message: StateFlow<Int?> = _message

        fun clearMessage() {
            _message.value = null
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AncService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AncService::class.java))
            _running.value = false
        }

        fun hasHeadphones(am: AudioManager): Boolean =
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }

        private val HEADPHONE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
