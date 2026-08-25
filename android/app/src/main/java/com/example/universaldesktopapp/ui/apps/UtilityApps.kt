package com.example.universaldesktopapp.ui.apps

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.universaldesktopapp.theme.ThemeController
import com.example.universaldesktopapp.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalculatorApp() {
    var display by remember { mutableStateOf("0") }
    var stored by remember { mutableDoubleStateOf(0.0) }
    var operation by remember { mutableStateOf<String?>(null) }
    var replace by remember { mutableStateOf(true) }
    fun number(value: String) { display = if (replace || display == "0") value else display + value; replace = false }
    fun calculate() {
        val right = display.toDoubleOrNull() ?: 0.0
        val result = when (operation) { "+" -> stored + right; "−" -> stored - right; "×" -> stored * right; "÷" -> if (right == 0.0) Double.NaN else stored / right; else -> right }
        display = if (result.isNaN()) "Error" else if (result % 1.0 == 0.0) result.toLong().toString() else result.toString().take(12)
        stored = result; operation = null; replace = true
    }
    val keys = listOf("C", "±", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "0", ".", "⌫", "=")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(display, Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize()) {
            items(keys) { key ->
                Button(onClick = {
                    when (key) {
                        in "0".."9" -> number(key)
                        "." -> if (!display.contains('.')) { if (replace) display = "0"; display += "."; replace = false }
                        "C" -> { display = "0"; stored = 0.0; operation = null; replace = true }
                        "⌫" -> { display = display.dropLast(1).ifBlank { "0" } }
                        "±" -> display = ((display.toDoubleOrNull() ?: 0.0) * -1).toString().removeSuffix(".0")
                        "%" -> display = ((display.toDoubleOrNull() ?: 0.0) / 100).toString()
                        "=" -> calculate()
                        else -> { if (operation != null && !replace) calculate(); stored = display.toDoubleOrNull() ?: 0.0; operation = key; replace = true }
                    }
                }, modifier = Modifier.padding(4.dp).height(48.dp)) { Text(key) }
            }
        }
    }
}

@Composable
fun ClockApp() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); now = System.currentTimeMillis() } }
    val time = remember(now) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date(now)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, style = MaterialTheme.typography.displayMedium)
            Text(date, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun AudioPlayerApp() {
    val context = LocalContext.current
    var source by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("No audio selected") }
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { player?.release() }
            source = uri; title = uri.lastPathSegment ?: "Audio"
            player = MediaPlayer.create(context, uri)?.apply { setOnCompletionListener { playing = false } }
            playing = false
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() }; player = null } }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("♫", style = MaterialTheme.typography.displayLarge)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Open audio") }
            Button(onClick = { player?.let { if (playing) it.pause() else it.start(); playing = !playing } }, enabled = player != null) { Text(if (playing) "Pause" else "Play") }
            OutlinedButton(onClick = { player?.seekTo(0); player?.pause(); playing = false }, enabled = player != null) { Text("Stop") }
        }
    }
}

@Composable
fun DesktopSettingsApp() {
    val context = LocalContext.current
    val mode by ThemeController.mode.collectAsState()
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Desktop Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { option ->
                FilterChip(selected = mode == option, onClick = { ThemeController.set(context, option) }, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        HorizontalDivider()
        Text("Android controls", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { context.startActivity(android.content.Intent(Settings.ACTION_SETTINGS)) }) { Text("Open system settings") }
        Text("System mode follows the phone theme and Android 12+ dynamic colors.", style = MaterialTheme.typography.bodySmall)
    }
}
