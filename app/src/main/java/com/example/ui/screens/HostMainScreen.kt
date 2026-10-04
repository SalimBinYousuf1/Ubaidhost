package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.HostState
import com.example.service.UbaidHostService
import com.example.ui.components.ActionRow
import com.example.ui.components.AppleCard
import com.example.ui.components.ApplePrimaryButton
import com.example.ui.components.AppleSecondaryButton
import com.example.ui.components.MetricTile
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AppleAmber
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBorder
import com.example.ui.theme.AppleGreen
import com.example.ui.theme.AppleRed
import com.example.ui.theme.AppleSurfaceVariant
import com.example.ui.theme.AppleTextPrimary
import com.example.ui.theme.AppleTextSecondary
import com.example.ui.viewmodel.HostNavigationScreen
import com.example.ui.viewmodel.HostViewModel

@Composable
fun HostMainScreen(
    viewModel: HostViewModel,
    onRequestMediaProjection: () -> Unit
) {
    val context = LocalContext.current
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()
    val permStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val telemetry by viewModel.deviceStatus.collectAsStateWithLifecycle()
    val authorizedDevices by viewModel.authorizedDevices.collectAsStateWithLifecycle()
    val activeControllerName by viewModel.activeSessionControllerName.collectAsStateWithLifecycle()

    val isServiceRunning = UbaidHostService.isRunning()

    val statusColor = when (hostState) {
        HostState.ONLINE -> AppleGreen
        HostState.ACTIVE_SESSION -> AppleGreen
        HostState.PAIRING -> AppleAmber
        HostState.SETUP_REQUIRED -> AppleAmber
        HostState.ERROR -> AppleRed
        else -> AppleTextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Ubaid",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Remote Host Service • Phone A",
                    fontSize = 14.sp,
                    color = AppleTextSecondary
                )
            }
            StatusPill(
                text = hostState.label,
                statusColor = statusColor,
                modifier = Modifier.testTag("host_state_pill")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Session Banner (if connected)
        if (hostState == HostState.ACTIVE_SESSION && activeControllerName != null) {
            AppleCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_session_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live Remote Session",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Controller: $activeControllerName",
                            fontSize = 14.sp,
                            color = AppleGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    AppleSecondaryButton(
                        text = "Disconnect",
                        onClick = { viewModel.disconnectActiveSession() },
                        color = AppleRed,
                        modifier = Modifier.width(120.dp),
                        testTag = "disconnect_session_button"
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Host Service Control Card
        AppleCard(modifier = Modifier.testTag("host_control_card")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Host Service",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isServiceRunning) "Running in background • Listening on port 8999" else "Service stopped • Remote connection unavailable",
                        fontSize = 13.sp,
                        color = AppleTextSecondary
                    )
                }
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { viewModel.toggleHostService() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppleGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = AppleBorder
                    ),
                    modifier = Modifier.testTag("host_service_switch")
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = AppleBorder
            )

            // Screen Capture Consent Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Screen Streaming (MediaProjection)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = if (permStatus.isMediaProjectionActive) "Ready • Hardware display mirror active" else "Requires one-time OS system authorization",
                        fontSize = 13.sp,
                        color = if (permStatus.isMediaProjectionActive) AppleGreen else AppleAmber
                    )
                }

                if (!permStatus.isMediaProjectionActive) {
                    AppleSecondaryButton(
                        text = "Authorize",
                        onClick = onRequestMediaProjection,
                        modifier = Modifier.width(105.dp),
                        testTag = "authorize_screen_button"
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = AppleGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Telemetry Row
        SectionHeader(title = "Real Device Telemetry")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Battery",
                value = "${telemetry.batteryLevel}%",
                subValue = if (telemetry.isCharging) "Charging (${telemetry.chargePlug})" else "${telemetry.batteryTemperatureC}°C",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Network",
                value = telemetry.networkType,
                subValue = if (telemetry.wifiSsid.isNotEmpty()) telemetry.wifiSsid else telemetry.ipAddress,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Local Port",
                value = "8999",
                subValue = "Direct LAN WS",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Permissions Checklist Card
        SectionHeader(title = "Permission & Capability State")
        AppleCard(modifier = Modifier.testTag("permissions_card")) {
            PermissionCheckRow(
                name = "Accessibility Service",
                description = "Required for remote touch, gestures, and navigation",
                isEnabled = permStatus.isAccessibilityEnabled,
                onFix = {
                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppleBorder)
            PermissionCheckRow(
                name = "Persistent Notifications",
                description = "Required to keep host service alive in background",
                isEnabled = permStatus.hasNotifications,
                onFix = {
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppleBorder)
            PermissionCheckRow(
                name = "Battery Optimization Exemption",
                description = "Prevents Android from freezing host while unattended",
                isEnabled = permStatus.isBatteryOptimizationIgnored,
                onFix = {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )

            if (!permStatus.isReadyForHostSession) {
                Spacer(modifier = Modifier.height(14.dp))
                ApplePrimaryButton(
                    text = "Complete Host Setup",
                    onClick = { viewModel.navigateTo(HostNavigationScreen.Setup) },
                    testTag = "open_setup_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Authorized Devices Card
        SectionHeader(title = "Authorized Controllers (Salim)")
        AppleCard(modifier = Modifier.testTag("authorized_devices_card")) {
            val activeDevices = authorizedDevices.filter { !it.isRevoked }
            if (activeDevices.isEmpty()) {
                Text(
                    text = "No controllers authorized yet. Phone A will reject all incoming remote connection requests until paired.",
                    fontSize = 14.sp,
                    color = AppleTextSecondary,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                activeDevices.forEachIndexed { index, device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.deviceName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "Fingerprint: ${device.fingerprint.take(17)}...",
                                fontSize = 12.sp,
                                color = AppleTextSecondary
                            )
                        }
                        AppleSecondaryButton(
                            text = "Revoke",
                            onClick = { viewModel.revokeDevice(device.deviceId) },
                            color = AppleRed,
                            modifier = Modifier.width(85.dp),
                            testTag = "revoke_device_${device.deviceId}"
                        )
                    }
                    if (index < activeDevices.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            ApplePrimaryButton(
                text = "Pair New Controller",
                onClick = {
                    viewModel.generateNewPairingSession()
                    viewModel.navigateTo(HostNavigationScreen.Pairing)
                },
                testTag = "pair_controller_button"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Security & Configuration Hub
        SectionHeader(title = "Security & Host Diagnostics")
        AppleCard {
            ActionRow(
                title = "Hardware Keystore Identity",
                subtitle = "RSA-2048 Hardware-Backed • SHA-256",
                leadingIcon = Icons.Default.Security,
                trailingText = "Protected",
                testTag = "open_security_row"
            ) {
                viewModel.navigateTo(HostNavigationScreen.ProtocolInspector)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = AppleBorder)
            ActionRow(
                title = "Device Telemetry & Hardware Report",
                subtitle = "Battery, thermals, display, network details",
                leadingIcon = Icons.Default.Speed,
                testTag = "open_telemetry_row"
            ) {
                viewModel.navigateTo(HostNavigationScreen.TelemetryDetails)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = AppleBorder)
            ActionRow(
                title = "OEM Optimization Guide",
                subtitle = "Samsung, Xiaomi, OPPO, Vivo background stability",
                leadingIcon = Icons.Default.Warning,
                testTag = "open_oem_guide_row"
            ) {
                viewModel.navigateTo(HostNavigationScreen.OemGuide)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = AppleBorder)
            ActionRow(
                title = "APK 2 (Salim) Protocol Contract",
                subtitle = "Version 1.0.0 • Message schemas & specifications",
                leadingIcon = Icons.Default.Info,
                testTag = "open_protocol_row"
            ) {
                viewModel.navigateTo(HostNavigationScreen.ProtocolInspector)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun PermissionCheckRow(
    name: String,
    description: String,
    isEnabled: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = AppleTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (isEnabled) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Enabled",
                tint = AppleGreen,
                modifier = Modifier.size(22.dp)
            )
        } else {
            AppleSecondaryButton(
                text = "Enable",
                onClick = onFix,
                color = AppleAmber,
                modifier = Modifier.width(85.dp),
                testTag = "enable_permission_button"
            )
        }
    }
}
