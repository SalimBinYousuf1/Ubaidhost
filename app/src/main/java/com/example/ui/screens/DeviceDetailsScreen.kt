package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.DeviceTelemetryProvider
import com.example.ui.components.AppleCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AppleBorder
import com.example.ui.theme.AppleTextPrimary
import com.example.ui.theme.AppleTextSecondary
import com.example.ui.viewmodel.HostViewModel

@Composable
fun DeviceDetailsScreen(viewModel: HostViewModel) {
    val context = LocalContext.current
    val telemetry by viewModel.deviceStatus.collectAsStateWithLifecycle()
    val provider = rememberTelemetryProvider(context)
    val memory = provider.getMemoryInfo()
    val display = provider.getDisplayMetrics()

    BackHandler {
        viewModel.navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.navigateBack() },
                    modifier = Modifier.testTag("telemetry_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppleTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hardware Telemetry",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
            }
            IconButton(
                onClick = { viewModel.refreshAll() },
                modifier = Modifier.testTag("telemetry_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = AppleTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Power & Battery
        SectionHeader(title = "Battery & Power Subsystem")
        AppleCard {
            TelemetryRow("Battery Level", "${telemetry.batteryLevel}%")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Charging State", if (telemetry.isCharging) "Charging (${telemetry.chargePlug})" else "Discharging")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Battery Temperature", "${telemetry.batteryTemperatureC} °C")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Interactive Screen", if (telemetry.isScreenOn) "Screen ON" else "Screen OFF / Locked")
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Network
        SectionHeader(title = "Network Interfaces & Routing")
        AppleCard {
            TelemetryRow("Transport Type", telemetry.networkType)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            if (telemetry.wifiSsid.isNotEmpty()) {
                TelemetryRow("Wi-Fi SSID", telemetry.wifiSsid)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
                TelemetryRow("Signal Strength", "${telemetry.wifiSignalStrengthDbm} dBm")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            }
            TelemetryRow("Local IP Address", telemetry.ipAddress)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Embedded LAN Port", "8999 (WebSocket)")
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Display & Render
        SectionHeader(title = "Display & Hardware Metrics")
        AppleCard {
            TelemetryRow("Native Resolution", "${display.widthPixels} x ${display.heightPixels} px")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Display Density", "${display.densityDpi} DPI")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Available RAM", "${memory.first} MB / ${memory.second} MB")
        }

        Spacer(modifier = Modifier.height(18.dp))

        // System & Security
        SectionHeader(title = "Operating System & Security")
        AppleCard {
            TelemetryRow("Device Model", telemetry.deviceModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Android Version", "${telemetry.androidVersion} (API ${telemetry.sdkInt})")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Security Patch", Build.VERSION.SECURITY_PATCH)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            TelemetryRow("Host Architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun rememberTelemetryProvider(context: android.content.Context): DeviceTelemetryProvider {
    return androidx.compose.runtime.remember { DeviceTelemetryProvider(context) }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = AppleTextSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = AppleTextPrimary
        )
    }
}
