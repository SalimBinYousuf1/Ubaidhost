package com.example.system

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.security.CryptoUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class FileEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("path", path)
            put("sizeBytes", sizeBytes)
            put("lastModified", lastModified)
            put("isDirectory", isDirectory)
            put("mimeType", mimeType)
        }
    }
}

class HostFileManager(private val context: Context) {

    fun listPermittedDirectory(relativePath: String): Pair<Boolean, List<FileEntry>> {
        val rootDir = resolveSafeDirectory(relativePath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return Pair(false, emptyList())
        }

        val files = rootDir.listFiles() ?: return Pair(true, emptyList())
        val entries = files.map { file ->
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            FileEntry(
                name = file.name,
                path = file.absolutePath,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                mimeType = mime
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        return Pair(true, entries)
    }

    fun readFileChunk(filePath: String, offset: Long, length: Int): ByteArray? {
        val file = File(filePath)
        if (!isPathPermitted(file) || !file.exists() || file.isDirectory) {
            return null
        }

        return try {
            FileInputStream(file).use { fis ->
                fis.skip(offset)
                val buffer = ByteArray(length)
                val bytesRead = fis.read(buffer)
                if (bytesRead <= 0) return ByteArray(0)
                if (bytesRead < length) {
                    buffer.copyOf(bytesRead)
                } else {
                    buffer
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun computeFileSha256(filePath: String): String? {
        val file = File(filePath)
        if (!isPathPermitted(file) || !file.exists() || file.isDirectory) {
            return null
        }
        return CryptoUtils.sha256(file)
    }

    private fun resolveSafeDirectory(relativePath: String): File {
        return when (relativePath.uppercase()) {
            "DOWNLOADS", "DOWNLOAD" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            "CACHE" -> context.cacheDir
            else -> context.filesDir
        }
    }

    private fun isPathPermitted(file: File): Boolean {
        val canonical = file.canonicalPath
        val filesDir = context.filesDir.canonicalPath
        val cacheDir = context.cacheDir.canonicalPath
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath

        return canonical.startsWith(filesDir) ||
                canonical.startsWith(cacheDir) ||
                canonical.startsWith(downloads)
    }
}
