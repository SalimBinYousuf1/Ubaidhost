package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.repository.DeviceRepository
import com.example.data.repository.HostConfigRepository
import com.example.security.KeystoreHelper
import com.example.system.DeviceTelemetryProvider
import com.example.system.HostFileManager
import com.example.system.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UbaidApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }
    val deviceRepository by lazy {
        DeviceRepository(database.authorizedDeviceDao(), database.sessionLogDao())
    }
    val hostConfigRepository by lazy {
        HostConfigRepository(database.hostConfigDao())
    }
    val keystoreHelper by lazy { KeystoreHelper() }
    val permissionManager by lazy { PermissionManager(this) }
    val telemetryProvider by lazy { DeviceTelemetryProvider(this) }
    val fileManager by lazy { HostFileManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Pre-warm config and keys
        appScope.launch {
            hostConfigRepository.getOrCreateConfig()
        }
    }

    companion object {
        lateinit var instance: UbaidApplication
            private set
    }
}
