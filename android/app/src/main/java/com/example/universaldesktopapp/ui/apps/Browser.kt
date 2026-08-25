package com.example.universaldesktopapp.ui.apps

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun BrowserApp() {
    var address by remember { mutableStateOf("https://www.google.com") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFE2E8F0)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(address, { address = it }, Modifier.weight(1f), singleLine = true, label = { Text("Address") })
            Button(onClick = {
                val target = if (address.startsWith("http")) address else "https://$address"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            }) { Text("Open") }
        }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text("Desktop Browser", style = MaterialTheme.typography.headlineMedium)
            Text("URLs open in your installed browser while the embedded browser engine is developed.")
        }
    }
}
