package com.example.universaldesktopapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.universaldesktopapp.connection.ConnectionSnapshot
import com.example.universaldesktopapp.connection.WirelessReceiver

private val Navy = Color(0xFF020719)
private val Glass = Color(0x8A102B59)
private val GlassStrong = Color(0xB317315F)
private val Border = Color(0x4D78A8FF)
private val Secondary = Color(0xFFB7C8E7)
private val Blue = Color(0xFF3B82F6)
private val Violet = Color(0xFF7357F6)

@Composable
fun PremiumConnectionHub(
  connection: ConnectionSnapshot,
  pairingCode: String,
  onUsb: () -> Unit,
  onWifi: () -> Unit,
  onDisplay: () -> Unit,
  onDisconnect: () -> Unit,
  onReceiverConnect: (WirelessReceiver, String) -> Unit,
) {
  val usbReceivers = connection.wirelessReceivers.filter { it.transport == "USB" }
  val wifiReceivers = connection.wirelessReceivers.filter { it.transport == "Wireless" }
  val connected = connection.receiverConnected
  val status = when {
    connected -> "${connection.receiverTransport ?: "Desktop"} connected"
    connection.usbDebuggingEnabled -> "Developer Mode ready"
    connection.usbCableConnected -> "USB detected"
    connection.wirelessReceivers.isNotEmpty() -> "Receiver available"
    else -> "Searching for receivers"
  }
  BoxWithConstraints(
    Modifier.fillMaxSize().background(
      Brush.radialGradient(listOf(Color(0xFF153E7B), Color(0xFF07142F), Navy), radius = 1450f)
    )
  ) {
    val tablet = maxWidth >= 720.dp
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (tablet) 28.dp else 16.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      GlassSurface(radius = 22, modifier = Modifier.fillMaxWidth()) {
        if (tablet) Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
          MobileIdentity(Modifier.weight(1f)); StatusPill(status, connected)
        } else Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          MobileIdentity(Modifier.fillMaxWidth()); StatusPill(status, connected)
        }
      }

      GlassSurface(radius = 28, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(if (tablet) 30.dp else 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Image(painterResource(R.drawable.app_identity), null, Modifier.size(if (tablet) 108.dp else 88.dp), contentScale = ContentScale.Fit)
          Text(if (connected) "Desktop Ready" else "Connect Desktop Mode", color = Color.White, fontSize = if (tablet) 34.sp else 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
          Text(if (connected) "Your independent desktop is ready on the receiver." else "Choose the best available connection. Your phone remains usable.", color = Secondary, fontSize = 16.sp, textAlign = TextAlign.Center)
          Surface(color = Color(0x401A63C8), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
            Text("Pairing code  $pairingCode", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = Color(0xFFA9CDFF), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
          }
          if (connected) Button(onClick = onDisconnect, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB52B45))) { Text("Disconnect") }
        }
      }

      Text("Connection methods", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
      if (tablet) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          MethodCard(Modifier.weight(1f), R.drawable.connection_usb, "USB", usbStatus(connection, usbReceivers), "Fast, stable connection using Developer Mode or USB tethering.", Blue, onUsb)
          MethodCard(Modifier.weight(1f), R.drawable.connection_wifi, "Wi-Fi", wifiStatus(connection, wifiReceivers), "Connect wirelessly on the same trusted private network.", Violet, onWifi)
          MethodCard(Modifier.weight(1f), R.drawable.connection_hdmi, "External display", displayStatus(connection), "USB-C or HDMI desktop output where supported.", Color(0xFF8B5CF6), onDisplay)
        }
      } else {
        MethodCard(Modifier.fillMaxWidth(), R.drawable.connection_usb, "USB", usbStatus(connection, usbReceivers), "Fast, stable connection using Developer Mode or USB tethering.", Blue, onUsb)
        MethodCard(Modifier.fillMaxWidth(), R.drawable.connection_wifi, "Wi-Fi", wifiStatus(connection, wifiReceivers), "Connect wirelessly on the same trusted private network.", Violet, onWifi)
        MethodCard(Modifier.fillMaxWidth(), R.drawable.connection_hdmi, "External display", displayStatus(connection), "USB-C or HDMI desktop output where supported.", Color(0xFF8B5CF6), onDisplay)
      }

      if (usbReceivers.isNotEmpty()) ReceiverList("Available USB receivers", usbReceivers, "USB", onReceiverConnect)
      if (wifiReceivers.isNotEmpty()) ReceiverList("Available Wi-Fi receivers", wifiReceivers, "Wireless", onReceiverConnect)

      GlassSurface(radius = 18, modifier = Modifier.fillMaxWidth()) {
        Text("ⓘ  This creates a separate desktop. It does not mirror your phone screen.", Modifier.padding(16.dp), color = Color(0xFFA9CDFF), textAlign = TextAlign.Center)
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable private fun MobileIdentity(modifier: Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Image(painterResource(R.drawable.app_identity), "Desktop Mod", Modifier.size(52.dp), contentScale = ContentScale.Fit)
    Column(Modifier.padding(start = 12.dp)) { Text("Desktop Mod", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Your phone. A real desktop.", color = Secondary, fontSize = 13.sp) }
  }
}

@Composable private fun StatusPill(text: String, connected: Boolean) {
  Surface(color = Color(0x7A07152F), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, if (connected) Color(0x6634D399) else Border)) {
    Text("●  $text", Modifier.padding(horizontal = 15.dp, vertical = 10.dp), color = if (connected) Color(0xFF52DFA7) else Color(0xFF8FC1FF), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
  }
}

@Composable private fun MethodCard(modifier: Modifier, image: Int, title: String, status: String, description: String, accent: Color, onClick: () -> Unit) {
  Box(modifier.shadow(18.dp, RoundedCornerShape(20.dp), ambientColor = accent.copy(.18f), spotColor = accent.copy(.18f)).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(GlassStrong, Color(0xA70A193B)))).border(1.dp, accent.copy(.38f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(18.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(.15f)), contentAlignment = Alignment.Center) { Image(painterResource(image), null, Modifier.size(48.dp), contentScale = ContentScale.Fit) }
        Column(Modifier.padding(start = 13.dp).weight(1f)) { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(status, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
      }
      Text(description, color = Secondary, fontSize = 14.sp, lineHeight = 20.sp)
      Text("View setup  →", color = Color(0xFFAED1FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
  }
}

@Composable private fun ReceiverList(title: String, receivers: List<WirelessReceiver>, transport: String, connect: (WirelessReceiver, String) -> Unit) {
  GlassSurface(radius = 20, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
      receivers.forEach { receiver -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(receiver.name, color = Color.White, fontWeight = FontWeight.SemiBold); Text("${receiver.platform} • ${receiver.address}", color = Secondary, fontSize = 12.sp) }
        Button(onClick = { connect(receiver, transport) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Connect") }
      } }
    }
  }
}

@Composable private fun GlassSurface(radius: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(modifier.shadow(24.dp, RoundedCornerShape(radius.dp), ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.25f)).clip(RoundedCornerShape(radius.dp)).background(Brush.linearGradient(listOf(Glass, Color(0x7610193E)))).border(1.dp, Border, RoundedCornerShape(radius.dp))) { content() }
}

private fun usbStatus(connection: ConnectionSnapshot, receivers: List<WirelessReceiver>) = when { connection.receiverConnected && connection.receiverTransport == "USB" -> "Connected"; connection.usbDebuggingEnabled -> "Developer Mode ready"; connection.developerModeEnabled -> "Enable USB debugging"; connection.usbTetheringActive && receivers.isNotEmpty() -> "${receivers.size} receiver found"; connection.usbCableConnected -> "Enable tethering"; else -> "Searching…" }
private fun wifiStatus(connection: ConnectionSnapshot, receivers: List<WirelessReceiver>) = when { connection.receiverConnected && connection.receiverTransport == "Wireless" -> "Connected"; receivers.isNotEmpty() -> "${receivers.size} receiver found"; else -> "Searching…" }
private fun displayStatus(connection: ConnectionSnapshot) = connection.externalDisplays.firstOrNull()?.let { if (it.supportsPresentation) "${it.name} available" else "Mirror only" } ?: "Checking support…"
