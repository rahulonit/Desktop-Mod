package com.example.universaldesktopapp.ui.desktop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.widget.Toast

internal fun launchInstalledApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        Toast.makeText(context, "This app has no launchable activity", Toast.LENGTH_SHORT).show()
        return
    }

    val freeformSupported = context.packageManager.hasSystemFeature(
        PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT,
    ) || runCatching {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(false)

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    val options = ActivityOptions.makeBasic()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.let { options.launchDisplayId = it.displayId }
    }
    if (freeformSupported) {
        val metrics = context.resources.displayMetrics
        val marginX = (metrics.widthPixels * 0.08f).toInt()
        val marginY = (metrics.heightPixels * 0.08f).toInt()
        options.launchBounds = Rect(
            marginX,
            marginY,
            (metrics.widthPixels * 0.74f).toInt(),
            (metrics.heightPixels * 0.82f).toInt(),
        )
    } else {
        Toast.makeText(
            context,
            "Freeform windows are unavailable; opening the app normally",
            Toast.LENGTH_SHORT,
        ).show()
    }

    runCatching { context.startActivity(intent, options.toBundle()) }
        .onFailure {
            runCatching { context.startActivity(intent) }
                .onFailure { error ->
                    Toast.makeText(context, "Could not open app: ${error.message}", Toast.LENGTH_LONG).show()
                }
        }
}
