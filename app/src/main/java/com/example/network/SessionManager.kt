package com.example.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.repository.DeviceRepository
import com.example.data.repository.HostConfigRepository
import com.example.protocol.DeviceStatus
import com.example.protocol.HostCapabilities
import com.example.protocol.InputEvent
import com.example.protocol.ProtocolConstants
import com.example.protocol.ProtocolMessage
import com.example.security.KeystoreHelper
import com.example.service.ScreenCaptureManager
import com.example.service.UbaidAccessibilityService
import com.example.system.DeviceTelemetryProvider
import com.example.system.HostFileManager
import com.example.system.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SessionManager(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val hostConfigRepository: HostConfigRepository,
    private val keystoreHelper: KeystoreHelper,
    private val permissionManager: PermissionManager,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val fileManager: HostFileManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _hostState = MutableStateFlow(HostState.INITIALIZING)
    val hostState: StateFlow<HostState> = _hostState.asStateFlow()

    private val _activeSessionControllerName = MutableStateFlow<String?>(null)
    val activeSessionControllerName: StateFlow<String?> = _activeSessionControllerName.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private var activeSessionDeviceId: String? = null
    private var activeSessionStartTime: Long = 0L
    private var sessionBytesTransferred: Long = 0L
    private var pendingChallengeNonce: String = ""

    // Transport callback for sending messages back to controller
    var sendMessageCallback: ((String) -> Unit)? = null

    init {
        evaluateInitialState()
    }

    fun evaluateInitialState() {
        val permissions = permissionManager.checkAllPermissions()
        if (!permissions.isReadyForHostSession) {
            _hostState.value = HostState.SETUP_REQUIRED
        } else {
            _hostState.value = HostState.ONLINE
        }
    }

    fun setHostState(state: HostState) {
        _hostState.value = state
    }

    fun handleIncomingMessage(rawJson: String) {
        scope.launch {
            try {
                val message = ProtocolMessage.fromJsonString(rawJson)

                // 1. Version enforcement
                if (message.protocolVersion != ProtocolConstants.PROTOCOL_VERSION) {
                    sendError(
                        requestId = message.requestId,
                        code = ProtocolConstants.ERR_PROTOCOL_VERSION_MISMATCH,
                        message = "Unsupported protocol version ${message.protocolVersion}. Required: ${ProtocolConstants.PROTOCOL_VERSION}"
                    )
                    return@launch
                }

                // 2. Dispatch by message type
                when (message.messageType) {
                    ProtocolConstants.TYPE_HELLO -> handleHello(message)
                    ProtocolConstants.TYPE_PAIR_REQUEST -> handlePairRequest(message)
                    ProtocolConstants.TYPE_AUTH -> handleAuth(message)
                    ProtocolConstants.TYPE_SESSION_START -> handleSessionStart(message)
                    ProtocolConstants.TYPE_INPUT_EVENT -> handleInputEvent(message)
                    ProtocolConstants.TYPE_TEXT_INPUT -> handleTextInput(message)
                    ProtocolConstants.TYPE_FILE_REQUEST -> handleFileRequest(message)
                    ProtocolConstants.TYPE_PING -> handlePing(message)
                    ProtocolConstants.TYPE_SESSION_END -> handleSessionEnd(message)
                    else -> {
                        sendError(message.requestId, "UNKNOWN_MESSAGE_TYPE", "Unrecognized message type: ${message.messageType}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "Error processing message", e)
                sendError("", ProtocolConstants.ERR_INTERNAL_SERVER, e.message ?: "Malformed message")
            }
        }
    }

    private suspend fun handleHello(message: ProtocolMessage) {
        val payload = message.payload
        val clientDeviceId = payload.optString("clientDeviceId", "")
        val clientDeviceName = payload.optString("clientDeviceName", "Unknown Controller")

        val config = hostConfigRepository.getOrCreateConfig()
        pendingChallengeNonce = keystoreHelper.generateSecureNonce()

        val ackPayload = JSONObject().apply {
            put("hostDeviceId", config.hostDeviceId)
            put("hostDeviceName", config.hostDeviceName)
            put("challengeNonce", pendingChallengeNonce)
            put("hostPublicKey", keystoreHelper.getHostPublicKeyBase64())
            put("hostFingerprint", keystoreHelper.getHostPublicKeyFingerprint())
        }

        val ackMessage = ProtocolMessage(
            messageType = ProtocolConstants.TYPE_HELLO_ACK,
            requestId = message.requestId,
            payload = ackPayload
        )
        sendMessage(ackMessage)
    }

    private suspend fun handlePairRequest(message: ProtocolMessage) {
        val payload = message.payload
        val pairingSecret = payload.optString("pairingSecret", "")
        val clientDeviceId = payload.optString("clientDeviceId", "")
        val clientDeviceName = payload.optString("clientDeviceName", "Salim Controller")
        val clientPublicKey = payload.optString("clientPublicKey", "")

        val config = hostConfigRepository.getOrCreateConfig()

        // Validate pairing secret and expiration
        val isValidSecret = (config.pairingNonce.isNotEmpty() &&
                config.pairingNonce == pairingSecret &&
                System.currentTimeMillis() <= config.pairingNonceExpiresAt)

        if (!isValidSecret) {
            val responsePayload = JSONObject().apply {
                put("paired", false)
                put("errorCode", "INVALID_PAIRING_SECRET")
                put("message", "The pairing code is invalid or has expired.")
            }
            sendMessage(
                ProtocolMessage(
                    messageType = ProtocolConstants.TYPE_PAIR_RESPONSE,
                    requestId = message.requestId,
                    payload = responsePayload
                )
            )
            return
        }

        // Store authorized device in Room
        val fingerprint = keystoreHelper.computeFingerprint(clientPublicKey)
        deviceRepository.authorizeDevice(
            deviceId = clientDeviceId,
            deviceName = clientDeviceName,
            publicKey = clientPublicKey,
            fingerprint = fingerprint
        )

        // Clear used one-time nonce
        hostConfigRepository.setPairingNonce("", 0L)

        // Sign confirmation
        val signature = keystoreHelper.signData(clientDeviceId.toByteArray(Charsets.UTF_8))

        val responsePayload = JSONObject().apply {
            put("paired", true)
            put("hostDeviceId", config.hostDeviceId)
            put("hostFingerprint", keystoreHelper.getHostPublicKeyFingerprint())
            put("hostSignature", signature)
        }

        sendMessage(
            ProtocolMessage(
                messageType = ProtocolConstants.TYPE_PAIR_RESPONSE,
                requestId = message.requestId,
                payload = responsePayload
            )
        )

        _hostState.value = HostState.PAIRED
    }

    private suspend fun handleAuth(message: ProtocolMessage) {
        val payload = message.payload
        val clientDeviceId = payload.optString("clientDeviceId", "")
        val signature = payload.optString("signature", "")

        // Check database authorization & revocation
        val authorizedDevice = deviceRepository.getDeviceById(clientDeviceId)
        if (authorizedDevice == null || authorizedDevice.isRevoked) {
            val resultPayload = JSONObject().apply {
                put("authenticated", false)
                put("errorCode", ProtocolConstants.ERR_DEVICE_REVOKED)
                put("message", "Device is not authorized or has been revoked by host.")
            }
            sendMessage(
                ProtocolMessage(
                    messageType = ProtocolConstants.TYPE_AUTH_RESULT,
                    requestId = message.requestId,
                    payload = resultPayload
                )
            )
            return
        }

        // Verify cryptographic signature against challenge nonce
        val nonceBytes = pendingChallengeNonce.toByteArray(Charsets.UTF_8)
        val validSig = keystoreHelper.verifyControllerSignature(
            publicKeyBase64 = authorizedDevice.publicKey,
            data = nonceBytes,
            signatureBase64 = signature
        )

        if (!validSig && pendingChallengeNonce.isNotEmpty()) {
            val resultPayload = JSONObject().apply {
                put("authenticated", false)
                put("errorCode", ProtocolConstants.ERR_INVALID_SIGNATURE)
                put("message", "Cryptographic signature verification failed.")
            }
            sendMessage(
                ProtocolMessage(
                    messageType = ProtocolConstants.TYPE_AUTH_RESULT,
                    requestId = message.requestId,
                    payload = resultPayload
                )
            )
            return
        }

        // Authentication successful
        val sessionId = "sess-${UUID.randomUUID().toString().take(12)}"
        val sessionExpiry = System.currentTimeMillis() + (3600 * 1000) // 1 hour token

        activeSessionDeviceId = clientDeviceId
        _activeSessionControllerName.value = authorizedDevice.deviceName
        _activeSessionId.value = sessionId
        activeSessionStartTime = System.currentTimeMillis()
        sessionBytesTransferred = 0L

        deviceRepository.updateLastConnected(clientDeviceId)
        deviceRepository.logSessionStart(sessionId, clientDeviceId, authorizedDevice.deviceName)

        _hostState.value = HostState.ACTIVE_SESSION

        val authPayload = JSONObject().apply {
            put("authenticated", true)
            put("sessionId", sessionId)
            put("expiresAt", sessionExpiry)
            put("permissions", JSONArray(authorizedDevice.permissionsGranted.split(",")))
        }

        sendMessage(
            ProtocolMessage(
                messageType = ProtocolConstants.TYPE_AUTH_RESULT,
                requestId = message.requestId,
                payload = authPayload
            )
        )

        // Broadcast authoritative capabilities immediately
        sendCapabilities(message.requestId)

        // Broadcast initial device status
        sendDeviceStatus()
    }

    private fun handleSessionStart(message: ProtocolMessage) {
        val payload = message.payload
        val preferredFps = payload.optInt("preferredFps", 30)

        val streamingStarted = ScreenCaptureManager.instance.startCapture(
            context = context,
            fps = preferredFps
        )

        val readyPayload = JSONObject().apply {
            put("streamActive", streamingStarted)
            put("codec", "H264")
            val metrics = telemetryProvider.getDisplayMetrics()
            put("actualWidth", metrics.widthPixels)
            put("actualHeight", metrics.heightPixels)
            put("actualFps", preferredFps)
        }

        sendMessage(
            ProtocolMessage(
                messageType = ProtocolConstants.TYPE_SESSION_READY,
                requestId = message.requestId,
                payload = readyPayload
            )
        )
    }

    private suspend fun handleInputEvent(message: ProtocolMessage) {
        val accessibilityService = UbaidAccessibilityService.getInstance()
        if (accessibilityService == null) {
            sendError(
                requestId = message.requestId,
                code = ProtocolConstants.ERR_ACCESSIBILITY_DISABLED,
                message = "Accessibility service is not active on host device."
            )
            return
        }

        val inputEvent = InputEvent.fromJson(message.payload)
        val result = accessibilityService.executeInputEvent(inputEvent)

        if (!result.first) {
            sendError(
                requestId = message.requestId,
                code = ProtocolConstants.ERR_GESTURE_FAILED,
                message = "Gesture execution failed: ${result.second}"
            )
        }
    }

    private fun handleTextInput(message: ProtocolMessage) {
        val accessibilityService = UbaidAccessibilityService.getInstance()
        if (accessibilityService == null) {
            sendError(
                requestId = message.requestId,
                code = ProtocolConstants.ERR_ACCESSIBILITY_DISABLED,
                message = "Accessibility service is not active."
            )
            return
        }

        val text = message.payload.optString("text", "")
        val append = message.payload.optBoolean("append", false)
        val result = accessibilityService.injectText(text, append)

        if (!result.first) {
            sendError(
                requestId = message.requestId,
                code = "TEXT_INPUT_FAILED",
                message = result.second
            )
        }
    }

    private fun handleFileRequest(message: ProtocolMessage) {
        val payload = message.payload
        val op = payload.optString("operation", "LIST")
        val path = payload.optString("path", "DOWNLOADS")

        when (op.uppercase()) {
            "LIST" -> {
                val listResult = fileManager.listPermittedDirectory(path)
                val entriesArray = JSONArray()
                for (entry in listResult.second) {
                    entriesArray.put(entry.toJsonObject())
                }

                val responsePayload = JSONObject().apply {
                    put("operation", "LIST")
                    put("status", if (listResult.first) "SUCCESS" else "ERROR")
                    put("entries", entriesArray)
                }

                sendMessage(
                    ProtocolMessage(
                        messageType = ProtocolConstants.TYPE_FILE_RESPONSE,
                        requestId = message.requestId,
                        payload = responsePayload
                    )
                )
            }
            "DOWNLOAD" -> {
                val filePath = payload.optString("filePath", "")
                val offset = payload.optLong("offset", 0L)
                val length = payload.optInt("length", 65536)

                val chunk = fileManager.readFileChunk(filePath, offset, length)
                if (chunk == null) {
                    sendError(message.requestId, ProtocolConstants.ERR_FILE_NOT_FOUND, "File not accessible or out of bounds")
                    return
                }

                val base64Data = Base64.encodeToString(chunk, Base64.NO_WRAP)
                val responsePayload = JSONObject().apply {
                    put("operation", "DOWNLOAD")
                    put("status", "SUCCESS")
                    put("filePath", filePath)
                    put("offset", offset)
                    put("chunkLength", chunk.size)
                    put("dataBase64", base64Data)
                }

                sendMessage(
                    ProtocolMessage(
                        messageType = ProtocolConstants.TYPE_FILE_RESPONSE,
                        requestId = message.requestId,
                        payload = responsePayload
                    )
                )
            }
        }
    }

    private fun handlePing(message: ProtocolMessage) {
        sendMessage(
            ProtocolMessage(
                messageType = ProtocolConstants.TYPE_PONG,
                requestId = message.requestId,
                payload = JSONObject()
            )
        )
    }

    private suspend fun handleSessionEnd(message: ProtocolMessage) {
        terminateActiveSession(message.payload.optString("reason", "CONTROLLER_DISCONNECTED"))
    }

    suspend fun terminateActiveSession(reason: String) {
        val sessId = _activeSessionId.value ?: return

        ScreenCaptureManager.instance.stopCapture()
        deviceRepository.logSessionEnd(sessId, sessionBytesTransferred, reason)

        _activeSessionId.value = null
        _activeSessionControllerName.value = null
        activeSessionDeviceId = null

        val perms = permissionManager.checkAllPermissions()
        _hostState.value = if (perms.isReadyForHostSession) HostState.ONLINE else HostState.SETUP_REQUIRED

        sendMessage(
            ProtocolMessage(
                messageType = ProtocolConstants.TYPE_SESSION_END,
                requestId = UUID.randomUUID().toString(),
                payload = JSONObject().apply { put("reason", reason) }
            )
        )
    }

    fun sendDeviceStatus() {
        val status = telemetryProvider.getRealDeviceStatus(hostState.value.name)
        val msg = ProtocolMessage(
            messageType = ProtocolConstants.TYPE_DEVICE_STATUS,
            requestId = UUID.randomUUID().toString(),
            payload = status.toJsonObject()
        )
        sendMessage(msg)
    }

    fun sendCapabilities(requestId: String = UUID.randomUUID().toString()) {
        val permissions = permissionManager.checkAllPermissions()
        val metrics = telemetryProvider.getDisplayMetrics()

        val caps = HostCapabilities(
            screenStreaming = if (permissions.isMediaProjectionActive) "AVAILABLE" else "PERMISSION_REQUIRED",
            remoteInput = if (permissions.isAccessibilityEnabled) "AVAILABLE" else "PERMISSION_REQUIRED",
            fileTransfer = "AVAILABLE",
            deviceStatus = "AVAILABLE",
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            screenDpi = metrics.densityDpi,
            screenRotation = 0
        )

        sendMessage(
            ProtocolMessage(
                messageType = ProtocolConstants.TYPE_CAPABILITIES,
                requestId = requestId,
                payload = caps.toJsonObject()
            )
        )
    }

    private fun sendError(requestId: String, code: String, message: String) {
        val errPayload = JSONObject().apply {
            put("errorCode", code)
            put("message", message)
            put("isFatal", false)
        }
        sendMessage(
            ProtocolMessage(
                messageType = ProtocolConstants.TYPE_ERROR,
                requestId = requestId,
                payload = errPayload
            )
        )
    }

    private fun sendMessage(message: ProtocolMessage) {
        val json = message.toJsonString()
        sessionBytesTransferred += json.length
        sendMessageCallback?.invoke(json)
    }
}
