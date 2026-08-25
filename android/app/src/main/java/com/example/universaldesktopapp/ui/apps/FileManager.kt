package com.example.universaldesktopapp.ui.apps

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import com.example.universaldesktopapp.R

@Composable
fun FileManagerApp() {
    val context = LocalContext.current
    val root = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(root) }
    var refresh by remember { mutableIntStateOf(0) }
    var createFolderOpen by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var accessGranted by remember { mutableStateOf(hasAccess()) }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        accessGranted = hasAccess()
        refresh++
    }
    val allFilesSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        accessGranted = hasAccess()
        refresh++
    }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            allFilesSettings.launch(intent)
        } else {
            legacyPermission.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            )
        }
    }

    val entries = remember(current, refresh, accessGranted) {
        if (!accessGranted) emptyList() else current.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        ).orEmpty()
    }
    val visibleEntries = remember(entries, search) {
        entries.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { current.parentFile?.takeIf { current != root && it.path.startsWith(root.path) }?.let { current = it } },
                enabled = current != root,
            ) { Text("←", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = { refresh++ }) { Text("↻", style = MaterialTheme.typography.titleLarge) }
            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
                Text(current.path.removePrefix(root.path).ifBlank { "This Phone" }, Modifier.padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedTextField(
                search, { search = it },
                modifier = Modifier.width(180.dp),
                placeholder = { Text("Search") },
                singleLine = true,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (accessGranted) Button(onClick = { createFolderOpen = true }) { Text("+  New folder") }
            else Button(onClick = ::requestAccess) { Text("Allow all files") }
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
            Spacer(Modifier.weight(1f))
            Text("${visibleEntries.size} items", style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider()
        if (!accessGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Storage access is required")
                    Text("Allow access to browse the phone storage root.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = ::requestAccess, modifier = Modifier.padding(top = 12.dp)) { Text("Open permission settings") }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(150.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("File Explorer", Modifier.padding(10.dp), style = MaterialTheme.typography.titleMedium)
                    ExplorerNav("⌂", "Home") { current = root }
                    listOf("Download" to "Downloads", "Documents" to "Documents", "Pictures" to "Pictures", "Movies" to "Videos", "Music" to "Music").forEach { (folder, label) ->
                        ExplorerNav("›", label) { File(root, folder).takeIf { it.exists() }?.let { current = it } }
                    }
                    Spacer(Modifier.weight(1f))
                    ExplorerNav("▣", "This Phone") { current = root }
                }
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    message?.let { Text(it, Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.primary) }
                    Text(if (current == root) "This Phone" else current.name, style = MaterialTheme.typography.titleLarge)
                    Text(current.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visibleEntries, key = { it.absolutePath }) { file ->
                            Column(
                                Modifier.clip(MaterialTheme.shapes.medium).clickable {
                                    if (file.isDirectory) current = file else when (file.extension.lowercase()) {
                                        "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "heic", "heif" -> MediaOpenController.open(file, MediaKind.IMAGE)
                                        "mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp", "ts" -> MediaOpenController.open(file, MediaKind.VIDEO)
                                        else -> message = "${file.name} • ${file.length()} bytes"
                                    }
                                }.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                androidx.compose.foundation.Image(
                                    painterResource(fileIcon(file)),
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                )
                                Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text(if (file.isDirectory) "File folder" else formatSize(file.length()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (createFolderOpen) {
        AlertDialog(
            onDismissRequest = { createFolderOpen = false },
            title = { Text("Create folder") },
            text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("Folder name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    val safeName = folderName.trim().replace(Regex("[\\/:*?\"<>|]"), "_")
                    message = if (safeName.isNotBlank() && File(current, safeName).mkdir()) "Folder created" else "Could not create folder"
                    folderName = ""
                    createFolderOpen = false
                    refresh++
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createFolderOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExplorerNav(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { Text(icon); Text(label, maxLines = 1) }
}

private fun fileIcon(file: File): Int {
    if (file.isDirectory) return R.drawable.explorer_folder
    return when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic" -> R.drawable.explorer_picture
        "mp4", "mkv", "avi", "mov", "webm" -> R.drawable.explorer_video
        "mp3", "wav", "aac", "flac", "m4a", "ogg" -> R.drawable.explorer_audio
        else -> R.drawable.explorer_file
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
