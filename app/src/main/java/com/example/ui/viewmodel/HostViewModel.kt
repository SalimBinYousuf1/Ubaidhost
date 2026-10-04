package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.AuthorizedDeviceEntity
import com.example.data.entity.HostConfigEntity
import com.example.data.repository.DeviceRepository
import com.example.data.repository.HostConfigRepository
import com.example.network.HostState
import com.example.protocol.DeviceStatus
import com.example.security.KeystoreHelper
import com.example.service.UbaidHostService
import com.example.system.DeviceTelemetryProvider
import com.example.system.PermissionManager
import com.example.system.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder

sealed class HostNavigationScreen {
    object Dashboard : HostNavigationScreen()
    object Setup : HostNavigationScreen()
    object Pairing : HostNavigationScreen()
    object AuthorizedDevices : HostNavigationScreen()
    object TelemetryDetails : HostNavigationScreen()
    object OemGuide : HostNavigationScreen()
    object ProtocolInspector : HostNavigationScreen()
}

class HostViewModel(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val hostConfigRepository: HostConfigRepository,
    private val keystoreHelper: KeystoreHelper,
    private val permissionManager: PermissionManager,
    private val telemetryProvider: DeviceTelemetryProvider
) : ViewModel() {

    private val _currentScreen = MutableStateFlow<HostNavigationScreen>(HostNavigationScreen.Dashboard)
    val currentScreen: StateFlow<HostNavigationScreen> = _currentScreen.asStateFlow()

    private val _permissionStatus = MutableStateFlow(permissionManager.checkAllPermissions())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _deviceStatus = MutableStateFlow(telemetryProvider.getRealDeviceStatus("INITIALIZING"))
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    val authorizedDevices: StateFlow<List<AuthorizedDeviceEntity>> =
        deviceRepository.allDevices.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val hostConfig: StateFlow<HostConfigEntity?> =
        hostConfigRepository.configFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _pairingSecret = MutableStateFlow("")
    val pairingSecret: StateFlow<String> = _pairingSecret.asStateFlow()

    private val _pairingPin = MutableStateFlow("")
    val pairingPin: StateFlow<String> = _pairingPin.asStateFlow()

    private val _pairingUri = MutableStateFlow("")
    val pairingUri: StateFlow<String> = _pairingUri.asStateFlow()

    private val _pairingExpiresAt = MutableStateFlow(0L)
    val pairingExpiresAt: StateFlow<Long> = _pairingExpiresAt.asStateFlow()

    val hostState: StateFlow<HostState>
        get() = UbaidHostService.instance?.sessionManager?.hostState
            ?: MutableStateFlow(if (_permissionStatus.value.isReadyForHostSession) HostState.READY else HostState.SETUP_REQUIRED)

    val activeSessionControllerName: StateFlow<String?>
        get() = UbaidHostService.instance?.sessionManager?.activeSessionControllerName
            ?: MutableStateFlow(null)

    init {
        refreshAll()
        startPeriodicTelemetry()
    }

    fun navigateTo(screen: HostNavigationScreen) {
        _currentScreen.value = screen
        refreshAll()
    }

    fun navigateBack() {
        _currentScreen.value = HostNavigationScreen.Dashboard
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _permissionStatus.value = permissionManager.checkAllPermissions()
            val stateName = UbaidHostService.instance?.sessionManager?.hostState?.value?.name ?: "STOPPED"
            _deviceStatus.value = telemetryProvider.getRealDeviceStatus(stateName)
        }
    }

    private fun startPeriodicTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(4000)
                _permissionStatus.value = permissionManager.checkAllPermissions()
                val stateName = UbaidHostService.instance?.sessionManager?.hostState?.value?.name ?: "STOPPED"
                _deviceStatus.value = telemetryProvider.getRealDeviceStatus(stateName)
            }
        }
    }

    fun toggleHostService() {
        if (UbaidHostService.isRunning()) {
            UbaidHostService.stop(context)
        } else {
            UbaidHostService.start(context)
        }
        refreshAll()
    }

    fun generateNewPairingSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val secret = keystoreHelper.generateSecureNonce(24)
            val pin = keystoreHelper.generateNumericPairingPin()
            val expiresAt = System.currentTimeMillis() + (10 * 60 * 1000L) // 10 minutes

            hostConfigRepository.setPairingNonce(secret, expiresAt)

            val config = hostConfigRepository.getOrCreateConfig()
            val pubKey = keystoreHelper.getHostPublicKeyBase64()

            val encodedName = URLEncoder.encode(config.hostDeviceName, "UTF-8")
            val uri = "ubaid://pair?v=1&id=${config.hostDeviceId}&name=$encodedName&pk=${URLEncoder.encode(pubKey, "UTF-8")}&secret=$secret&pin=$pin&port=${config.localPort}&relay=${URLEncoder.encode(config.signalingServerUrl, "UTF-8")}"

            _pairingSecret.value = secret
            _pairingPin.value = pin
            _pairingUri.value = uri
            _pairingExpiresAt.value = expiresAt

            UbaidHostService.instance?.sessionManager?.setHostState(HostState.PAIRING)
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceRepository.revokeDevice(deviceId)
            // If the revoked device is currently connected in active session, terminate it!
            val currentController = UbaidHostService.instance?.sessionManager?.activeSessionControllerName?.value
            val target = deviceRepository.getDeviceById(deviceId)
            if (target != null && target.deviceName == currentController) {
                UbaidHostService.instance?.sessionManager?.terminateActiveSession("REVOKED_BY_HOST")
            }
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceRepository.deleteDevice(deviceId)
        }
    }

    fun disconnectActiveSession() {
        viewModelScope.launch(Dispatchers.IO) {
            UbaidHostService.instance?.sessionManager?.terminateActiveSession("USER_MANUAL_DISCONNECT")
            refreshAll()
        }
    }

    fun getHostPublicKeyFingerprint(): String {
        return keystoreHelper.getHostPublicKeyFingerprint()
    }

    fun getHostPublicKeyBase64(): String {
        return keystoreHelper.getHostPublicKeyBase64()
    }
}

class HostViewModelFactory(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val hostConfigRepository: HostConfigRepository,
    private val keystoreHelper: KeystoreHelper,
    private val permissionManager: PermissionManager,
    private val telemetryProvider: DeviceTelemetryProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HostViewModel(
            context,
            deviceRepository,
            hostConfigRepository,
            keystoreHelper,
            permissionManager,
            telemetryProvider
        ) as T
    }
}
