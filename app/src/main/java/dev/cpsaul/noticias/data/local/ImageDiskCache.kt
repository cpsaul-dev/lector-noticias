package dev.cpsaul.noticias.data.local

import java.io.File
import java.security.MessageDigest

/**
 * Caché LRU en disco para imágenes principales de artículos offline (§1 y §6 del Manifiesto).
 */
class ImageDiskCache(
    private val cacheDir: File,
    private val maxSizeBytes: Long = 50L * 1024L * 1024L
) {
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun fileForUrl(url: String): File {
        val hash = sha256(url).take(32)
        return File(cacheDir, "$hash.img")
    }

    fun put(url: String, bytes: ByteArray): String {
        val target = fileForUrl(url)
        target.writeBytes(bytes)
        target.setLastModified(System.currentTimeMillis())
        evictIfNeeded()
        return target.absolutePath
    }

    fun get(url: String): File? {
        val file = fileForUrl(url)
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    fun sizeBytes(): Long {
        val files = cacheDir.listFiles() ?: return 0L
        return files.filter { it.isFile }.sumOf { it.length() }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var currentSize = files.sumOf { it.length() }
        for (file in files) {
            if (currentSize <= maxSizeBytes) break
            val len = file.length()
            if (file.delete()) {
                currentSize -= len
            }
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
