# UBAID ↔ SALIM COMMUNICATION PROTOCOL SPECIFICATION (v1)

**Document Version:** 1.0.0  
**Target Systems:**  
- **Host Device (APK 1):** Ubaid (Android Host, Phone A at Home)  
- **Controller Device (APK 2):** Salim (Android Controller, Phone B carried by user)  
**Security Standard:** Zero-Trust, Hardware Keystore Asymmetric Identity, Encrypted WebSocket / WebRTC Signaling, Nonce-based Mutual Authentication, Real-time Revocation Enforcement.

---

## 1. System Overview & Architecture Boundary

```
+-----------------------------------------------------------------------------------+
|                                  INTERNET / WAN                                   |
|                Secure Signaling Relay (WSS) / Direct LAN Fallback                 |
+-----------------------------------------------------------------------------------+
                             ^                               ^
                             |                               |
       WebSocket / WebRTC Signaling            WebSocket / WebRTC Signaling
                             |                               |
                             v                               v
             +-------------------------------+   +-------------------------------+
             |         APK 1: UBAID          |   |         APK 2: SALIM          |
             |       (AUTHORITATIVE)         |   |         (CONTROLLER)          |
             |                               |   |                               |
             | - MediaProjection Streamer    |   | - Remote Viewport (Surface)   |
             | - Accessibility Input Injector|   | - Input Gesture Dispatcher    |
             | - Android KeyStore Signer     |   | - Client KeyStore Signer      |
             | - Device Telemetry Monitor    |   | - Telemetry Dashboard         |
             | - Room Database Persistence   |   | - Session Controller          |
             | - Foreground Persistent Svc   |   |                               |
             +-------------------------------+   +-------------------------------+
```

### Core Tenet
Ubaid is the **authoritative host**. Salim never commands the device without Ubaid explicitly verifying:
1. Valid device authorization in local database (not revoked).
2. Cryptographic signature matching the registered public key.
3. Active runtime permission on the host (Accessibility, MediaProjection, Storage).
4. Valid, non-expired session nonce.

---

## 2. Cryptographic Primitives & Identity

| Primitive | Specification |
| :--- | :--- |
| **Host KeyStore** | Hardware-backed RSA-2048 or EC P-256 (`AndroidKeyStore`) |
| **Signature Algorithm** | `SHA256withRSA` or `SHA256withECDSA` |
| **Fingerprint** | Hex-encoded `SHA-256(SubjectPublicKeyInfo)` |
| **Session Nonce** | 256-bit cryptographically secure random (`SecureRandom`) |
| **Symmetric Encryption** | AES-256-GCM for encrypted channel payloads |
| **Hash Function** | SHA-256 for file checksums and message integrity |

---

## 3. Protocol Envelope Structure

All messages transmitted between Ubaid and Salim MUST adhere strictly to the JSON message envelope:

```json
{
  "protocolVersion": 1,
  "messageType": "MESSAGE_TYPE_STRING",
  "requestId": "uuid-v4-or-unique-string",
  "timestamp": 1728038400000,
  "payload": {}
}
```

### Field Definitions
- `protocolVersion` (`int`): Must be `1`. If either party receives a version they do not support, they must immediately respond with an `ERROR` (code: `PROTOCOL_VERSION_MISMATCH`) and terminate the socket.
- `messageType` (`string`): One of the defined protocol constants.
- `requestId` (`string`): Unique identifier for correlating request/response pairs (e.g. `FILE_REQUEST` -> `FILE_RESPONSE`).
- `timestamp` (`long`): Epoch timestamp in milliseconds. Messages with timestamps deviating >300 seconds from host time may be rejected to prevent replay attacks.
- `payload` (`object`): Type-specific JSON payload.

---

## 4. Message Types & Schemas

### 4.1 HELLO (Handshake Initiation)
Sent by Salim to initiate contact.
```json
{
  "protocolVersion": 1,
  "messageType": "HELLO",
  "requestId": "req-001",
  "timestamp": 1728038400000,
  "payload": {
    "clientVersion": "1.0.0",
    "clientDeviceId": "salim-device-uuid-9876",
    "clientDeviceName": "Salim Galaxy S24"
  }
}
```
Ubaid responds with `HELLO_ACK`:
```json
{
  "protocolVersion": 1,
  "messageType": "HELLO_ACK",
  "requestId": "req-001",
  "timestamp": 1728038400050,
  "payload": {
    "hostDeviceId": "ubaid-host-uuid-1234",
    "hostDeviceName": "Ubaid Pixel 8",
    "challengeNonce": "base64-random-32-byte-nonce",
    "supportedAuthMethods": ["PUBKEY_CHALLENGE", "PAIRING_SECRET"]
  }
}
```

### 4.2 AUTH & AUTH_RESULT
Salim signs the `challengeNonce` using its private key:
```json
{
  "protocolVersion": 1,
  "messageType": "AUTH",
  "requestId": "req-002",
  "timestamp": 1728038400100,
  "payload": {
    "clientDeviceId": "salim-device-uuid-9876",
    "clientPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
    "signature": "base64-signature-of-challengeNonce",
    "sessionRequestedType": "FULL_CONTROL"
  }
}
```
Ubaid checks:
1. Is `clientDeviceId` in Ubaid's `authorized_devices` database?
2. Is `isRevoked == false`?
3. Does `signature` verify against stored or presented public key?

Response on success:
```json
{
  "protocolVersion": 1,
  "messageType": "AUTH_RESULT",
  "requestId": "req-002",
  "timestamp": 1728038400200,
  "payload": {
    "authenticated": true,
    "sessionId": "sess-live-99887766",
    "expiresAt": 1728042000000,
    "permissions": ["SCREEN_STREAM", "INPUT_INJECTION", "FILE_ACCESS", "DEVICE_STATUS"]
  }
}
```
Response on failure:
```json
{
  "protocolVersion": 1,
  "messageType": "AUTH_RESULT",
  "requestId": "req-002",
  "timestamp": 1728038400200,
  "payload": {
    "authenticated": false,
    "errorCode": "DEVICE_NOT_AUTHORIZED",
    "message": "Controller device is not paired or has been revoked by host"
  }
}
```

### 4.3 CAPABILITIES (Authoritative Host State)
Broadcast by Ubaid upon authentication or permission change:
```json
{
  "protocolVersion": 1,
  "messageType": "CAPABILITIES",
  "requestId": "cap-001",
  "timestamp": 1728038400250,
  "payload": {
    "screenStreaming": "AVAILABLE",
    "remoteInput": "AVAILABLE",
    "fileTransfer": "AVAILABLE",
    "deviceStatus": "AVAILABLE",
    "notificationAccess": "DISABLED",
    "screenResolution": {
      "width": 1080,
      "height": 2400,
      "dpi": 420,
      "rotation": 0
    }
  }
}
```
*Capability values:* `AVAILABLE`, `PERMISSION_REQUIRED`, `RESTRICTED_BY_OS`, `UNSUPPORTED`.

### 4.4 SESSION_START & SESSION_READY
Salim requests video streaming start:
```json
{
  "protocolVersion": 1,
  "messageType": "SESSION_START",
  "requestId": "req-003",
  "timestamp": 1728038400300,
  "payload": {
    "streamType": "SCREEN_STREAM",
    "preferredWidth": 1080,
    "preferredHeight": 2400,
    "preferredFps": 30,
    "codec": "H264"
  }
}
```
Ubaid confirms when MediaProjection VirtualDisplay and encoder are active:
```json
{
  "protocolVersion": 1,
  "messageType": "SESSION_READY",
  "requestId": "req-003",
  "timestamp": 1728038400600,
  "payload": {
    "streamActive": true,
    "actualWidth": 1080,
    "actualHeight": 2400,
    "actualFps": 30,
    "codec": "H264",
    "spsPps": "base64-header-data-if-raw-nal"
  }
}
```

### 4.5 INPUT_EVENT (Remote Touch & Gesture Injection)
Sent by Salim to interact with Ubaid's screen:
```json
{
  "protocolVersion": 1,
  "messageType": "INPUT_EVENT",
  "requestId": "input-101",
  "timestamp": 1728038401000,
  "payload": {
    "action": "TAP",
    "xNormalized": 0.542,
    "yNormalized": 0.381,
    "durationMs": 50,
    "pathPoints": []
  }
}
```

#### Supported Input Actions:
- `TAP`: Single tap at `(xNormalized, yNormalized)`.
- `DOUBLE_TAP`: Two rapid taps at `(xNormalized, yNormalized)`.
- `LONG_PRESS`: Sustained touch for 600ms at `(xNormalized, yNormalized)`.
- `SWIPE`: Drag gesture along `pathPoints: [{"x": 0.5, "y": 0.8}, {"x": 0.5, "y": 0.2}]` with `durationMs`.
- `SCROLL`: Quick swipe in direction.
- `GLOBAL_BACK`: Performs Android System Back (`GLOBAL_ACTION_BACK`).
- `GLOBAL_HOME`: Performs Android System Home (`GLOBAL_ACTION_HOME`).
- `GLOBAL_RECENTS`: Opens App Switcher (`GLOBAL_ACTION_RECENTS`).
- `GLOBAL_NOTIFICATIONS`: Expands Notification Shade (`GLOBAL_ACTION_NOTIFICATIONS`).
- `GLOBAL_QUICK_SETTINGS`: Expands Quick Settings (`GLOBAL_ACTION_QUICK_SETTINGS`).
- `GLOBAL_LOCK_SCREEN`: Locks device screen (`GLOBAL_ACTION_LOCK_SCREEN`).
- `GLOBAL_TAKE_SCREENSHOT`: Triggers OS screenshot (`GLOBAL_ACTION_TAKE_SCREENSHOT`).

*Note on Coordinates:* All coordinates MUST be normalized (`0.0f` to `1.0f`) relative to the host display width and height. Ubaid scales these to actual physical display pixels before constructing `GestureDescription`.

### 4.6 TEXT_INPUT (Remote Typing)
```json
{
  "protocolVersion": 1,
  "messageType": "TEXT_INPUT",
  "requestId": "txt-201",
  "timestamp": 1728038402000,
  "payload": {
    "text": "Hello from Salim controller!",
    "append": false,
    "pressEnter": true
  }
}
```
Ubaid uses `AccessibilityNodeInfo` to locate the currently focused editable node (`findFocus(AccessibilityNodeInfo.FOCUS_INPUT)`) and dispatches `ACTION_SET_TEXT`.

### 4.7 DEVICE_STATUS (Host Telemetry)
Pushed periodically (every 5-10s) or on demand:
```json
{
  "protocolVersion": 1,
  "messageType": "DEVICE_STATUS",
  "requestId": "stat-301",
  "timestamp": 1728038405000,
  "payload": {
    "batteryLevel": 88,
    "isCharging": true,
    "chargePlug": "AC",
    "batteryTemperatureC": 31.2,
    "networkType": "WIFI",
    "wifiSsid": "Home_WiFi_5G",
    "wifiSignalStrengthDbm": -52,
    "ipAddress": "192.168.1.120",
    "androidVersion": "14",
    "sdkInt": 34,
    "deviceModel": "Pixel 8",
    "hostServiceState": "RUNNING",
    "isScreenOn": true
  }
}
```

### 4.8 FILE_REQUEST & FILE_RESPONSE
For browsing and downloading permitted files:
```json
{
  "protocolVersion": 1,
  "messageType": "FILE_REQUEST",
  "requestId": "file-401",
  "timestamp": 1728038406000,
  "payload": {
    "operation": "LIST",
    "path": "DOWNLOADS"
  }
}
```
Response:
```json
{
  "protocolVersion": 1,
  "messageType": "FILE_RESPONSE",
  "requestId": "file-401",
  "timestamp": 1728038406150,
  "payload": {
    "operation": "LIST",
    "status": "SUCCESS",
    "entries": [
      {
        "name": "statement.pdf",
        "sizeBytes": 204850,
        "lastModified": 1728010000000,
        "isDirectory": false,
        "mimeType": "application/pdf"
      }
    ]
  }
}
```

### 4.9 PING & PONG (Keepalive Heartbeat)
Transmitted every 15 seconds. If no PONG is received within 30 seconds, connection is declared dropped and enters `RECONNECTING`.
```json
{
  "protocolVersion": 1,
  "messageType": "PING",
  "requestId": "ping-501",
  "timestamp": 1728038415000,
  "payload": {}
}
```
Response:
```json
{
  "protocolVersion": 1,
  "messageType": "PONG",
  "requestId": "ping-501",
  "timestamp": 1728038415020,
  "payload": {}
}
```

### 4.10 ERROR
Standard error format for any rejected request or internal condition:
```json
{
  "protocolVersion": 1,
  "messageType": "ERROR",
  "requestId": "originating-request-id",
  "timestamp": 1728038415500,
  "payload": {
    "errorCode": "ACCESSIBILITY_DISABLED",
    "message": "Accessibility service has been turned off by user on host device.",
    "isFatal": false
  }
}
```

---

## 5. Pairing Flow (First-Time Setup)

```
       UBAID (HOST)                                    SALIM (CONTROLLER)
            |                                                   |
1. Generate KeyPair in KeyStore                                 |
2. Generate One-Time Pairing Secret                             |
3. Display Pairing QR / Code:                                   |
   ubaid://pair?v=1&hostId=...&pubKey=...&secret=...            |
            |                                                   |
            | <--------- [Scans QR Code or inputs Code] --------|
            |                                                   |
            |<---------- PAIR_REQUEST (Signed with Salim Key) --|
4. Verify One-Time Pairing Secret                               |
5. Verify Salim Key Signature                                   |
6. Save Salim device in DB (Authorized)                         |
            |                                                   |
            |----------> PAIR_SUCCESS (Signed by Ubaid) ------->|
7. Host status transitions to PAIRED                            |
```

### Pairing URI Schema
`ubaid://pair?v=1&id=<HOST_DEVICE_ID>&name=<HOST_NAME>&pk=<BASE64_PUBKEY>&secret=<ONE_TIME_NONCE>&relay=<OPTIONAL_RELAY_URL>`

---

## 6. Security Guarantees & Non-Permitted Boundaries

1. **Android `FLAG_SECURE` Restriction:**
   Android system views (banking apps, password fields, DRM video, system permission prompts on Android 12+) render black frames through `MediaProjection` by design. This is an OS hardware security guarantee and cannot be bypassed. Ubaid reports black frame detection cleanly without crashing.
2. **Accessibility Restrictions:**
   Accessibility Service gestures cannot penetrate device administrator confirmation dialogs, system lock screen PIN entry on certain OEM Knox/SELinux profiles, or package installer verification prompts without user physical confirmation.
3. **Revocation Immediate Invalidation:**
   When the Ubaid device owner taps "Revoke" on an authorized controller:
   - The controller's record in Room DB is immediately marked `isRevoked = true`.
   - Any currently active socket session is abruptly terminated with `SESSION_END` (`REASON_REVOKED`).
   - Any future `AUTH` attempt from that public key fingerprint is rejected outright.
