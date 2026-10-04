package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "host_config")
data class HostConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val hostDeviceId: String,
    val hostDeviceName: String,
    val signalingServerUrl: String = "wss://relay.ubaid.internal/ws",
    val localPort: Int = 8999,
    val autoStartOnBoot: Boolean = true,
    val requireScreenConsentOnSession: Boolean = false,
    val streamFps: Int = 30,
    val streamBitrateKbps: Int = 2500,
    val pairingNonce: String = "",
    val pairingNonceExpiresAt: Long = 0L
)
