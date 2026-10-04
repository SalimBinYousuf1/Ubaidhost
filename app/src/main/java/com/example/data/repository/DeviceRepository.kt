package com.example.data.repository

import com.example.data.dao.AuthorizedDeviceDao
import com.example.data.dao.SessionLogDao
import com.example.data.entity.AuthorizedDeviceEntity
import com.example.data.entity.SessionLogEntity
import kotlinx.coroutines.flow.Flow

class DeviceRepository(
    private val authorizedDeviceDao: AuthorizedDeviceDao,
    private val sessionLogDao: SessionLogDao
) {
    val activeDevices: Flow<List<AuthorizedDeviceEntity>> = authorizedDeviceDao.getActiveDevices()
    val allDevices: Flow<List<AuthorizedDeviceEntity>> = authorizedDeviceDao.getAllDevices()
    val recentSessions: Flow<List<SessionLogEntity>> = sessionLogDao.getRecentSessions()
    val activeDeviceCount: Flow<Int> = authorizedDeviceDao.getActiveDeviceCount()

    suspend fun getDeviceById(deviceId: String): AuthorizedDeviceEntity? {
        return authorizedDeviceDao.getDeviceById(deviceId)
    }

    suspend fun getDeviceByFingerprint(fingerprint: String): AuthorizedDeviceEntity? {
        return authorizedDeviceDao.getDeviceByFingerprint(fingerprint)
    }

    suspend fun authorizeDevice(
        deviceId: String,
        deviceName: String,
        publicKey: String,
        fingerprint: String
    ) {
        val entity = AuthorizedDeviceEntity(
            deviceId = deviceId,
            deviceName = deviceName,
            publicKey = publicKey,
            fingerprint = fingerprint,
            pairedAt = System.currentTimeMillis(),
            lastConnectedAt = System.currentTimeMillis(),
            isRevoked = false
        )
        authorizedDeviceDao.insertOrUpdate(entity)
    }

    suspend fun revokeDevice(deviceId: String) {
        authorizedDeviceDao.revokeDevice(deviceId)
    }

    suspend fun deleteDevice(deviceId: String) {
        authorizedDeviceDao.deleteDevice(deviceId)
    }

    suspend fun updateLastConnected(deviceId: String) {
        authorizedDeviceDao.updateLastConnected(deviceId, System.currentTimeMillis())
    }

    suspend fun logSessionStart(sessionId: String, deviceId: String, deviceName: String): Long {
        return sessionLogDao.insertSession(
            SessionLogEntity(
                sessionId = sessionId,
                controllerDeviceId = deviceId,
                controllerDeviceName = deviceName,
                startedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun logSessionEnd(sessionId: String, bytesTransferred: Long, reason: String) {
        sessionLogDao.closeSession(
            sessionId = sessionId,
            endedAt = System.currentTimeMillis(),
            bytes = bytesTransferred,
            reason = reason
        )
    }

    suspend fun clearSessionLogs() {
        sessionLogDao.clearLogs()
    }
}
