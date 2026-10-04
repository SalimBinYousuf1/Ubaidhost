package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "authorized_devices")
data class AuthorizedDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val fingerprint: String,
    val pairedAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long = 0L,
    val isRevoked: Boolean = false,
    val permissionsGranted: String = "SCREEN_STREAM,INPUT_INJECTION,FILE_ACCESS,DEVICE_STATUS"
)
