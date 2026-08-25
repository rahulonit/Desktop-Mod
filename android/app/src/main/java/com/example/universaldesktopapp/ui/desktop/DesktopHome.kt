package com.example.universaldesktopapp.ui.desktop

import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.universaldesktopapp.ui.apps.BrowserApp
import com.example.universaldesktopapp.ui.apps.FileManagerApp
import com.example.universaldesktopapp.ui.apps.NotesApp
import com.example.universaldesktopapp.usb.UsbService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DesktopApp(val title: String, val icon: String, val content: @Composable () -> Unit)

@Composable
fun DesktopHome() {
    val usbConnected by UsbService.isReceiverConnected.collectAsState()
    val windows = remember { mutableStateListOf<WindowState>() }
    var startOpen by remember { mutableStateOf(false) }
    var controllerOpen by remember { mutableStateOf(false) }
    var nextZ by remember { mutableIntStateOf(1) }
    val apps = remember {
        listOf(
            DesktopApp("Files", "📁") { FileManagerApp() },
            DesktopApp("Browser", "🌐") { BrowserApp() },
            DesktopApp("Notes", "📝") { NotesApp() },
            DesktopApp("Device", "📱") { DeviceApp() },
        )
    }

    fun openApp(app: DesktopApp) {
        val existing = windows.indexOfFirst { it.title == app.title }
        nextZ += 1
        if (existing >= 0) windows[existing] = windows[existing].copy(minimized = false, zIndex = nextZ)
        else windows += WindowState(title = app.title, icon = app.icon, zIndex = nextZ, content = app.content)
        startOpen = false
    }
    fun updateWindow(updated: WindowState) {
        val index = windows.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            nextZ += 1
            windows[index] = updated.copy(zIndex = nextZ)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF164E63), Color(0xFF0E7490)))
        ),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            apps.forEach { app -> DesktopIcon(app) { openApp(app) } }
        }

        WindowManager(windows, ::updateWindow) { closing -> windows.removeAll { it.id == closing.id } }

        if (startOpen) StartMenu(apps, ::openApp, Modifier.align(Alignment.BottomStart).padding(bottom = 58.dp, start = 8.dp))

        Taskbar(
            windows = windows,
            onStart = { startOpen = !startOpen },
            onWindow = ::updateWindow,
            onController = { controllerOpen = true },
            usbConnected = usbConnected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (controllerOpen) ControllerSheet { controllerOpen = false }
    }
}

@Composable
private fun DesktopIcon(app: DesktopApp, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(app.icon, style = MaterialTheme.typography.headlineLarge)
        Text(app.title, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StartMenu(apps: List<DesktopApp>, onOpen: (DesktopApp) -> Unit, modifier: Modifier) {
    Card(modifier.width(300.dp).height(390.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF21E293B))) {
        Column(Modifier.padding(18.dp)) {
            Text("Universal Mobile Desktop", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Connect your phone. Get a desktop.", color = Color(0xFF94A3B8))
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFF475569))
            LazyColumn { items(apps) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(app) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) { Text(app.icon); Text(app.title, color = Color.White) }
            } }
        }
    }
}

@Composable
private fun Taskbar(
    windows: List<WindowState>, onStart: () -> Unit, onWindow: (WindowState) -> Unit,
    onController: () -> Unit, usbConnected: Boolean, modifier: Modifier,
) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    Row(
        modifier.fillMaxWidth().height(54.dp).background(Color(0xF2111827)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(onClick = onStart, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("◆  Start") }
        windows.forEach { window ->
            TextButton(onClick = { onWindow(window.copy(minimized = !window.minimized)) }) {
                Text("${window.icon} ${window.title}", color = if (window.minimized) Color.Gray else Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onController) { Text("⌁ Controller", color = Color.White) }
        Text(
            if (usbConnected) "● USB connected" else "● USB disconnected",
            color = if (usbConnected) Color(0xFF4ADE80) else Color(0xFFF87171),
        )
        Text(clock, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
private fun DeviceApp() {
    val context = LocalContext.current
    val battery = remember { context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device compatibility", style = MaterialTheme.typography.headlineSmall)
        StatusRow("Android desktop shell", "Ready")
        StatusRow("Battery", "$battery%")
        StatusRow("USB receiver mode", "Prototype")
        StatusRow("Wireless receiver", "Planned")
        StatusRow("Native external display", "Device dependent")
        HorizontalDivider()
        Text("This build provides the desktop shell and controller. Encoded video transport and trusted-device pairing are the next protocol milestones.")
    }
}

@Composable private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ControllerSheet(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth(0.88f).height(420.dp).clickable(enabled = false) {}, colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
            Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Desktop Controller", color = Color.White, style = MaterialTheme.typography.titleLarge); Text("● Session ready", color = Color(0xFF4ADE80)) }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Surface(Modifier.fillMaxWidth().weight(1f).padding(vertical = 14.dp), color = Color(0xFF1E293B), shape = MaterialTheme.shapes.large) {
                    Box(contentAlignment = Alignment.Center) { Text("TRACKPAD\nMove • tap • two-finger scroll", color = Color(0xFF94A3B8)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button({}, Modifier.weight(1f)) { Text("Left click") }
                    OutlinedButton({}, Modifier.weight(1f)) { Text("Right click") }
                    OutlinedButton({}, Modifier.weight(1f)) { Text("Keyboard") }
                }
            }
        }
    }
}
