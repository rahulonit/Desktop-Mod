package com.example.universaldesktopapp.ui.apps

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotesApp() {
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Quick note", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = note, onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
            placeholder = { Text("Write something…") },
        )
        Text("${note.length} characters", style = MaterialTheme.typography.labelMedium)
    }
}
