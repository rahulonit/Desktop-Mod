package com.example.universaldesktopapp.ui.desktop

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.example.universaldesktopapp.engine.DesktopInputBus
import com.example.universaldesktopapp.engine.DesktopMenuRequest
import com.example.universaldesktopapp.ui.apps.BrowserApp
import com.example.universaldesktopapp.ui.apps.FileManagerApp
import com.example.universaldesktopapp.ui.apps.NotesApp
import com.example.universaldesktopapp.ui.apps.ImagePreviewApp
import com.example.universaldesktopapp.ui.apps.VideoPlayerApp
import com.example.universaldesktopapp.ui.apps.MediaKind
import com.example.universaldesktopapp.ui.apps.MediaOpenController
import com.example.universaldesktopapp.ui.apps.CalculatorApp
import com.example.universaldesktopapp.ui.apps.ClockApp
import com.example.universaldesktopapp.ui.apps.AudioPlayerApp
import com.example.universaldesktopapp.ui.apps.DesktopSettingsApp
import com.example.universaldesktopapp.usb.UsbService
import com.example.universaldesktopapp.R
import com.example.universaldesktopapp.theme.ThemeController
import com.example.universaldesktopapp.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class DesktopApp(
    val title: String,
    val icon: String,
    val createdAt: Long = System.nanoTime(),
    val content: @Composable () -> Unit,
)

private val WinBlue = Color(0xFF0067C0)
private val Glass: Color @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = .94f)
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface

@Composable
fun DesktopHome(isPcMode: Boolean = false) {
    val context = LocalContext.current
    val usbConnected by UsbService.isReceiverConnected.collectAsState()
    val windows = remember { mutableStateListOf<WindowState>() }
    var startOpen by remember { mutableStateOf(false) }
    var quickSettingsOpen by remember { mutableStateOf(false) }
    var controllerOpen by remember { mutableStateOf(false) }
    var nextZ by remember { mutableIntStateOf(1) }
    var desktopMenu by remember { mutableStateOf<DesktopMenuRequest?>(null) }
    var iconSize by remember { mutableIntStateOf(1) }
    var sortMode by remember { mutableIntStateOf(0) }
    var wallpaperStyle by remember { mutableIntStateOf(0) }
    var propertiesOpen by remember { mutableStateOf(false) }
    var newItemNumber by remember { mutableIntStateOf(1) }
    var selectedDesktopApp by remember { mutableStateOf<String?>(null) }
    val mediaRequest by MediaOpenController.request.collectAsState()
    val apps = remember {
        mutableStateListOf(
            DesktopApp("File Explorer", "files") { FileManagerApp() },
            DesktopApp("Browser", "browser") { BrowserApp() },
            DesktopApp("Notepad", "notes") { NotesApp() },
            DesktopApp("This Phone", "phone") { DeviceApp() },
            DesktopApp("Image Preview", "image") { ImagePreviewApp() },
            DesktopApp("Video Player", "video") { VideoPlayerApp() },
            DesktopApp("Audio Player", "audio") { AudioPlayerApp() },
            DesktopApp("Calculator", "calculator") { CalculatorApp() },
            DesktopApp("Clock", "clock") { ClockApp() },
            DesktopApp("Settings", "settings") { DesktopSettingsApp() },
        )
    }
    val displayedApps = when (sortMode) {
        1 -> apps.sortedWith(compareBy<DesktopApp> { it.icon }.thenBy { it.title })
        2 -> apps.sortedByDescending { it.createdAt }
        else -> apps.sortedBy { it.title }
    }

    fun openApp(app: DesktopApp) {
        nextZ++
        val existing = windows.indexOfFirst { it.title == app.title }
        if (existing >= 0) windows[existing] = windows[existing].copy(minimized = false, zIndex = nextZ)
        else windows += WindowState(title = app.title, icon = app.icon, zIndex = nextZ, content = app.content)
        startOpen = false
    }
    fun updateWindow(updated: WindowState) {
        val index = windows.indexOfFirst { it.id == updated.id }
        if (index >= 0) { nextZ++; windows[index] = updated.copy(zIndex = nextZ) }
    }
    fun openInstalledApp(app: LaunchableApp) {
        launchInstalledApp(context, app.packageName)
        startOpen = false
    }
    LaunchedEffect(mediaRequest?.id) {
        mediaRequest?.let { request ->
            nextZ++
            windows += WindowState(
                title = request.file.name,
                icon = when (request.kind) { MediaKind.IMAGE -> "image"; MediaKind.VIDEO -> "video"; MediaKind.AUDIO -> "audio" },
                zIndex = nextZ,
                width = 720f,
                height = 480f,
                content = {
                    when (request.kind) {
                        MediaKind.IMAGE -> ImagePreviewApp(request.file)
                        MediaKind.VIDEO -> VideoPlayerApp(request.file)
                        MediaKind.AUDIO -> AudioPlayerApp(request.file)
                    }
                },
            )
            MediaOpenController.consumed(request)
        }
    }
    LaunchedEffect(isPcMode) {
        if (isPcMode) DesktopInputBus.menuRequests.collect { desktopMenu = it }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFFB9E5FF), Color(0xFF5AA8F5), Color(0xFF2859B8), Color(0xFF091B4D)),
            ),
        ),
    ) {
        if (wallpaperStyle == 0) {
            Image(
                painter = painterResource(R.drawable.desktop_wallpaper),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else Box(
            Modifier.fillMaxSize().background(
                if (wallpaperStyle == 1) Brush.linearGradient(listOf(Color(0xFF071A35), Color(0xFF0067C0), Color(0xFF8FD3FF)))
                else Brush.linearGradient(listOf(Color(0xFF25134D), Color(0xFF6A3BB5), Color(0xFFE69ACB)))
            )
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)))
        Box(Modifier.fillMaxSize().clickable {
            startOpen = false
            quickSettingsOpen = false
            desktopMenu = null
            selectedDesktopApp = null
        })
        if (isPcMode) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = listOf(68, 82, 104)[iconSize].dp),
                modifier = Modifier.width(listOf(176, 210, 236)[iconSize].dp).fillMaxHeight().padding(start = 18.dp, top = 18.dp, bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false,
            ) {
                items(displayedApps, key = { it.title }) { app ->
                    DesktopShortcut(
                        app = app,
                        selected = selectedDesktopApp == app.title,
                        onSelect = { selectedDesktopApp = app.title },
                        onOpen = { openApp(app) },
                        compact = true,
                        iconSize = listOf(28, 38, 50)[iconSize],
                    )
                }
            }
        } else {
            Column(Modifier.padding(start = 24.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                displayedApps.forEach { app ->
                    DesktopShortcut(app, selectedDesktopApp == app.title, { selectedDesktopApp = app.title }, { openApp(app) })
                }
            }
        }

        WindowManager(windows, ::updateWindow) { closing -> windows.removeAll { it.id == closing.id } }

        if (startOpen) {
            StartMenu(
                onInstalledApp = ::openInstalledApp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp),
            )
        }
        if (quickSettingsOpen) {
            QuickSettings(
                usbConnected,
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 68.dp),
            )
        }

        WindowsTaskbar(
            windows = windows,
            onStart = { startOpen = !startOpen; quickSettingsOpen = false },
            onWindow = ::updateWindow,
            onQuickSettings = { quickSettingsOpen = !quickSettingsOpen; startOpen = false },
            onController = { controllerOpen = true },
            onPinned = { icon -> apps.firstOrNull { it.icon == icon }?.let(::openApp) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (controllerOpen) ControllerSheet(usbConnected) { controllerOpen = false }
        desktopMenu?.let { request ->
            DesktopContextMenu(
                request = request,
                onDismiss = { desktopMenu = null },
                onIconSize = { iconSize = it },
                onSort = { sortMode = it },
                onRefresh = { desktopMenu = null },
                onNewFolder = {
                    val number = newItemNumber++
                    apps += DesktopApp("New Folder $number", "files") { FileManagerApp() }
                    desktopMenu = null
                },
                onNewText = {
                    val number = newItemNumber++
                    apps += DesktopApp("New Text Document $number", "notes") { NotesApp() }
                    desktopMenu = null
                },
                onWallpaper = { wallpaperStyle = (wallpaperStyle + 1) % 3; desktopMenu = null },
                onSettings = { apps.firstOrNull { it.title == "Settings" }?.let(::openApp); desktopMenu = null },
                onProperties = { propertiesOpen = true; desktopMenu = null },
            )
        }
        if (propertiesOpen) AlertDialog(
            onDismissRequest = { propertiesOpen = false },
            confirmButton = { TextButton({ propertiesOpen = false }) { Text("OK") } },
            title = { Text("Desktop properties") },
            text = { Text("Desktop Mod\n1920 × 1080 workspace\nWindows-style independent phone desktop\nMouse, keyboard, resize, snap and full-screen enabled") },
        )
    }
}

@Composable
private fun DesktopShortcut(
    app: DesktopApp,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    compact: Boolean = false,
    iconSize: Int? = null,
) {
    Column(
        Modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(86.dp))
            .heightIn(min = ((iconSize ?: if (compact) 34 else 42) + 40).dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) Color.White.copy(alpha = .22f) else Color.Transparent)
            .pointerInput(app.title) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onDoubleTap = { onSelect(); onOpen() },
                )
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShellIcon(app.icon, Modifier.size((iconSize ?: if (compact) 34 else 42).dp))
        Text(
            app.title,
            color = Color.White,
            style = if ((iconSize ?: 42) <= 28) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StartMenu(onInstalledApp: (LaunchableApp) -> Unit, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    Card(
        modifier.width(520.dp).height(570.dp).shadow(28.dp, MaterialTheme.shapes.extraLarge),
        colors = CardDefaults.cardColors(containerColor = Glass),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search for apps, settings, and documents") },
                leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp), singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            )
            Text("All apps", Modifier.padding(start = 38.dp, top = 22.dp, bottom = 8.dp), color = Ink, fontWeight = FontWeight.SemiBold)
            AllAppsGrid(query = query, onLaunch = onInstalledApp, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp))
            Row(
                Modifier.fillMaxWidth().height(68.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .85f)).padding(horizontal = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = WinBlue, modifier = Modifier.size(34.dp)) { Box(contentAlignment = Alignment.Center) { Text("U", color = Color.White) } }
                Text("Universal Mobile", Modifier.padding(start = 12.dp).weight(1f), color = Ink)
                IconButton(onClick = {}) { Text("⏻", color = Ink, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

@Composable
private fun WindowsTaskbar(
    windows: List<WindowState>, onStart: () -> Unit,
    onWindow: (WindowState) -> Unit, onQuickSettings: () -> Unit, onController: () -> Unit,
    onPinned: (String) -> Unit,
    modifier: Modifier,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = Date()
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now) }
    Box(
        modifier.fillMaxWidth().height(58.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .94f))
            .clickable(onClick = {}),
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TaskbarIcon("start", onStart, WinBlue)
            TaskbarIcon("search", onStart, Ink)
            listOf("files", "browser", "settings").forEach { icon ->
                val running = windows.firstOrNull { it.icon == icon }
                TaskbarIcon(icon, { if (running == null) onPinned(icon) else onWindow(running.copy(minimized = !running.minimized)) }, Ink, running != null)
            }
            windows.filter { it.icon !in setOf("files", "browser", "settings") }.take(5).forEach { window ->
                TaskbarIcon(window.icon, { onWindow(window.copy(minimized = !window.minimized)) }, Ink, true)
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onQuickSettings).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⌁", color = Ink, modifier = Modifier.clickable(onClick = onController))
            Text("🔊", color = Ink)
            Column(horizontalAlignment = Alignment.End) { Text(time, color = Ink, style = MaterialTheme.typography.labelMedium); Text(date, color = Ink, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable private fun TaskbarIcon(label: String, onClick: () -> Unit, color: Color, active: Boolean = false) {
    Box(contentAlignment = Alignment.BottomCenter) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { ShellIcon(label, Modifier.size(36.dp), color) }
        if (active) Box(Modifier.padding(bottom = 2.dp).width(16.dp).height(3.dp).clip(MaterialTheme.shapes.small).background(WinBlue))
    }
}

@Composable
private fun DesktopContextMenu(
    request: DesktopMenuRequest,
    onDismiss: () -> Unit,
    onIconSize: (Int) -> Unit,
    onSort: (Int) -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onNewText: () -> Unit,
    onWallpaper: () -> Unit,
    onSettings: () -> Unit,
    onProperties: () -> Unit,
) {
    Card(
        Modifier
            .offset { IntOffset(request.x.coerceAtMost(1660), request.y.coerceAtMost(630)) }
            .width(238.dp)
            .zIndex(10_000f)
            .shadow(22.dp, MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .97f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(vertical = 7.dp)) {
            ContextMenuHeading("View")
            ContextMenuItem("Small icons") { onIconSize(0); onDismiss() }
            ContextMenuItem("Medium icons") { onIconSize(1); onDismiss() }
            ContextMenuItem("Large icons") { onIconSize(2); onDismiss() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ContextMenuHeading("Sort by")
            ContextMenuItem("Name") { onSort(0); onDismiss() }
            ContextMenuItem("Type") { onSort(1); onDismiss() }
            ContextMenuItem("Date created") { onSort(2); onDismiss() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ContextMenuItem("Refresh", onRefresh)
            ContextMenuItem("New folder", onNewFolder)
            ContextMenuItem("New text document", onNewText)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ContextMenuItem("Change wallpaper", onWallpaper)
            ContextMenuItem("Display settings", onSettings)
            ContextMenuItem("Personalize", onSettings)
            ContextMenuItem("Properties", onProperties)
        }
    }
}

@Composable private fun ContextMenuHeading(label: String) {
    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun ContextMenuItem(label: String, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp), color = Ink, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun ShellIcon(key: String, modifier: Modifier = Modifier, fallbackColor: Color = Ink) {
    val resource = when (key) {
        "files" -> R.drawable.desktop_files_asset
        "browser" -> R.drawable.desktop_browser_asset
        "notes" -> R.drawable.desktop_notes_asset
        "phone" -> R.drawable.desktop_phone_asset
        "image" -> R.drawable.desktop_image_asset
        "video" -> R.drawable.desktop_video_asset
        "audio" -> R.drawable.desktop_audio_asset
        "calculator" -> R.drawable.desktop_calculator_asset
        "clock" -> R.drawable.desktop_clock_asset
        "settings" -> R.drawable.desktop_settings_asset
        "search" -> R.drawable.desktop_search_asset
        "start" -> R.drawable.desktop_start
        else -> null
    }
    if (resource != null) {
        Image(painterResource(resource), contentDescription = key, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(key.take(1).uppercase(), color = fallbackColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickSettings(usbConnected: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val themeMode by ThemeController.mode.collectAsState()
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maximumVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximumVolume) }
    val notificationManager = remember { context.getSystemService(NotificationManager::class.java) }
    val batteryLevel = remember { context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    val focusActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    fun openSetting(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Card(modifier.width(330.dp).shadow(24.dp, MaterialTheme.shapes.extraLarge), colors = CardDefaults.cardColors(containerColor = Glass), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Quick settings", color = Ink, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(R.drawable.ic_qs_wifi, "Wi-Fi", true) {
                    openSetting(Intent(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
                }
                QuickTile(R.drawable.ic_qs_bluetooth, "Bluetooth", true) { openSetting(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                QuickTile(R.drawable.ic_qs_focus, "Focus", focusActive) { openSetting(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_qs_usb), null, tint = if (usbConnected) Color(0xFF16853D) else Color(0xFFC42B1C), modifier = Modifier.size(20.dp))
                Text(if (usbConnected) "Phone receiver connected" else "Phone receiver disconnected", color = if (usbConnected) Color(0xFF16853D) else Color(0xFFC42B1C))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_qs_battery), null, tint = Ink, modifier = Modifier.size(20.dp))
                Text("Battery", Modifier.weight(1f), color = Ink)
                Text("$batteryLevel%", color = Ink, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_qs_theme), null, tint = Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Appearance", Modifier.weight(1f), color = Ink)
                OutlinedButton(onClick = {
                    val next = when (themeMode) {
                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.SYSTEM
                    }
                    ThemeController.set(context, next)
                }) { Text(themeMode.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_qs_volume), null, tint = Ink, modifier = Modifier.size(20.dp))
                Text("Volume", color = Ink)
            }
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maximumVolume).toInt(), 0)
                },
            )
        }
    }
}

@Composable private fun RowScope.QuickTile(icon: Int, label: String, active: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.weight(1f), color = if (active) WinBlue else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), null, tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(label, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DeviceApp() {
    val usbConnected by UsbService.isReceiverConnected.collectAsState()
    val context = LocalContext.current
    val battery = remember { context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("System", style = MaterialTheme.typography.headlineSmall, color = Ink)
        StatusRow("Desktop shell", "Ready")
        StatusRow("Battery", "$battery%")
        StatusRow("USB receiver", if (usbConnected) "Connected" else "Disconnected")
        StatusRow("Wireless display", "Planned")
        HorizontalDivider()
        Text("Device and desktop connection information.", color = Color.Gray)
    }
}

@Composable private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Ink); Text(value, color = Ink, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ControllerSheet(connected: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth(.86f).height(410.dp).clickable(enabled = false) {}, colors = CardDefaults.cardColors(containerColor = Glass), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Desktop controller", color = Ink, style = MaterialTheme.typography.titleLarge); Text(if (connected) "● Connected" else "● Disconnected", color = if (connected) Color(0xFF16853D) else Color(0xFFC42B1C)) }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Surface(Modifier.fillMaxWidth().weight(1f).padding(vertical = 14.dp), color = Color(0xFFE8EDF5), shape = MaterialTheme.shapes.large) { Box(contentAlignment = Alignment.Center) { Text("TRACKPAD\nMove • tap • two-finger scroll", color = Color.Gray) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button({}, Modifier.weight(1f)) { Text("Left click") }; OutlinedButton({}, Modifier.weight(1f)) { Text("Right click") }; OutlinedButton({}, Modifier.weight(1f)) { Text("Keyboard") } }
            }
        }
    }
}
