package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.OemHelper
import com.example.ui.components.AppleCard
import com.example.ui.components.ApplePrimaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AppleAmber
import com.example.ui.theme.AppleBorder
import com.example.ui.theme.AppleTextPrimary
import com.example.ui.theme.AppleTextSecondary
import com.example.ui.viewmodel.HostViewModel

@Composable
fun OemGuideScreen(viewModel: HostViewModel) {
    val context = LocalContext.current
    val oemInfo = OemHelper.detectOem(context)

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
                modifier = Modifier.testTag("oem_guide_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppleTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "OEM Background Guide",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Aggressive OEM battery savers (Xiaomi MIUI, Samsung One UI, ColorOS, OriginOS) aggressively terminate background services when a phone sits idle. Follow the instructions below to guarantee Ubaid stays online 24/7.",
            fontSize = 14.sp,
            color = AppleTextSecondary,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(title = "Detected Device Profile")
        AppleCard {
            Text(
                text = oemInfo.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manufacturer: ${oemInfo.manufacturer}",
                fontSize = 13.sp,
                color = AppleTextSecondary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppleBorder)

            oemInfo.steps.forEach { step ->
                Text(
                    text = step,
                    fontSize = 14.sp,
                    color = AppleTextPrimary,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (oemInfo.targetIntent != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ApplePrimaryButton(
                    text = "Open ${oemInfo.brandName} Settings",
                    onClick = {
                        try {
                            oemInfo.targetIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(oemInfo.targetIntent)
                        } catch (_: Exception) {}
                    },
                    testTag = "open_oem_settings_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(title = "Android OS Hardware Limitations")
        AppleCard {
            Text(
                text = "Security Restrictions (Cannot Be Bypassed)",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• FLAG_SECURE: Banking apps and password entry screens render as black frames by Android OS design.\n" +
                        "• System Lock PIN: Certain Knox and SELinux implementations prevent simulated accessibility touches on device credential entry.\n" +
                        "• Device Reboot: Third-party apps cannot trigger a hardware system reboot without Device Owner or Root access.",
                fontSize = 13.sp,
                color = AppleTextSecondary,
                lineHeight = 19.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
