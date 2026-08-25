package com.example.universaldesktopapp.ui.desktop

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale

internal data class LaunchableApp(val label: String, val packageName: String)

@Composable
internal fun AllAppsGrid(
    query: String,
    onLaunch: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        results.map {
            LaunchableApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = modifier) {
        items(filtered, key = { it.packageName }) { app ->
            val icon = remember(app.packageName) {
                runCatching {
                    context.packageManager.getApplicationIcon(app.packageName)
                        .toBitmap(width = 96, height = 96).asImageBitmap()
                }.getOrNull()
            }
            Column(
                Modifier.clickable { onLaunch(app) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) Image(icon, app.label, Modifier.size(40.dp))
                else Text(app.label.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
