package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocol.ProtocolConstants
import com.example.ui.components.AppleCard
import com.example.ui.components.AppleSecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBorder
import com.example.ui.theme.AppleGreen
import com.example.ui.theme.AppleTextPrimary
import com.example.ui.theme.AppleTextSecondary
import com.example.ui.viewmodel.HostViewModel

@Composable
fun ProtocolInspectorScreen(viewModel: HostViewModel) {
    val context = LocalContext.current
    val fingerprint = viewModel.getHostPublicKeyFingerprint()
    val pubKeyBase64 = viewModel.getHostPublicKeyBase64()

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
                modifier = Modifier.testTag("protocol_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppleTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Protocol & Identity",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Official contract specification for APK 2 (Salim Controller). All communications are authenticated against Ubaid's hardware keystore identity.",
            fontSize = 14.sp,
            color = AppleTextSecondary,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        SectionHeader(title = "Cryptographic Identity")
        AppleCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hardware KeyStore",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                StatusPill(text = "RSA-2048 Active", statusColor = AppleGreen)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppleBorder)

            Text(
                text = "HOST FINGERPRINT (SHA-256)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = fingerprint,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = AppleBlue,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppleSecondaryButton(
                text = "Copy Public Key (Base64)",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ubaid Public Key", pubKeyBase64))
                    Toast.makeText(context, "Public key copied", Toast.LENGTH_SHORT).show()
                },
                testTag = "copy_public_key_button"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(title = "Protocol Specification v${ProtocolConstants.PROTOCOL_VERSION}")
        AppleCard {
            ProtocolDetailRow("Version", "${ProtocolConstants.PROTOCOL_VERSION}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            ProtocolDetailRow("Handshake", "HELLO -> HELLO_ACK -> AUTH -> AUTH_RESULT")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            ProtocolDetailRow("Input Injection", "INPUT_EVENT (Normalized 0.0 - 1.0 coords)")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            ProtocolDetailRow("Text Typing", "TEXT_INPUT (AccessibilityNodeInfo)")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            ProtocolDetailRow("Telemetry", "DEVICE_STATUS (Pushed every 10s)")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            ProtocolDetailRow("Keepalive", "PING / PONG (15s interval, 30s timeout)")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AppleBorder)
            ProtocolDetailRow("Relay / LAN", "WebSocket JSON Framing + Embedded Port 8999")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ProtocolDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppleTextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = AppleTextPrimary
        )
    }
}
