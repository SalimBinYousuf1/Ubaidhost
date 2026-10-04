package com.example.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.example.service.ScreenCaptureManager
import com.example.service.UbaidAccessibilityService

data class PermissionStatus(
    val hasInternet: Boolean,
    val hasNotifications: Boolean,
    val isAccessibilityEnabled: Boolean,
    val isMediaProjectionActive: Boolean,
    val isBatteryOptimizationIgnored: Boolean,
    val isForegroundServiceAllowed: Boolean,
    val isReadyForHostSession: Boolean
)

class PermissionManager(private val context: Context) {

    fun checkAllPermissions(): PermissionStatus {
        val internet = checkInternetPermission()
        val notifications = checkNotificationPermission()
        val accessibility = checkAccessibilityServiceEnabled()
        val mediaProjection = ScreenCaptureManager.isProjectionActive
        val batteryOpt = checkBatteryOptimizationIgnored()
        val foregroundService = true // Granted at install time for target API

        val ready = internet && notifications && accessibility && batteryOpt

        return PermissionStatus(
            hasInternet = internet,
            hasNotifications = notifications,
            isAccessibilityEnabled = accessibility,
            isMediaProjectionActive = mediaProjection,
            isBatteryOptimizationIgnored = batteryOpt,
            isForegroundServiceAllowed = foregroundService,
            isReadyForHostSession = ready
        )
    }

    fun checkInternetPermission(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true
        }
    }

    fun checkAccessibilityServiceEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val expectedServiceId = "${context.packageName}/${UbaidAccessibilityService::class.java.canonicalName}"
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (enabled in enabledServices) {
            if (enabled.id.equals(expectedServiceId, ignoreCase = true) ||
                enabled.resolveInfo.serviceInfo.packageName == context.packageName
            ) {
                return true
            }
        }
        return UbaidAccessibilityService.isServiceRunning()
    }

    fun checkBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createNotificationSettingsIntent(): Intent {
        return Intent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun createBatteryOptimizationIntent(): Intent {
        return Intent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
