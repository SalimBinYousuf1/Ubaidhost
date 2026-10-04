package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.UbaidHostService
import com.example.ui.components.AppleCard
import com.example.ui.components.ApplePrimaryButton
import com.example.ui.components.AppleSecondaryButton
import com.example.ui.theme.AppleAmber
import com.example.ui.theme.AppleAmberLight
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBorder
import com.example.ui.theme.AppleGreen
import com.example.ui.theme.AppleGreenLight
import com.example.ui.theme.AppleTextPrimary
import com.example.ui.theme.AppleTextSecondary
import com.example.ui.viewmodel.HostViewModel

@Composable
fun SetupScreen(
    viewModel: HostViewModel,
    onRequestMediaProjection: () -> Unit
) {
    val context = LocalContext.current
    val permStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()

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
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier.testTag("setup_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppleTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Host Setup",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "To allow Phone B (Salim) to securely view and control this phone while you are away, configure the required Android system capabilities.",
            fontSize = 15.sp,
            color = AppleTextSecondary,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Step 1: Accessibility Service
        SetupStepCard(
            stepNumber = "1",
            title = "Accessibility Remote Input",
            description = "Enables Ubaid to inject touch gestures, taps, swipes, and system navigation (Back/Home/Recents) on behalf of Salim.",
            icon = Icons.Default.TouchApp,
            isCompleted = permStatus.isAccessibilityEnabled,
            actionLabel = "Enable in Settings",
            onAction = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            },
            testTag = "step_accessibility"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Step 2: Notifications
        SetupStepCard(
            stepNumber = "2",
            title = "Persistent Foreground Notifications",
            description = "Required by Android OS so Ubaid's network listening service remains running continuously in the background without being suspended.",
            icon = Icons.Default.Notifications,
            isCompleted = permStatus.hasNotifications,
            actionLabel = "Allow Notifications",
            onAction = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
                context.startActivity(intent)
            },
            testTag = "step_notifications"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Step 3: Battery Optimization
        SetupStepCard(
            stepNumber = "3",
            title = "Battery Optimization Exemption",
            description = "Prevents Android's Doze mode from putting network sockets and CPU to sleep while Phone A stays unattended at home.",
            icon = Icons.Default.BatteryAlert,
            isCompleted = permStatus.isBatteryOptimizationIgnored,
            actionLabel = "Disable Optimization",
            onAction = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
            testTag = "step_battery"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Step 4: Screen Capture (MediaProjection)
        SetupStepCard(
            stepNumber = "4",
            title = "Screen Streaming (MediaProjection)",
            description = "Authorizes the Android system virtual display to capture real frames for Salim's viewport.",
            icon = Icons.Default.Cast,
            isCompleted = permStatus.isMediaProjectionActive,
            actionLabel = "Grant Screen Mirroring",
            onAction = onRequestMediaProjection,
            testTag = "step_media_projection"
        )

        Spacer(modifier = Modifier.height(28.dp))

        ApplePrimaryButton(
            text = if (permStatus.isReadyForHostSession) "Finish & Start Host" else "Refresh Status",
            onClick = {
                viewModel.refreshAll()
                if (permStatus.isReadyForHostSession) {
                    UbaidHostService.start(context)
                    viewModel.navigateBack()
                }
            },
            testTag = "setup_finish_button"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isCompleted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    AppleCard(modifier = Modifier.testTag(testTag)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) AppleGreenLight else AppleAmberLight),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = AppleGreen,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = stepNumber,
                        color = AppleAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = AppleTextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = AppleGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Enabled & Active",
                            fontSize = 13.sp,
                            color = AppleGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    AppleSecondaryButton(
                        text = actionLabel,
                        onClick = onAction,
                        color = AppleBlue,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "${testTag}_button"
                    )
                }
            }
        }
    }
}
