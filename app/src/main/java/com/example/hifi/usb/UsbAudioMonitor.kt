package com.example.hifi.usb

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Android reports about a connected USB output. These are the device's
 * advertised capabilities, not a statement about the active stream.
 * Empty lists mean Android did not report them.
 */
data class UsbOutput(
    val name: String,
    val sampleRatesHz: List<Int>,
    val channelCounts: List<Int>
) {
    val maxSampleRateHz: Int? get() = sampleRatesHz.maxOrNull()
}

class UsbAudioMonitor(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _output = MutableStateFlow<UsbOutput?>(null)
    val output: StateFlow<UsbOutput?> = _output.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() = audioManager.unregisterAudioDeviceCallback(callback)

    private fun refresh() {
        val usb = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        _output.value = usb?.let {
            UsbOutput(
                name = it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "USB Audio Device",
                sampleRatesHz = it.sampleRates.toList().sorted(),
                channelCounts = it.channelCounts.toList().sorted()
            )
        }
    }
}
