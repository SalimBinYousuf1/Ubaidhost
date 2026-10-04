package com.example.data.repository

import android.os.Build
import com.example.data.dao.HostConfigDao
import com.example.data.entity.HostConfigEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class HostConfigRepository(
    private val hostConfigDao: HostConfigDao
) {
    val configFlow: Flow<HostConfigEntity?> = hostConfigDao.getConfigFlow()

    suspend fun getOrCreateConfig(): HostConfigEntity {
        val existing = hostConfigDao.getConfig()
        if (existing != null) return existing

        val newConfig = HostConfigEntity(
            id = 1,
            hostDeviceId = "ubaid-${UUID.randomUUID().toString().take(8)}",
            hostDeviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Ubaid)"
        )
        hostConfigDao.saveConfig(newConfig)
        return newConfig
    }

    suspend fun updateConfig(config: HostConfigEntity) {
        hostConfigDao.saveConfig(config)
    }

    suspend fun setPairingNonce(nonce: String, expiresAt: Long) {
        hostConfigDao.updatePairingNonce(nonce, expiresAt)
    }
}
