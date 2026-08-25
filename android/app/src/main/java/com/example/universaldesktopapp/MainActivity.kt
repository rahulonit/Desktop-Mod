package com.example.universaldesktopapp

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.universaldesktopapp.theme.UniversalDesktopAppTheme
import com.example.universaldesktopapp.theme.ThemeController
import com.example.universaldesktopapp.ui.desktop.DesktopHome
import com.example.universaldesktopapp.usb.UsbService

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ThemeController.initialize(applicationContext)

    startService(Intent(this, UsbService::class.java))

    enableEdgeToEdge()
    enterImmersiveDesktop()
    setContent {
      UniversalDesktopAppTheme { 
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
          val receiverConnected by UsbService.isReceiverConnected.collectAsStateWithLifecycle()
          if (receiverConnected) PhoneHostScreen() else DesktopHome()
        } 
      }
    }
  }

  @Composable
  private fun PhoneHostScreen() {
    Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text("Desktop Mod", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
      Text(
        "Independent desktop is running on the PC",
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        "This phone is the host. Its physical screen is not being mirrored.",
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        Button(onClick = {
          startActivity(Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
          ))
        }) { Text("Grant file access") }
      }
      Button(onClick = { UsbService.disconnectReceiver() }) { Text("End PC session") }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) enterImmersiveDesktop()
  }

  private fun enterImmersiveDesktop() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }
}
