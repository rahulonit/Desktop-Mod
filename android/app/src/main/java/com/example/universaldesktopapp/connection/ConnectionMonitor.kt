package com.example.universaldesktopapp.connection

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.provider.Settings
import android.view.Display
import com.example.universaldesktopapp.usb.UsbService
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Availability { CONNECTED, AVAILABLE, SEARCHING, UNSUPPORTED, MIRROR_ONLY, RECEIVER_NOT_FOUND }

data class ExternalDisplayInfo(
    val id: Int,
    val name: String,
    val supportsPresentation: Boolean,
)

data class WirelessReceiver(
    val name: String,
    val address: String,
    val platform: String,
    val port: Int,
    val transport: String = "Wireless",
    val lastSeen: Long = System.currentTimeMillis(),
)

data class ConnectionSnapshot(
    val usbCableConnected: Boolean = false,
    val usbTetheringActive: Boolean = false,
    val developerModeEnabled: Boolean = false,
    val usbDebuggingEnabled: Boolean = false,
    val receiverConnected: Boolean = false,
    val receiverTransport: String? = null,
    val externalDisplays: List<ExternalDisplayInfo> = emptyList(),
    val wirelessReceivers: List<WirelessReceiver> = emptyList(),
    val wirelessSearching: Boolean = true,
)

object ConnectionMonitor : DisplayManager.DisplayListener {
    private const val DISCOVERY_PORT = 50505
    private const val DISCOVERY_REQUEST = "DESKTOP_MOD_DISCOVER_V1"
    private var appContext: Context? = null
    private var displayManager: DisplayManager? = null
    private var running = false
    private val receivers = ConcurrentHashMap<String, WirelessReceiver>()
    private val mutableSnapshot = MutableStateFlow(ConnectionSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    fun start(context: Context) {
        if (running) return
        running = true
        appContext = context.applicationContext
        displayManager = context.getSystemService(DisplayManager::class.java).also { it.registerDisplayListener(this, null) }
        refreshLocalSignals()
        thread(name = "DesktopModDiscovery", isDaemon = true) { discoveryLoop() }
    }

    fun stop() {
        if (!running) return
        running = false
        displayManager?.unregisterDisplayListener(this)
        displayManager = null
        appContext = null
        receivers.clear()
        mutableSnapshot.value = ConnectionSnapshot(wirelessSearching = false)
    }

    fun requestConnection(receiver: WirelessReceiver, transport: String) {
        UsbService.nextTransportHint.value = transport
        thread(name = "DesktopModWirelessStart", isDaemon = true) {
            runCatching {
                DatagramSocket().use { socket ->
                    val message = "DESKTOP_MOD_START_V1|$transport"
                    val bytes = message.toByteArray(Charsets.UTF_8)
                    socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(receiver.address), DISCOVERY_PORT))
                }
            }
        }
    }

    fun refreshLocalSignals() {
        val context = appContext ?: return
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val usbCable = plugged == BatteryManager.BATTERY_PLUGGED_USB
        val developerMode = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val usbDebugging = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val usbTethering = networkInterfaces().any { network ->
            network.isUp && !network.isLoopback && listOf("rndis", "usb", "ncm").any { network.name.contains(it, ignoreCase = true) }
        }
        val manager = displayManager
        val presentationIds = manager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.map { it.displayId }?.toSet().orEmpty()
        val external = manager?.displays.orEmpty()
            .filter { it.displayId != Display.DEFAULT_DISPLAY && it.name != "DesktopMod" }
            .map { ExternalDisplayInfo(it.displayId, it.name.ifBlank { "External display" }, it.displayId in presentationIds) }
        val now = System.currentTimeMillis()
        receivers.entries.removeIf { now - it.value.lastSeen > 12_000L }
        mutableSnapshot.value = ConnectionSnapshot(
            usbCableConnected = usbCable,
            usbTetheringActive = usbTethering,
            developerModeEnabled = developerMode,
            usbDebuggingEnabled = usbDebugging,
            receiverConnected = UsbService.isReceiverConnected.value,
            receiverTransport = UsbService.receiverTransport.value,
            externalDisplays = external,
            wirelessReceivers = receivers.values.sortedBy { it.name },
            wirelessSearching = true,
        )
    }

    private fun discoveryLoop() {
        while (running) {
            runCatching {
                DatagramSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.broadcast = true
                    socket.soTimeout = 1200
                    socket.bind(InetSocketAddress(0))
                    val bytes = DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
                    val broadcastTargets = networkInterfaces().flatMap { it.interfaceAddresses.orEmpty() }
                        .mapNotNull { it.broadcast }.distinct().ifEmpty { listOf(InetAddress.getByName("255.255.255.255")) }
                    broadcastTargets.forEach { target ->
                        runCatching { socket.send(DatagramPacket(bytes, bytes.size, target, DISCOVERY_PORT)) }
                    }
                    val deadline = System.currentTimeMillis() + 1500
                    while (System.currentTimeMillis() < deadline) {
                        val buffer = ByteArray(512)
                        val response = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(response)
                            val text = String(response.data, 0, response.length, Charsets.UTF_8)
                            val parts = text.split('|')
                            if (parts.size >= 4 && parts[0] == "DESKTOP_MOD_RECEIVER_V1") {
                                val address = response.address.hostAddress.orEmpty()
                                val item = WirelessReceiver(
                                    parts[1], address, parts[2], parts[3].toIntOrNull() ?: 5000,
                                    transport = classifyTransport(response.address),
                                )
                                receivers[item.address] = item
                            }
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                    }
                }
            }
            refreshLocalSignals()
            Thread.sleep(2500)
        }
    }

    override fun onDisplayAdded(displayId: Int) = refreshLocalSignals()
    override fun onDisplayRemoved(displayId: Int) = refreshLocalSignals()
    override fun onDisplayChanged(displayId: Int) = refreshLocalSignals()

    private fun networkInterfaces(): List<NetworkInterface> =
        runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun classifyTransport(receiverAddress: InetAddress): String {
        val matchingInterface = networkInterfaces().firstOrNull { network ->
            network.interfaceAddresses.orEmpty().any { local ->
                sameSubnet(local.address, receiverAddress, local.networkPrefixLength.toInt())
            }
        }
        val name = matchingInterface?.name.orEmpty()
        return if (listOf("rndis", "usb", "ncm").any { name.contains(it, ignoreCase = true) }) "USB" else "Wireless"
    }

    private fun sameSubnet(first: InetAddress?, second: InetAddress, prefixLength: Int): Boolean {
        val a = first?.address ?: return false
        val b = second.address
        if (a.size != b.size || prefixLength < 0) return false
        var bits = prefixLength
        for (index in a.indices) {
            if (bits <= 0) break
            val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((a[index].toInt() and mask) != (b[index].toInt() and mask)) return false
            bits -= 8
        }
        return true
    }
}
