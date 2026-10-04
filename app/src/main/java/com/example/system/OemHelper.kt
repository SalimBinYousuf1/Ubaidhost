package com.example.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

data class OemInstruction(
    val manufacturer: String,
    val brandName: String,
    val title: String,
    val steps: List<String>,
    val targetIntent: Intent?
)

object OemHelper {

    fun detectOem(context: Context): OemInstruction {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                OemInstruction(
                    manufacturer = "Xiaomi / MIUI / HyperOS",
                    brandName = "Xiaomi",
                    title = "MIUI / HyperOS Background Protection",
                    steps = listOf(
                        "1. Enable 'Autostart' for Ubaid in Security > Manage Apps.",
                        "2. Set Battery Saver to 'No Restrictions'.",
                        "3. Enable 'Display pop-up windows while running in the background'.",
                        "4. Lock Ubaid in the Recent Apps tray by pulling down on its card."
                    ),
                    targetIntent = createXiaomiIntent()
                )
            }
            manufacturer.contains("samsung") -> {
                OemInstruction(
                    manufacturer = "Samsung / One UI",
                    brandName = "Samsung",
                    title = "Samsung One UI Background Exemption",
                    steps = listOf(
                        "1. Go to Settings > Apps > Ubaid > Battery.",
                        "2. Select 'Unrestricted'.",
                        "3. Go to Device Care > Battery > Background Usage Limits.",
                        "4. Add Ubaid to 'Never sleeping apps'."
                    ),
                    targetIntent = createSamsungIntent()
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                OemInstruction(
                    manufacturer = "OPPO / Realme / OnePlus (ColorOS / OxygenOS)",
                    brandName = "ColorOS",
                    title = "ColorOS Background Optimization Exemption",
                    steps = listOf(
                        "1. Open App Info > Battery Usage.",
                        "2. Enable 'Allow background activity' and 'Allow auto-launch'.",
                        "3. Lock Ubaid in Recent tasks."
                    ),
                    targetIntent = createOppoIntent()
                )
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                OemInstruction(
                    manufacturer = "Vivo / iQOO (FuntouchOS / OriginOS)",
                    brandName = "Vivo",
                    title = "FuntouchOS Background Protection",
                    steps = listOf(
                        "1. Open Settings > Battery > High background power consumption.",
                        "2. Toggle ON for Ubaid.",
                        "3. Enable Autostart in i Manager > App Manager."
                    ),
                    targetIntent = createVivoIntent()
                )
            }
            else -> {
                OemInstruction(
                    manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                    brandName = "Standard Android",
                    title = "Standard Android Background Exemption",
                    steps = listOf(
                        "1. Disable Battery Optimization in Settings > Apps > Ubaid > Battery.",
                        "2. Set to 'Unrestricted' background access.",
                        "3. Ensure Notifications are allowed for continuous foreground service."
                    ),
                    targetIntent = null
                )
            }
        }
    }

    private fun createXiaomiIntent(): Intent? {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        )
        return intents.firstOrNull()
    }

    private fun createSamsungIntent(): Intent? {
        return Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"))
    }

    private fun createOppoIntent(): Intent? {
        return Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
    }

    private fun createVivoIntent(): Intent? {
        return Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
    }
}
