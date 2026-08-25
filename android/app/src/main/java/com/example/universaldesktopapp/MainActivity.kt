package com.example.universaldesktopapp

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.universaldesktopapp.theme.UniversalDesktopAppTheme
import com.example.universaldesktopapp.theme.ThemeController
import com.example.universaldesktopapp.usb.UsbService
import com.example.universaldesktopapp.connection.ConnectionMonitor
import com.example.universaldesktopapp.connection.ConnectionSnapshot
import com.example.universaldesktopapp.connection.WirelessReceiver
import com.example.universaldesktopapp.engine.DesktopPresentation

class MainActivity : ComponentActivity() {
  private enum class ConnectionMethod { USB, EXTERNAL_DISPLAY, WIFI }
  private var nativePresentation: DesktopPresentation? = null
  private var nativeDisplayId: Int? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ThemeController.initialize(applicationContext)

    startService(Intent(this, UsbService::class.java))
    ConnectionMonitor.start(applicationContext)

    setContent {
      UniversalDesktopAppTheme { 
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
          val connection by ConnectionMonitor.snapshot.collectAsStateWithLifecycle()
          LaunchedEffect(connection.externalDisplays) {
            connection.externalDisplays.firstOrNull { it.supportsPresentation }?.let { startNativeDesktop(it.id) }
          }
          ConnectionHubScreen(connection)
        } 
      }
    }
  }

  @Composable
  private fun ConnectionHubScreen(connection: ConnectionSnapshot) {
    var selectedMethod by remember { mutableStateOf<ConnectionMethod?>(null) }
    selectedMethod?.let { method ->
      ConnectionGuidePage(connection, method) { selectedMethod = null }
      return
    }
    val receiverConnected = connection.receiverConnected
    val bestStatus = when {
      receiverConnected -> "${connection.receiverTransport ?: "Desktop"} connected"
      connection.externalDisplays.any { it.supportsPresentation } -> "External display available"
      connection.wirelessReceivers.isNotEmpty() -> "Wireless receiver available"
      connection.usbCableConnected -> "USB connected • receiver not found"
      else -> "Searching for displays"
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val wide = maxWidth >= 840.dp
      val pagePadding = if (wide) 28.dp else 18.dp
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pagePadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 14.dp),
      ) {
        if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          AppIdentity(Modifier.weight(1f))
          ConnectionStatus(receiverConnected, bestStatus)
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          AppIdentity(Modifier.fillMaxWidth())
          ConnectionStatus(receiverConnected, bestStatus)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
          if (wide) Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroCopy(receiverConnected, Modifier.weight(1f))
            if (receiverConnected) Button(onClick = { UsbService.disconnectReceiver() }) { Text("Disconnect") }
          } else Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroCopy(receiverConnected, Modifier.fillMaxWidth())
            if (receiverConnected) Button(onClick = { UsbService.disconnectReceiver() }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect PC") }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text("Connect to a display", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text("Choose the method that works with your device", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          ConnectionMethods(connection, true) { selectedMethod = it }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          ConnectionMethods(connection, false) { selectedMethod = it }
        }

        if (connection.wirelessReceivers.isNotEmpty()) NearbyReceivers(connection.wirelessReceivers)

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
          if (wide) Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Setup and permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PermissionButtons()
          } else Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Setup and permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PermissionButtons(stacked = true)
          }
        }
        Text("The external desktop is private and independent. Your phone screen is never mirrored.", Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
      }
    }
  }

  @Composable private fun AppIdentity(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
      Surface(Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(15.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("D", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
      }
      Column(Modifier.padding(start = 13.dp)) {
        Text("Desktop Mod", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your phone. A real desktop.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }

  @Composable private fun ConnectionStatus(connected: Boolean, label: String) {
    Surface(color = if (connected) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
      Text("●  $label", Modifier.padding(horizontal = 16.dp, vertical = 9.dp), color = if (connected) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
  }

  @Composable private fun HeroCopy(connected: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(if (connected) "Your desktop is ready" else "Turn this phone into a desktop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text(if (connected) "The independent desktop is running on your PC. Keep using your phone normally." else "Connect a PC, monitor, or TV. The desktop appears there while this phone remains usable.", color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
  }

  @Composable private fun RowScope.ConnectionMethods(connection: ConnectionSnapshot, weighted: Boolean, onSelect: (ConnectionMethod) -> Unit) {
    val first = if (weighted) Modifier.weight(1f) else Modifier.fillMaxWidth()
    ConnectionCards(connection, first, onSelect)
  }

  @Composable private fun ColumnScope.ConnectionMethods(connection: ConnectionSnapshot, weighted: Boolean, onSelect: (ConnectionMethod) -> Unit) {
    ConnectionCards(connection, Modifier.fillMaxWidth(), onSelect)
  }

  @Composable private fun ConnectionCards(connection: ConnectionSnapshot, modifier: Modifier, onSelect: (ConnectionMethod) -> Unit) {
    val usbConnected = connection.receiverConnected && connection.receiverTransport == "USB"
    val usbStatus = when {
      usbConnected -> "Connected"
      connection.usbCableConnected -> "Receiver Not Found"
      else -> "Searching"
    }
    val external = connection.externalDisplays.firstOrNull()
    val externalStatus = when {
      external == null -> "Searching"
      external.supportsPresentation -> "Available • ${external.name}"
      else -> "Mirror Only • ${external.name}"
    }
    val wirelessConnected = connection.receiverConnected && connection.receiverTransport == "Wireless"
    val wirelessStatus = when {
      wirelessConnected -> "Connected"
      connection.wirelessReceivers.isNotEmpty() -> "Available • ${connection.wirelessReceivers.size} found"
      else -> "Searching"
    }
    ConnectionCard(modifier, "USB", "USB to Windows PC", "Recommended • fastest", "Connect a USB data cable. The Windows receiver is detected automatically after it connects.", usbStatus, usbConnected, "View setup") { onSelect(ConnectionMethod.USB) }
    ConnectionCard(modifier, "HDMI", "USB-C / HDMI monitor", "Native external display", "Android checks for a real secondary presentation display. Mirror-only outputs are not treated as desktop mode.", externalStatus, external?.supportsPresentation == true, "View setup") { onSelect(ConnectionMethod.EXTERNAL_DISPLAY) }
    ConnectionCard(modifier, "Wi-Fi", "Wi-Fi to PC or TV", "Nearby receiver discovery", "Compatible Desktop Mod receivers on the same network appear below. Select one to request a wireless session.", wirelessStatus, wirelessConnected, "View setup") { onSelect(ConnectionMethod.WIFI) }
  }

  @Composable private fun NearbyReceivers(receivers: List<WirelessReceiver>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Nearby wireless receivers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        receivers.forEach { receiver ->
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(receiver.name, fontWeight = FontWeight.SemiBold)
              Text("${receiver.platform} • ${receiver.address}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { ConnectionMonitor.requestWirelessConnection(receiver) }) { Text("Connect") }
          }
        }
      }
    }
  }

  @Composable
  private fun ConnectionGuidePage(connection: ConnectionSnapshot, method: ConnectionMethod, onBack: () -> Unit) {
    val external = connection.externalDisplays.firstOrNull()
    val title = when (method) {
      ConnectionMethod.USB -> "USB to Windows PC"
      ConnectionMethod.EXTERNAL_DISPLAY -> "USB-C / HDMI display"
      ConnectionMethod.WIFI -> "Wi-Fi receiver"
    }
    val status = when (method) {
      ConnectionMethod.USB -> when {
        connection.receiverConnected && connection.receiverTransport == "USB" -> "Connected"
        connection.usbCableConnected -> "Receiver Not Found"
        else -> "Searching"
      }
      ConnectionMethod.EXTERNAL_DISPLAY -> when {
        external == null -> "Searching"
        external.supportsPresentation -> "Available"
        else -> "Mirror Only"
      }
      ConnectionMethod.WIFI -> when {
        connection.receiverConnected && connection.receiverTransport == "Wireless" -> "Connected"
        connection.wirelessReceivers.isNotEmpty() -> "Available"
        else -> "Searching"
      }
    }
    val steps = when (method) {
      ConnectionMethod.USB -> listOf(
        "Use a USB data cable—not a charge-only cable—to connect the phone to the Windows PC.",
        "Enable Developer options and USB debugging on this phone.",
        "Open Desktop Mod Receiver on Windows. It configures ADB port forwarding automatically.",
        "Accept the USB debugging authorization prompt on the phone and choose Always allow for this computer if desired.",
        "Press Connect in the Windows receiver. The independent desktop will appear on the PC.",
      )
      ConnectionMethod.EXTERNAL_DISPLAY -> listOf(
        "Confirm that the phone supports video output through USB-C DisplayPort Alt Mode or its manufacturer desktop feature.",
        "Connect a compatible USB-C to HDMI/DisplayPort adapter, dock, monitor, TV, or projector.",
        "Select the correct source input on the external display.",
        "Wait for Android to expose a secondary presentation display. Desktop Mod starts Native Desktop automatically.",
        "Connect a Bluetooth or USB keyboard and mouse for desktop input.",
      )
      ConnectionMethod.WIFI -> listOf(
        "Connect the phone and receiver device to the same local Wi-Fi network.",
        "Open the latest Desktop Mod Receiver on the computer and allow it through the private-network firewall prompt.",
        "Wait for the receiver name to appear below. Discovery uses UDP port 50505.",
        "Select Connect beside the receiver. The computer will connect directly to the phone on TCP port 5000.",
        "Keep both apps open while pairing and use a trusted private network.",
      )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val wide = maxWidth >= 760.dp
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 32.dp else 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          OutlinedButton(onClick = onBack) { Text("Back") }
          Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Connection guide", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          GuideStatus(status)
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
          Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(when (method) {
              ConnectionMethod.USB -> if (status == "Connected") "USB Desktop is running" else "Set up USB Desktop"
              ConnectionMethod.EXTERNAL_DISPLAY -> if (status == "Mirror Only") "This output supports mirroring only" else "Set up Native Desktop"
              ConnectionMethod.WIFI -> "Connect without a USB cable"
            }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(when (method) {
              ConnectionMethod.USB -> "Best performance and lowest latency for a Windows computer."
              ConnectionMethod.EXTERNAL_DISPLAY -> "A true secondary Android display is required for an independent desktop."
              ConnectionMethod.WIFI -> "Desktop Mod searches only for compatible receivers on your local network."
            }, color = MaterialTheme.colorScheme.onPrimaryContainer)
          }
        }
        Text("How to connect", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        steps.forEachIndexed { index, step -> GuideStep(index + 1, step) }
        when (method) {
          ConnectionMethod.USB -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.weight(1f)) { Text("Open USB debugging") }
              if (connection.receiverConnected && connection.receiverTransport == "USB") Button(onClick = { UsbService.disconnectReceiver() }, Modifier.weight(1f)) { Text("Disconnect USB Desktop") }
            }
            if (status == "Receiver Not Found") GuideNotice("The cable is detected, but Desktop Mod Receiver has not connected. Open the Windows receiver, accept the authorization prompt, and press Connect.", true)
          }
          ConnectionMethod.EXTERNAL_DISPLAY -> {
            if (external?.supportsPresentation == true) Button(onClick = { startNativeDesktop(external.id) }, Modifier.fillMaxWidth()) { Text("Start Native Desktop on ${external.name}") }
            else if (status == "Mirror Only") GuideNotice("Android exposes ${external?.name ?: "this display"} as mirror-only. Desktop Mod cannot place a separate workspace on it.", true)
            else GuideNotice("No secondary presentation display is currently detected. Some USB-C ports support charging and data but not video output.", false)
          }
          ConnectionMethod.WIFI -> {
            if (connection.wirelessReceivers.isEmpty()) GuideNotice("Searching for compatible receivers. Confirm both devices are on the same subnet and allow UDP 50505 and TCP 5000 through the receiver firewall.", false)
            else NearbyReceivers(connection.wirelessReceivers)
          }
        }
        Text("Your phone remains usable and is not mirrored while Desktop Mode is active.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
      }
    }
  }

  @Composable private fun GuideStatus(status: String) {
    val error = status == "Mirror Only" || status == "Receiver Not Found" || status == "Unsupported"
    val positive = status == "Connected" || status == "Available"
    Surface(color = when { error -> MaterialTheme.colorScheme.errorContainer; positive -> Color(0xFFDCFCE7); else -> MaterialTheme.colorScheme.surfaceVariant }, shape = RoundedCornerShape(18.dp)) {
      Text(status, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = when { error -> MaterialTheme.colorScheme.onErrorContainer; positive -> Color(0xFF166534); else -> MaterialTheme.colorScheme.onSurfaceVariant }, fontWeight = FontWeight.SemiBold)
    }
  }

  @Composable private fun GuideStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Surface(Modifier.size(34.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(11.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
      }
      Text(text, Modifier.padding(start = 13.dp, top = 5.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
  }

  @Composable private fun GuideNotice(text: String, error: Boolean) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
      Text(text, Modifier.fillMaxWidth().padding(16.dp), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }

  @Composable private fun PermissionButtons(stacked: Boolean = false) {
    val modifier = if (stacked) Modifier.fillMaxWidth() else Modifier
    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, modifier = modifier) { Text("USB debugging") }
    if (!stacked) Spacer(Modifier.width(10.dp))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
      Button(onClick = { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }, modifier = modifier) { Text("Grant file access") }
    }
  }

  @Composable
  private fun ConnectionCard(
    modifier: Modifier,
    badge: String,
    title: String,
    subtitle: String,
    steps: String,
    status: String,
    active: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
  ) {
    Card(modifier.heightIn(min = 238.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f))) {
      Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp)) {
          Text(badge, Modifier.padding(horizontal = 11.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(steps, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(status, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = when {
          status.startsWith("Connected") -> Color(0xFF16853D)
          status.startsWith("Mirror") || status.startsWith("Unsupported") || status.startsWith("Receiver") -> MaterialTheme.colorScheme.error
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        })
        if (actionLabel != null) Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
      }
    }
  }

  private fun startNativeDesktop(displayId: Int) {
    if (nativeDisplayId == displayId && nativePresentation?.isShowing == true) return
    val display = getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId) ?: return
    if (display.displayId == android.view.Display.DEFAULT_DISPLAY || display.name == "DesktopMod") return
    runCatching {
      nativePresentation?.dismiss()
      nativePresentation = DesktopPresentation(this, display).also { it.show() }
      nativeDisplayId = displayId
    }
  }

  override fun onDestroy() {
    nativePresentation?.dismiss()
    nativePresentation = null
    nativeDisplayId = null
    super.onDestroy()
  }

}
