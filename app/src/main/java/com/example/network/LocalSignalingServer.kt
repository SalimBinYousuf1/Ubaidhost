package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

class LocalSignalingServer(
    private val port: Int = 8999,
    private val onMessageReceived: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val activeClients = CopyOnWriteArrayList<Socket>()

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("LocalSignalingServer", "Server listening on port $port")

                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    activeClients.add(client)
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e("LocalSignalingServer", "Server error", e)
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        scope.launch {
            try {
                val input = client.getInputStream()
                val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
                val output = client.getOutputStream()

                // HTTP WebSocket Handshake
                var line: String?
                var isWebSocket = false
                var secKey = ""

                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    val lower = line!!.lowercase()
                    if (lower.startsWith("upgrade:") && lower.contains("websocket")) {
                        isWebSocket = true
                    }
                    if (lower.startsWith("sec-websocket-key:")) {
                        secKey = line!!.substringAfter(":").trim()
                    }
                }

                if (isWebSocket && secKey.isNotEmpty()) {
                    performHandshake(output, secKey)
                    readWebSocketFrames(input, client)
                } else {
                    client.close()
                }
            } catch (e: Exception) {
                Log.d("LocalSignalingServer", "Client disconnected")
            } finally {
                activeClients.remove(client)
            }
        }
    }

    private fun performHandshake(output: OutputStream, key: String) {
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hash = sha1.digest((key + magic).toByteArray(StandardCharsets.UTF_8))
        val acceptKey = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        output.write(response.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun readWebSocketFrames(input: java.io.InputStream, client: Socket) {
        while (isRunning && !client.isClosed) {
            val b0 = input.read()
            if (b0 == -1) break
            val b1 = input.read()
            if (b1 == -1) break

            val opcode = b0 and 0x0F
            if (opcode == 8) { // Connection Close
                break
            }

            val isMasked = (b1 and 0x80) != 0
            var payloadLen = (b1 and 0x7F).toLong()

            if (payloadLen == 126L) {
                val b2 = input.read()
                val b3 = input.read()
                payloadLen = ((b2 shl 8) or b3).toLong()
            } else if (payloadLen == 127L) {
                var len = 0L
                for (i in 0 until 8) {
                    len = (len shl 8) or (input.read().toLong() and 0xFF)
                }
                payloadLen = len
            }

            val mask = ByteArray(4)
            if (isMasked) {
                input.read(mask)
            }

            val payload = ByteArray(payloadLen.toInt())
            var bytesRead = 0
            while (bytesRead < payloadLen) {
                val r = input.read(payload, bytesRead, (payloadLen - bytesRead).toInt())
                if (r == -1) break
                bytesRead += r
            }

            if (isMasked) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }

            if (opcode == 1) { // Text frame
                val messageText = String(payload, StandardCharsets.UTF_8)
                onMessageReceived(messageText)
            }
        }
    }

    fun broadcast(message: String) {
        val payload = message.toByteArray(StandardCharsets.UTF_8)
        val frame = encodeFrame(payload)

        for (client in activeClients) {
            try {
                if (!client.isClosed) {
                    val out = client.getOutputStream()
                    out.write(frame)
                    out.flush()
                }
            } catch (e: Exception) {
                activeClients.remove(client)
            }
        }
    }

    private fun encodeFrame(payload: ByteArray): ByteArray {
        val len = payload.size
        val header = mutableListOf<Byte>()
        header.add(0x81.toByte()) // FIN + text opcode

        when {
            len <= 125 -> {
                header.add(len.toByte())
            }
            len <= 65535 -> {
                header.add(126.toByte())
                header.add(((len shr 8) and 0xFF).toByte())
                header.add((len and 0xFF).toByte())
            }
            else -> {
                header.add(127.toByte())
                for (i in 7 downTo 0) {
                    header.add(((len.toLong() shr (i * 8)) and 0xFF).toByte())
                }
            }
        }

        return header.toByteArray() + payload
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        for (client in activeClients) {
            try { client.close() } catch (_: Exception) {}
        }
        activeClients.clear()
    }
}
