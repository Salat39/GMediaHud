package com.salat.gmediahud.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import timber.log.Timber

fun Context.isPackageInstalled(packageName: String): Boolean = try {
    packageManager.getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

fun Context.openAppSystemSettings(packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e)
    }
}

/**
 * Checks if the notification service is enabled for this application.
 *
 * @return true if notifications are allowed, otherwise false.
 */
fun Context.isNotificationServiceEnabled(): Boolean {
    val packageName = packageName
    // Retrieves a list of active NotificationListener services, separated by colons
    val enabledListeners =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
    return enabledListeners?.contains(packageName) ?: false
}

/**
 * Requests permission for the NotificationListener by opening the notification settings.
 *
 * If the context is not an Activity, the FLAG_ACTIVITY_NEW_TASK flag must be added.
 */
fun Context.requestNotificationServicePermission() {
    // Creates an Intent to navigate to the notification access settings
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    // If the context is not an Activity, adds the flag for a new task
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

fun Context.openAccessibilitySettings() = try {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
} catch (e: Exception) {
    println(e)
}

fun Context.toast(text: String) {
    try {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Timber.e(e)
    }
}
