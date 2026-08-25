package com.example.universaldesktopapp.ui.apps

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
    var ascending by remember { mutableStateOf(true) }
    var largeIcons by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<File?>(null) }
    var clipboard by remember { mutableStateOf<File?>(null) }
    var clipboardCuts by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var deleteOpen by remember { mutableStateOf(false) }

    fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var accessGranted by remember { mutableStateOf(hasAccess()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            val granted = hasAccess()
            if (granted != accessGranted) {
                accessGranted = granted
                refresh++
            }
        }
    }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { message = "Open Desktop Mod on the phone to grant file access." }
        } else {
            message = "Open Desktop Mod on the phone to grant storage permission."
        }
    }

    val entries = remember(current, refresh, accessGranted, ascending) {
        if (!accessGranted) emptyList() else runCatching {
            current.listFiles()?.sortedWith(
                if (ascending) compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
                else compareByDescending<File> { it.isDirectory }.thenByDescending { it.name.lowercase() },
            ).orEmpty()
        }.getOrElse { emptyList() }
    }
    val visibleEntries = remember(entries, search) {
        entries.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }

    fun openEntry(file: File) {
        if (file.isDirectory) {
            current = file; selected = null; search = ""
        } else when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "heic", "heif" -> MediaOpenController.open(file, MediaKind.IMAGE)
            "mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp", "ts" -> MediaOpenController.open(file, MediaKind.VIDEO)
            "mp3", "wav", "aac", "flac", "m4a", "ogg" -> MediaOpenController.open(file, MediaKind.AUDIO)
            else -> message = "${file.name} • ${formatSize(file.length())}"
        }
    }

    fun pasteClipboard() {
        val source = clipboard ?: run { message = "Nothing to paste"; return }
        val target = uniqueDestination(current, source.name)
        val succeeded = runCatching {
            if (clipboardCuts && source.renameTo(target)) true
            else source.copyRecursively(target, overwrite = false).also { if (it && clipboardCuts) source.deleteRecursively() }
        }.getOrDefault(false)
        message = if (succeeded) "${source.name} pasted" else "Could not paste ${source.name}"
        if (succeeded && clipboardCuts) { clipboard = null; clipboardCuts = false }
        refresh++
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { current.parentFile?.takeIf { current != root && it.path.startsWith(root.path) }?.let { current = it } },
                enabled = current != root,
            ) { Text("←", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = { refresh++ }) { Text("↻", style = MaterialTheme.typography.titleLarge) }
            Surface(Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("This Phone", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text("  ›  ${current.path.removePrefix(root.path).trim('/').ifBlank { "Home" }}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedTextField(
                search, { search = it },
                modifier = Modifier.width(240.dp),
                placeholder = { Text("Search this folder") },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (accessGranted) Button(onClick = { createFolderOpen = true }) { Text("+  New folder") }
            else Button(onClick = ::requestAccess) { Text("Allow all files") }
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
            TextButton(onClick = { selected?.let { clipboard = it; clipboardCuts = false; message = "${it.name} copied" } }, enabled = selected != null) { Text("Copy") }
            TextButton(onClick = { selected?.let { clipboard = it; clipboardCuts = true; message = "${it.name} cut" } }, enabled = selected != null) { Text("Cut") }
            TextButton(onClick = ::pasteClipboard, enabled = clipboard != null) { Text("Paste") }
            TextButton(onClick = { selected?.let { renameValue = it.name; renameOpen = true } }, enabled = selected != null) { Text("Rename") }
            TextButton(onClick = { deleteOpen = true }, enabled = selected != null) { Text("Delete") }
            Spacer(Modifier.weight(1f))
            FilterChip(selected = ascending, onClick = { ascending = !ascending }, label = { Text(if (ascending) "A–Z" else "Z–A") })
            FilterChip(selected = largeIcons, onClick = { largeIcons = !largeIcons }, label = { Text(if (largeIcons) "Compact" else "Large icons") })
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
                    Modifier.width(190.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("File Explorer", Modifier.padding(10.dp), style = MaterialTheme.typography.titleMedium)
                    Text("QUICK ACCESS", Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExplorerNav("⌂", "Home") { current = root }
                    listOf("Download" to "Downloads", "Documents" to "Documents", "Pictures" to "Pictures", "Movies" to "Videos", "Music" to "Music").forEach { (folder, label) ->
                        ExplorerNav("›", label) { File(root, folder).takeIf { it.exists() }?.let { current = it } }
                    }
                    Spacer(Modifier.weight(1f))
                    ExplorerNav("▣", "This Phone") { current = root }
                }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .45f)).padding(16.dp)) {
                    message?.let { Text(it, Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.primary) }
                    Text(if (current == root) "This Phone" else current.name, style = MaterialTheme.typography.titleLarge)
                    Text(current.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (largeIcons) 142.dp else 108.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (visibleEntries.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (search.isBlank()) "This folder is empty" else "No matching files", style = MaterialTheme.typography.titleMedium)
                                        Text(if (search.isBlank()) "Create a folder or copy files here." else "Try a different search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        items(visibleEntries, key = { it.absolutePath }) { file ->
                            Column(
                                Modifier.clip(MaterialTheme.shapes.medium)
                                    .background(if (selected?.absolutePath == file.absolutePath) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), MaterialTheme.shapes.medium)
                                    .pointerInput(file.absolutePath) {
                                        detectTapGestures(
                                            onTap = { selected = file },
                                            onDoubleTap = { selected = file; openEntry(file) },
                                        )
                                    }.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                androidx.compose.foundation.Image(
                                    painterResource(fileIcon(file)),
                                    contentDescription = null,
                                    modifier = Modifier.size(if (largeIcons) 68.dp else 44.dp),
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
    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename item") },
            text = { OutlinedTextField(renameValue, { renameValue = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = { Button(onClick = {
                val source = selected
                val safeName = renameValue.trim().replace(Regex("[\\/:*?\"<>|]"), "_")
                val success = source != null && safeName.isNotBlank() && source.renameTo(File(source.parentFile, safeName))
                message = if (success) "Renamed to $safeName" else "Could not rename item"
                if (success) selected = null
                renameOpen = false
                refresh++
            }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete ${selected?.name ?: "item"}?") },
            text = { Text("This permanently deletes the selected item from the phone.") },
            confirmButton = { Button(onClick = {
                val success = selected?.deleteRecursively() == true
                message = if (success) "Item deleted" else "Could not delete item"
                if (success) selected = null
                deleteOpen = false
                refresh++
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
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

private fun uniqueDestination(folder: File, originalName: String): File {
    var target = File(folder, originalName)
    if (!target.exists()) return target
    val base = originalName.substringBeforeLast('.', originalName)
    val extension = originalName.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
    var index = 2
    while (target.exists()) {
        target = File(folder, "$base ($index)$extension")
        index++
    }
    return target
}
