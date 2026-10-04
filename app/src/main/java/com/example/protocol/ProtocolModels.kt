package com.example.protocol

import org.json.JSONArray
import org.json.JSONObject

data class ProtocolMessage(
    val protocolVersion: Int = ProtocolConstants.PROTOCOL_VERSION,
    val messageType: String,
    val requestId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: JSONObject = JSONObject()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("protocolVersion", protocolVersion)
        root.put("messageType", messageType)
        root.put("requestId", requestId)
        root.put("timestamp", timestamp)
        root.put("payload", payload)
        return root.toString()
    }

    companion object {
        fun fromJsonString(json: String): ProtocolMessage {
            val root = JSONObject(json)
            return ProtocolMessage(
                protocolVersion = root.optInt("protocolVersion", ProtocolConstants.PROTOCOL_VERSION),
                messageType = root.getString("messageType"),
                requestId = root.optString("requestId", ""),
                timestamp = root.optLong("timestamp", System.currentTimeMillis()),
                payload = root.optJSONObject("payload") ?: JSONObject()
            )
        }
    }
}

// Structured Input Event Payload
data class InputEvent(
    val action: String,
    val xNormalized: Float = 0f,
    val yNormalized: Float = 0f,
    val durationMs: Long = 50L,
    val pathPoints: List<PointNormalized> = emptyList()
) {
    data class PointNormalized(val x: Float, val y: Float)

    companion object {
        fun fromJson(payload: JSONObject): InputEvent {
            val action = payload.optString("action", ProtocolConstants.ACTION_TAP)
            val x = payload.optDouble("xNormalized", 0.0).toFloat()
            val y = payload.optDouble("yNormalized", 0.0).toFloat()
            val duration = payload.optLong("durationMs", 50L)

            val pointsList = mutableListOf<PointNormalized>()
            val pointsArray = payload.optJSONArray("pathPoints")
            if (pointsArray != null) {
                for (i in 0 until pointsArray.length()) {
                    val p = pointsArray.optJSONObject(i)
                    if (p != null) {
                        pointsList.add(
                            PointNormalized(
                                x = p.optDouble("x", 0.0).toFloat(),
                                y = p.optDouble("y", 0.0).toFloat()
                            )
                        )
                    }
                }
            }

            return InputEvent(
                action = action,
                xNormalized = x,
                yNormalized = y,
                durationMs = duration,
                pathPoints = pointsList
            )
        }
    }
}

// Structured Device Status Payload
data class DeviceStatus(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val chargePlug: String,
    val batteryTemperatureC: Float,
    val networkType: String,
    val wifiSsid: String,
    val wifiSignalStrengthDbm: Int,
    val ipAddress: String,
    val androidVersion: String,
    val sdkInt: Int,
    val deviceModel: String,
    val hostServiceState: String,
    val isScreenOn: Boolean
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("batteryLevel", batteryLevel)
            put("isCharging", isCharging)
            put("chargePlug", chargePlug)
            put("batteryTemperatureC", batteryTemperatureC.toDouble())
            put("networkType", networkType)
            put("wifiSsid", wifiSsid)
            put("wifiSignalStrengthDbm", wifiSignalStrengthDbm)
            put("ipAddress", ipAddress)
            put("androidVersion", androidVersion)
            put("sdkInt", sdkInt)
            put("deviceModel", deviceModel)
            put("hostServiceState", hostServiceState)
            put("isScreenOn", isScreenOn)
        }
    }
}

// Structured Capabilities Payload
data class HostCapabilities(
    val screenStreaming: String, // AVAILABLE, PERMISSION_REQUIRED, RESTRICTED_BY_OS
    val remoteInput: String,     // AVAILABLE, PERMISSION_REQUIRED
    val fileTransfer: String,    // AVAILABLE, PERMISSION_REQUIRED
    val deviceStatus: String,    // AVAILABLE
    val screenWidth: Int,
    val screenHeight: Int,
    val screenDpi: Int,
    val screenRotation: Int
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("screenStreaming", screenStreaming)
            put("remoteInput", remoteInput)
            put("fileTransfer", fileTransfer)
            put("deviceStatus", deviceStatus)
            put("screenResolution", JSONObject().apply {
                put("width", screenWidth)
                put("height", screenHeight)
                put("dpi", screenDpi)
                put("rotation", screenRotation)
            })
        }
    }
}
