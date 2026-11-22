package dev.cpsaul.noticias.ai

/**
 * Redacción de datos personales identificables (PII) y secretos antes de transmitir
 * cualquier payload a proveedores en la nube (§6 de aihub-integration.md).
 */
object PayloadRedactor {
    private val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val phoneRegex = Regex("""(?<!\d)(?:\+?\d{1,3}[\s.-]?)?(?:\(?\d{2,4}\)?[\s.-]?)?\d{3}[\s.-]?\d{3,4}(?!\d)""")
    private val secretRegex = Regex("""(?i)\b(bearer\s+[a-z0-9._\-]+|(?:api[_-]?key|token|password|secret)\s*[:=]\s*[^\s,;]+)""")

    fun redact(input: String): String {
        if (input.isBlank()) return input
        return input
            .replace(emailRegex, "[correo-redactado]")
            .replace(secretRegex, "[credencial-redactada]")
            .replace(phoneRegex) { match ->
                val digits = match.value.count { it.isDigit() }
                if (digits >= 9) "[telefono-redactado]" else match.value
            }
    }
}
