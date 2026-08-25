package com.example.universaldesktopapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.universaldesktopapp.theme.UniversalDesktopAppTheme
import com.example.universaldesktopapp.ui.desktop.DesktopHome
import com.example.universaldesktopapp.usb.UsbService

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    startService(Intent(this, UsbService::class.java))

    enableEdgeToEdge()
    setContent {
      UniversalDesktopAppTheme { 
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
          DesktopHome() 
        } 
      }
    }
  }
}
