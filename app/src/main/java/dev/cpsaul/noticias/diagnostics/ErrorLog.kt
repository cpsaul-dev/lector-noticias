package dev.cpsaul.noticias.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.cpsaul.noticias.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * §5 de ESTANDAR_DISENO.md — Registro local de errores técnicos sin datos personales.
 * Guarda hasta 20 ficheros redactados (sin correos, URLs con parámetros, tokens ni números largos).
 */
object ErrorLog {
    private const val MAX_REPORTS = 20
    private const val REPORTS_SUBDIR = "error_reports"
    private val fileTimestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    @Volatile
    var isEnabled: Boolean = true

    fun installUncaughtHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(appContext, "Cierre inesperado (${thread.name})", throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, action: String, error: Throwable) {
        if (!isEnabled) return
        val timestamp = LocalDateTime.now().format(fileTimestampFormat)
        val fileName = "pulso-${BuildConfig.VERSION_NAME}-$timestamp.txt"
        val body = formatReport(action, error)

        val dir = reportsDir(context)
        runCatching {
            File(dir, fileName).writeText(body)
            pruneOldReports(dir)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { writeToSharedDocuments(context, fileName, body) }
        }
    }

    fun reportCount(context: Context): Int =
        reportsDir(context).listFiles()?.count { it.isFile && it.name.endsWith(".txt") } ?: 0

    fun clearReports(context: Context) {
        reportsDir(context).listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    /**
     * Redacta cualquier dato potencialmente sensible antes de persistir el informe (§5.2).
     */
    fun redact(text: String): String {
        if (text.isBlank()) return text
        var result = text
        // Correos electrónicos
        result = result.replace(Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""), "***@***")
        // URLs con credenciales o parámetros: dejar solo esquema y host
        result = result.replace(Regex("""(https?://)(?:[^/@\s]+@)?([a-zA-Z0-9.-]+(?::\d+)?)[^\s"'<>]*""")) { m ->
            "${m.groupValues[1]}${m.groupValues[2]}"
        }
        // Tokens, claves, contraseñas y Bearer
        result = result.replace(Regex("""(?i)Bearer\s+[a-zA-Z0-9._\-]+"""), "Bearer ***")
        result = result.replace(Regex("""(?i)(key|token|password|secret)\s*=\s*[^\s&,;]+""")) { m ->
            "${m.groupValues[1]}=***"
        }
        result = result.replace(Regex("""x'[0-9a-fA-F]+'"""), "***")
        // Números de 6 cifras o más
        result = result.replace(Regex("""\b\d{6,}\b"""), "#")
        return result
    }

    internal fun formatReport(action: String, error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val stack = redact(sw.toString())
        val safeAction = redact(action)
        return buildString {
            appendLine("App: Pulso (${BuildConfig.APPLICATION_ID})")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build: ${if (BuildConfig.DEBUG) "debug" else "release"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Action: $safeAction")
            appendLine("---")
            appendLine(stack)
        }
    }

    private fun reportsDir(context: Context): File =
        File(context.applicationContext.filesDir, REPORTS_SUBDIR).apply { if (!exists()) mkdirs() }

    private fun pruneOldReports(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > MAX_REPORTS) {
            files.drop(MAX_REPORTS).forEach { it.delete() }
        }
    }

    private fun writeToSharedDocuments(context: Context, fileName: String, content: String) {
        val resolver = context.applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Errores/Pulso")
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
    }
}
