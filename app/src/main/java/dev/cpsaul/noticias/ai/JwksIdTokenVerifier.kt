package dev.cpsaul.noticias.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

data class ValidatedIdToken(
    val subject: String,
    val email: String?,
    val issuer: String
)

sealed interface IdTokenValidationResult {
    data class Valid(val token: ValidatedIdToken) : IdTokenValidationResult
    data class Invalid(val reason: String) : IdTokenValidationResult
}

/**
 * Verificador criptográfico RS256 de ID tokens OIDC contra el JWKS público de OpenAI
 * (`https://auth.openai.com/.well-known/jwks.json`), comprobando firma, `iss`, `aud`, `exp` y `nonce`.
 */
class JwksIdTokenVerifier(
    private val httpClient: OkHttpClient,
    private val jwksEndpoint: String = ChatGptOAuthConfig.JWKS_ENDPOINT,
    private val expectedIssuer: String = ChatGptOAuthConfig.EXPECTED_ISSUER
) {
    @Volatile
    private var cachedKeys: Map<String, PublicKey> = emptyMap()

    fun verify(
        idToken: String,
        expectedAudience: String,
        expectedNonce: String?,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): IdTokenValidationResult {
        val parts = idToken.split(".")
        if (parts.size != 3) return IdTokenValidationResult.Invalid("Formato JWT inválido")

        val header = runCatching { JSONObject(decodeBase64Url(parts[0]).toString(Charsets.UTF_8)) }.getOrNull()
            ?: return IdTokenValidationResult.Invalid("Cabecera JWT inválida")
        val payload = runCatching { JSONObject(decodeBase64Url(parts[1]).toString(Charsets.UTF_8)) }.getOrNull()
            ?: return IdTokenValidationResult.Invalid("Payload JWT inválido")
        val signatureBytes = runCatching { decodeBase64Url(parts[2]) }.getOrNull()
            ?: return IdTokenValidationResult.Invalid("Firma JWT inválida")

        val alg = header.optString("alg")
        if (alg != "RS256") {
            return IdTokenValidationResult.Invalid("Algoritmo no soportado: $alg")
        }

        val kid = header.optString("kid")
        val publicKey = resolveKey(kid)
            ?: return IdTokenValidationResult.Invalid("Clave pública JWKS no encontrada para kid=$kid")

        val signedInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)
        val sigOk = runCatching {
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(signedInput)
            verifier.verify(signatureBytes)
        }.getOrDefault(false)

        if (!sigOk) {
            return IdTokenValidationResult.Invalid("Firma criptográfica RS256 inválida")
        }

        val iss = payload.optString("iss")
        if (iss != expectedIssuer) {
            return IdTokenValidationResult.Invalid("Issuer inesperado: $iss")
        }

        if (!matchesAudience(payload, expectedAudience)) {
            return IdTokenValidationResult.Invalid("Audience no coincide con client_id emitido")
        }

        val exp = payload.optLong("exp", 0L)
        if (exp <= 0L || nowSeconds > exp + CLOCK_SKEW_SECONDS) {
            return IdTokenValidationResult.Invalid("ID token expirado")
        }

        if (!expectedNonce.isNullOrBlank()) {
            val tokenNonce = payload.optString("nonce")
            if (tokenNonce != expectedNonce) {
                return IdTokenValidationResult.Invalid("Nonce OIDC no coincide")
            }
        }

        val sub = payload.optString("sub").trim()
        if (sub.isBlank()) {
            return IdTokenValidationResult.Invalid("ID token sin claim sub")
        }

        return IdTokenValidationResult.Valid(
            ValidatedIdToken(
                subject = sub,
                email = payload.optString("email").trim().takeIf { it.isNotBlank() },
                issuer = iss
            )
        )
    }

    private fun resolveKey(kid: String): PublicKey? {
        cachedKeys[kid]?.let { return it }
        if (kid.isBlank() && cachedKeys.size == 1) return cachedKeys.values.first()
        val refreshed = fetchJwksKeys()
        if (refreshed.isNotEmpty()) {
            cachedKeys = refreshed
        }
        return cachedKeys[kid] ?: if (kid.isBlank() && cachedKeys.size == 1) cachedKeys.values.first() else null
    }

    private fun fetchJwksKeys(): Map<String, PublicKey> = runCatching {
        val req = Request.Builder().url(jwksEndpoint).header("Accept", "application/json").build()
        httpClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyMap()
            val root = JSONObject(res.body.string())
            val keys = root.optJSONArray("keys") ?: return emptyMap()
            val map = mutableMapOf<String, PublicKey>()
            for (i in 0 until keys.length()) {
                val jwk = keys.optJSONObject(i) ?: continue
                if (jwk.optString("kty") != "RSA") continue
                val n = jwk.optString("n")
                val e = jwk.optString("e")
                if (n.isBlank() || e.isBlank()) continue
                val modulus = BigInteger(1, decodeBase64Url(n))
                val exponent = BigInteger(1, decodeBase64Url(e))
                val pubKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
                val keyId = jwk.optString("kid").ifBlank { "default-$i" }
                map[keyId] = pubKey
            }
            map
        }
    }.getOrDefault(emptyMap())

    private fun matchesAudience(payload: JSONObject, expectedAudience: String): Boolean {
        val audArr = payload.optJSONArray("aud")
        if (audArr != null) {
            for (i in 0 until audArr.length()) {
                if (audArr.optString(i) == expectedAudience) return true
            }
            return false
        }
        return payload.optString("aud") == expectedAudience
    }

    private fun decodeBase64Url(input: String): ByteArray =
        Base64.getUrlDecoder().decode(input)

    companion object {
        private const val CLOCK_SKEW_SECONDS = 60L
    }
}
