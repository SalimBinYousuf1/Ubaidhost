package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppleCard
import com.example.ui.components.ApplePrimaryButton
import com.example.ui.components.AppleSecondaryButton
import com.example.ui.components.PairingQrCanvas
import com.example.ui.components.StatusPill
import com.example.ui.theme.AppleAmber
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBorder
import com.example.ui.theme.AppleGreen
import com.example.ui.theme.AppleSurfaceVariant
import com.example.ui.theme.AppleTextPrimary
import com.example.ui.theme.AppleTextSecondary
import com.example.ui.viewmodel.HostViewModel
import kotlinx.coroutines.delay

@Composable
fun PairingScreen(viewModel: HostViewModel) {
    val context = LocalContext.current
    val pin by viewModel.pairingPin.collectAsStateWithLifecycle()
    val uri by viewModel.pairingUri.collectAsStateWithLifecycle()
    val expiresAt by viewModel.pairingExpiresAt.collectAsStateWithLifecycle()
    val hostConfig by viewModel.hostConfig.collectAsStateWithLifecycle()

    var remainingSeconds by remember(expiresAt) {
        mutableLongStateOf(((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0L))
    }

    LaunchedEffect(expiresAt) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)

    BackHandler {
        viewModel.navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier.testTag("pairing_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppleTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pair a Controller",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan this QR code from APK 2 (Salim) or enter the 6-digit numeric pairing PIN. Pairing keys are cryptographic and expire in 10 minutes.",
            fontSize = 14.sp,
            color = AppleTextSecondary,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // QR Canvas Box
        if (uri.isNotEmpty()) {
            PairingQrCanvas(
                payload = uri,
                modifier = Modifier.testTag("pairing_qr_canvas")
            )
        } else {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppleBlue)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pairing PIN Banner
        AppleCard(modifier = Modifier.testTag("pairing_pin_card")) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ONE-TIME PAIRING PIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (pin.length >= 6) "${pin.take(3)} ${pin.takeLast(3)}" else pin,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AppleBlue,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                StatusPill(
                    text = if (remainingSeconds > 0) "Expires in $timeFormatted" else "Expired",
                    statusColor = if (remainingSeconds > 0) AppleGreen else AppleAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Listening Status Card
        AppleCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = AppleBlue
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Waiting for Salim...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "Listening on LAN port 8999 & WAN Relay",
                        fontSize = 12.sp,
                        color = AppleTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppleSecondaryButton(
                text = "Copy Pairing URI",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ubaid Pairing URI", uri))
                    Toast.makeText(context, "Pairing URI copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                testTag = "copy_pairing_uri_button"
            )
            AppleSecondaryButton(
                text = "Regenerate",
                onClick = { viewModel.generateNewPairingSession() },
                modifier = Modifier.weight(1f),
                testTag = "regenerate_pin_button"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Footnote
        AppleCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AppleBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Host ID: ${hostConfig?.hostDeviceId ?: "Generating..."}\nKey Fingerprint: ${viewModel.getHostPublicKeyFingerprint().take(20)}...",
                    fontSize = 12.sp,
                    color = AppleTextSecondary,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
