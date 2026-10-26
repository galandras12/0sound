package com.zerosound.anc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Recognises output devices ANC may use: USB, 3.5 mm jack and Bluetooth audio with media mode. */
object AudioRoutes {
    private val allowed: Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES) // 3.5 mm jack
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET) // 3.5 mm jack with mic
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_DEVICE)
        add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) // Bluetooth media mode
        add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    fun isAllowed(device: AudioDeviceInfo?): Boolean = device != null && device.type in allowed

    /** First connected supported output device, or null (phone speaker, earpiece, HDMI, BT call mode are rejected). */
    fun find(am: AudioManager): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in allowed }

    fun label(context: Context, d: AudioDeviceInfo): String {
        val kind = when (d.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.route_jack
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.string.route_usb
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> R.string.route_bt
            AudioDeviceInfo.TYPE_HEARING_AID -> R.string.route_hearing
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.route_speaker
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> R.string.route_earpiece
            else -> if (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET || d.type == 30) R.string.route_ble else R.string.route_other
        }
        val name = d.productName?.toString().orEmpty()
        val base = context.getString(kind)
        return if (name.isNotBlank() && d.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "$base — $name" else base
    }
}
