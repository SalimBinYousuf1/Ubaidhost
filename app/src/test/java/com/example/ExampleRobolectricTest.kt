package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.protocol.InputEvent
import com.example.protocol.ProtocolConstants
import com.example.protocol.ProtocolMessage
import com.example.security.CryptoUtils
import com.example.system.HostFileManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppNameIsUbaid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ubaid", appName)
    }

    @Test
    fun testProtocolMessageSerialization() {
        val payload = JSONObject().apply {
            put("action", ProtocolConstants.ACTION_TAP)
            put("xNormalized", 0.5)
            put("yNormalized", 0.8)
            put("durationMs", 60L)
        }

        val message = ProtocolMessage(
            protocolVersion = 1,
            messageType = ProtocolConstants.TYPE_INPUT_EVENT,
            requestId = "req-123",
            timestamp = 1728000000000L,
            payload = payload
        )

        val jsonString = message.toJsonString()
        val parsed = ProtocolMessage.fromJsonString(jsonString)

        assertEquals(1, parsed.protocolVersion)
        assertEquals(ProtocolConstants.TYPE_INPUT_EVENT, parsed.messageType)
        assertEquals("req-123", parsed.requestId)
        assertEquals(1728000000000L, parsed.timestamp)

        val inputEvent = InputEvent.fromJson(parsed.payload)
        assertEquals(ProtocolConstants.ACTION_TAP, inputEvent.action)
        assertEquals(0.5f, inputEvent.xNormalized)
        assertEquals(0.8f, inputEvent.yNormalized)
        assertEquals(60L, inputEvent.durationMs)
    }

    @Test
    fun testSha256Checksum() {
        val text = "Ubaid_Salim_Security_Token"
        val hash = CryptoUtils.sha256(text)
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun testHostFileManagerSafeDirectories() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = HostFileManager(context)

        val testFile = File(context.filesDir, "test_doc.txt")
        testFile.writeText("Ubaid secure host content")

        val result = manager.listPermittedDirectory("FILES")
        assertTrue(result.first)
        val found = result.second.any { it.name == "test_doc.txt" }
        assertTrue(found)

        val chunk = manager.readFileChunk(testFile.absolutePath, 0, 100)
        assertNotNull(chunk)
        assertEquals("Ubaid secure host content", String(chunk!!))
    }
}
