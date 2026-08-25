package com.example.universaldesktopapp.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Folder(val icon: String, val name: String, val detail: String)

@Composable
fun FileManagerApp() {
    val folders = listOf(
        Folder("📄", "Documents", "App-accessible documents"),
        Folder("⬇", "Downloads", "Downloaded files"),
        Folder("🖼", "Pictures", "Photos and screenshots"),
        Folder("▶", "Videos", "Movies and recordings"),
        Folder("♫", "Music", "Audio library"),
    )
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Phone storage", style = MaterialTheme.typography.headlineSmall)
        Text("Storage Access Framework integration is the next file-transfer milestone.")
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        folders.forEach { folder ->
            Row(Modifier.fillMaxWidth().clickable { }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(folder.icon, style = MaterialTheme.typography.titleLarge)
                Column { Text(folder.name, style = MaterialTheme.typography.titleMedium); Text(folder.detail) }
            }
        }
    }
}
