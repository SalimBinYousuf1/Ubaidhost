package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_logs")
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val controllerDeviceId: String,
    val controllerDeviceName: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long = 0L,
    val bytesTransferred: Long = 0L,
    val terminationReason: String = "NORMAL"
)
