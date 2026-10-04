package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppleCard
import com.example.ui.components.ApplePrimaryButton
import com.example.ui.components.AppleSecondaryButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.AppleAmber
import com.example.ui.theme.AppleBorder
import com.example.ui.theme.AppleGreen
import com.example.ui.theme.AppleRed
import com.example.ui.theme.AppleTextPrimary
import com.example.ui.theme.AppleTextSecondary
import com.example.ui.viewmodel.HostNavigationScreen
import com.example.ui.viewmodel.HostViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuthorizedDevicesScreen(viewModel: HostViewModel) {
    val devices by viewModel.authorizedDevices.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    BackHandler {
        viewModel.navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier.testTag("authorized_devices_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppleTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Authorized Devices",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Controllers listed below have completed cryptographic pairing. Revoking a controller immediately disconnects any active session and blocks all future authentication.",
            fontSize = 14.sp,
            color = AppleTextSecondary,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = AppleTextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "No Authorized Controllers",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pair Salim to begin remote management.",
                        fontSize = 13.sp,
                        color = AppleTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices, key = { it.deviceId }) { device ->
                    AppleCard(modifier = Modifier.testTag("device_item_${device.deviceId}")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.deviceName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleTextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ID: ${device.deviceId}",
                                    fontSize = 12.sp,
                                    color = AppleTextSecondary
                                )
                            }
                            StatusPill(
                                text = if (device.isRevoked) "Revoked" else "Authorized",
                                statusColor = if (device.isRevoked) AppleRed else AppleGreen
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppleBorder)

                        Text(
                            text = "Key Fingerprint: ${device.fingerprint}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppleTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Paired on: ${dateFormat.format(Date(device.pairedAt))}",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )
                        if (device.lastConnectedAt > 0) {
                            Text(
                                text = "Last active: ${dateFormat.format(Date(device.lastConnectedAt))}",
                                fontSize = 12.sp,
                                color = AppleTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!device.isRevoked) {
                                AppleSecondaryButton(
                                    text = "Revoke Access",
                                    onClick = { viewModel.revokeDevice(device.deviceId) },
                                    color = AppleRed,
                                    modifier = Modifier.weight(1f),
                                    testTag = "revoke_button_${device.deviceId}"
                                )
                            }
                            AppleSecondaryButton(
                                text = "Remove",
                                onClick = { viewModel.deleteDevice(device.deviceId) },
                                color = AppleTextSecondary,
                                modifier = Modifier.weight(1f),
                                testTag = "delete_button_${device.deviceId}"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ApplePrimaryButton(
            text = "Pair Another Controller",
            onClick = {
                viewModel.generateNewPairingSession()
                viewModel.navigateTo(HostNavigationScreen.Pairing)
            },
            testTag = "pair_another_button"
        )
    }
}
