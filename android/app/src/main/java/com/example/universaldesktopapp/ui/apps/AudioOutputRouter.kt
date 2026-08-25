package com.example.universaldesktopapp.ui.apps

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build

internal object AudioOutputRouter {
    fun routeToBestExternalOutput(context: Context, player: MediaPlayer): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "System default"
        val audioManager = context.getSystemService(AudioManager::class.java)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferredTypes = listOf(
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        )
        val selected = preferredTypes.firstNotNullOfOrNull { type -> outputs.firstOrNull { it.type == type } }
            ?: return "Phone / system default"
        val applied = runCatching { player.setPreferredDevice(selected) }.getOrDefault(false)
        if (!applied) return "Phone / system default"
        val product = selected.productName?.toString()?.takeIf { it.isNotBlank() }
        return product ?: when (selected.type) {
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI display"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio"
            else -> "External audio"
        }
    }
}
