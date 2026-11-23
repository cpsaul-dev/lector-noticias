package dev.cpsaul.noticias.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ChatGptOAuthSession(
    val email: String?,
    val subject: String?,
    val issuedClientId: String,
    val extAgentHostId: String,
    val idToken: String?,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
    val scopes: Set<String>
) {
    val hasPlanUsagePermission: Boolean
        get() = ChatGptOAuthConfig.SCOPE_DIRECT_TOKENS in scopes

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis >= (expiresAtMillis - 60_000L)
}

interface ChatGptSessionStore {
    fun loadSession(): ChatGptOAuthSession?
    fun saveSession(session: ChatGptOAuthSession)
    fun clearSession()
    fun getOrCreateHostId(): String
}

object ChatGptOAuthConfig {
    const val EXPECTED_ISSUER = "https://auth.openai.com"
    const val AUTHORIZE_ENDPOINT = "https://auth.openai.com/api/accounts/authorize"
    const val TOKEN_ENDPOINT = "https://auth.openai.com/api/accounts/oauth/token"
    const val JWKS_ENDPOINT = "https://auth.openai.com/.well-known/jwks.json"
    const val RESOURCE_API_V1 = "https://api.openai.com/v1"
    const val DYNAMIC_CLIENT_ID = "dynamic_agent_client"
    const val AGENT_NAME_HINT = "Pulso"
    const val CALLBACK_PATH = "/auth/callback"
    const val SCOPE_DIRECT_TOKENS = "chatgpt.tokens.use.direct"
    val DEFAULT_SCOPES = listOf(
        "openid",
        "profile",
        "email",
        "offline_access",
        "resource.invoke",
        SCOPE_DIRECT_TOKENS
    )
}

object PkceHelper {
    private val secureRandom = SecureRandom()

    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    fun generateChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64UrlNoPadding(digest)
    }

    fun generateRandomToken(byteCount: Int = 24): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    internal fun base64UrlNoPadding(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Almacenamiento cifrado con Android Keystore (AES-256-GCM) para la sesión OAuth de ChatGPT.
 */
class AndroidKeystoreChatGptSessionStore(context: Context) : ChatGptSessionStore {
    private val appContext = context.applicationContext
    private val sessionFile = File(appContext.filesDir, "chatgpt_siwc_session.enc")
    private val hostPrefs = appContext.getSharedPreferences("chatgpt_siwc_host", Context.MODE_PRIVATE)

    override fun getOrCreateHostId(): String {
        val existing = hostPrefs.getString(KEY_HOST_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = "urn:uuid:${UUID.randomUUID()}"
        hostPrefs.edit().putString(KEY_HOST_ID, created).apply()
        return created
    }

    override fun loadSession(): ChatGptOAuthSession? {
        if (!sessionFile.exists()) return null
        return runCatching {
            val encrypted = sessionFile.readBytes()
            if (encrypted.size < 13) return null
            val iv = encrypted.copyOfRange(0, 12)
            val cipherText = encrypted.copyOfRange(12, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(cipherText).toString(Charsets.UTF_8)
            decodeSessionJson(plain)
        }.getOrNull()
    }

    override fun saveSession(session: ChatGptOAuthSession) {
        val plain = encodeSessionJson(session).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        val tmp = File(sessionFile.parentFile, "${sessionFile.name}.tmp")
        tmp.writeBytes(iv + cipherText)
        tmp.renameTo(sessionFile)
    }

    override fun clearSession() {
        if (sessionFile.exists()) sessionFile.delete()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pulso_chatgpt_siwc_aes"
        private const val KEY_HOST_ID = "ext_agent_host_id"

        internal fun encodeSessionJson(session: ChatGptOAuthSession): String {
            val scopesArr = JSONArray().apply { session.scopes.forEach { put(it) } }
            return JSONObject()
                .put("email", session.email)
                .put("subject", session.subject)
                .put("client_id", session.issuedClientId)
                .put("ext_agent_host_id", session.extAgentHostId)
                .put("id_token", session.idToken)
                .put("access_token", session.accessToken)
                .put("refresh_token", session.refreshToken)
                .put("expires_at", session.expiresAtMillis)
                .put("scopes", scopesArr)
                .toString()
        }

        internal fun decodeSessionJson(json: String): ChatGptOAuthSession {
            val obj = JSONObject(json)
            val scopesArr = obj.optJSONArray("scopes")
            val scopes = mutableSetOf<String>()
            if (scopesArr != null) {
                for (i in 0 until scopesArr.length()) {
                    val s = scopesArr.optString(i)
                    if (s.isNotBlank()) scopes += s
                }
            }
            return ChatGptOAuthSession(
                email = obj.optString("email").takeIf { it.isNotBlank() },
                subject = obj.optString("subject").takeIf { it.isNotBlank() },
                issuedClientId = obj.getString("client_id"),
                extAgentHostId = obj.getString("ext_agent_host_id"),
                idToken = obj.optString("id_token").takeIf { it.isNotBlank() },
                accessToken = obj.getString("access_token"),
                refreshToken = obj.optString("refresh_token").takeIf { it.isNotBlank() },
                expiresAtMillis = obj.optLong("expires_at", 0L),
                scopes = scopes
            )
        }
    }
}

sealed interface ChatGptAuthResult {
    data class Success(val session: ChatGptOAuthSession) : ChatGptAuthResult
    data class Error(val message: String) : ChatGptAuthResult
}

/**
 * Implementación del flujo oficial Sign in with ChatGPT (Open Source Token Sharing)
 * mediante OAuth 2.0 Authorization Code + PKCE S256 y loopback en 127.0.0.1.
 */
class ChatGptOAuthManager(
    private val store: ChatGptSessionStore,
    private val httpClient: OkHttpClient = defaultOAuthHttpClient(),
    private val authorizeEndpoint: String = ChatGptOAuthConfig.AUTHORIZE_ENDPOINT,
    private val tokenEndpoint: String = ChatGptOAuthConfig.TOKEN_ENDPOINT,
    private val idTokenVerifier: JwksIdTokenVerifier? = JwksIdTokenVerifier(httpClient)
) {
    private val _session = MutableStateFlow(store.loadSession())
    val session: StateFlow<ChatGptOAuthSession?> = _session.asStateFlow()

    fun buildAuthorizationUrl(
        redirectUri: String,
        state: String,
        nonce: String,
        codeChallenge: String,
        existingSession: ChatGptOAuthSession? = _session.value
    ): String {
        val hostId = store.getOrCreateHostId()
        val returningSession = existingSession?.takeIf { it.issuedClientId.isNotBlank() }
        val params = linkedMapOf(
            "client_id" to (returningSession?.issuedClientId ?: ChatGptOAuthConfig.DYNAMIC_CLIENT_ID),
            "ext_agent_host_id" to hostId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "scope" to ChatGptOAuthConfig.DEFAULT_SCOPES.joinToString(" "),
            "resource" to ChatGptOAuthConfig.RESOURCE_API_V1,
            "state" to state,
            "nonce" to nonce,
            "code_challenge_method" to "S256",
            "code_challenge" to codeChallenge
        )
        if (returningSession == null) {
            params["agent_name_hint"] = ChatGptOAuthConfig.AGENT_NAME_HINT
        } else {
            returningSession.idToken?.let { params["id_token_hint"] = it }
            returningSession.email?.let { params["login_hint"] = it }
        }
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$authorizeEndpoint?$query"
    }

    /**
     * Inicia un listener loopback en 127.0.0.1, invoca `onLaunchBrowser(authUrl)` y completa el intercambio PKCE.
     */
    suspend fun signInWithLoopback(
        timeoutMillis: Int = 180_000,
        onLaunchBrowser: (String) -> Unit
    ): ChatGptAuthResult = withContext(Dispatchers.IO) {
        val verifier = PkceHelper.generateVerifier()
        val challenge = PkceHelper.generateChallengeS256(verifier)
        val state = PkceHelper.generateRandomToken(24)
        val nonce = PkceHelper.generateRandomToken(24)
        val existing = _session.value

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = timeoutMillis
            val redirectUri = "http://127.0.0.1:${server.localPort}${ChatGptOAuthConfig.CALLBACK_PATH}"
            val authUrl = buildAuthorizationUrl(redirectUri, state, nonce, challenge, existing)
            onLaunchBrowser(authUrl)

            val socket = try {
                server.accept()
            } catch (e: Exception) {
                return@withContext ChatGptAuthResult.Error("Tiempo de espera agotado al iniciar sesión con ChatGPT")
            }

            val queryParams = socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val requestLine = reader.readLine().orEmpty()
                val htmlResponse = """
                    <html><body style="font-family:sans-serif;text-align:center;padding:40px">
                    <h2>Pulso</h2><p>Autenticación recibida. Puedes volver a la aplicación.</p>
                    </body></html>
                """.trimIndent()
                val httpResp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${htmlResponse.toByteArray().size}\r\nConnection: close\r\n\r\n$htmlResponse"
                client.getOutputStream().write(httpResp.toByteArray(Charsets.UTF_8))
                client.getOutputStream().flush()
                parseQueryFromHttpRequestLine(requestLine)
            }

            if (queryParams["state"] != state) {
                return@withContext ChatGptAuthResult.Error("Estado OAuth inválido (posible CSRF)")
            }
            val oauthError = queryParams["error"]
            if (!oauthError.isNullOrBlank()) {
                return@withContext ChatGptAuthResult.Error("Acceso denegado: $oauthError")
            }
            val code = queryParams["code"]
                ?: return@withContext ChatGptAuthResult.Error("No se recibió código de autorización")

            val returnedClientId = queryParams["client_id"]?.takeIf { it.isNotBlank() }
            if (existing != null && returnedClientId != null && returnedClientId != existing.issuedClientId) {
                return@withContext ChatGptAuthResult.Error("El client_id recibido no coincide con el registro de la cuenta")
            }
            val issuedClientId = returnedClientId
                ?: existing?.issuedClientId
                ?: return@withContext ChatGptAuthResult.Error("Registro incompleto: falta client_id emitido")

            exchangeAuthorizationCode(
                code = code,
                issuedClientId = issuedClientId,
                codeVerifier = verifier,
                redirectUri = redirectUri,
                expectedNonce = nonce,
                expectedSubject = existing?.subject
            )
        }
    }

    suspend fun exchangeAuthorizationCode(
        code: String,
        issuedClientId: String,
        codeVerifier: String,
        redirectUri: String,
        expectedNonce: String? = null,
        expectedSubject: String? = null
    ): ChatGptAuthResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", issuedClientId)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .add("resource", ChatGptOAuthConfig.RESOURCE_API_V1)
            .build()

        val req = Request.Builder()
            .url(tokenEndpoint)
            .post(form)
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(req).execute().use { res ->
                val body = res.body.string()
                if (!res.isSuccessful) {
                    return@withContext ChatGptAuthResult.Error("Error al canjear token (HTTP ${res.code})")
                }
                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                if (accessToken.isBlank()) {
                    return@withContext ChatGptAuthResult.Error("Respuesta OAuth sin access_token")
                }
                val idToken = json.optString("id_token").takeIf { it.isNotBlank() }
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                val expiresIn = json.optLong("expires_in", 3600L)
                val grantedScopes = json.optString("scope")
                    .split(" ")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()

                val validated = if (idToken != null && idTokenVerifier != null) {
                    when (val check = idTokenVerifier.verify(idToken, issuedClientId, expectedNonce)) {
                        is IdTokenValidationResult.Valid -> check.token
                        is IdTokenValidationResult.Invalid -> {
                            return@withContext ChatGptAuthResult.Error("ID token rechazado: ${check.reason}")
                        }
                    }
                } else {
                    val claims = idToken?.let { decodeJwtClaimsUnverified(it) }
                    claims?.let {
                        ValidatedIdToken(
                            subject = it.optString("sub"),
                            email = it.optString("email").takeIf { e -> e.isNotBlank() },
                            issuer = it.optString("iss")
                        )
                    }
                }

                if (!expectedSubject.isNullOrBlank() && validated != null && validated.subject != expectedSubject) {
                    return@withContext ChatGptAuthResult.Error("La identidad verificada no coincide con la cuenta seleccionada")
                }

                val session = ChatGptOAuthSession(
                    email = validated?.email,
                    subject = validated?.subject?.takeIf { it.isNotBlank() },
                    issuedClientId = issuedClientId,
                    extAgentHostId = store.getOrCreateHostId(),
                    idToken = idToken,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L,
                    scopes = grantedScopes
                )
                store.saveSession(session)
                _session.value = session
                ChatGptAuthResult.Success(session)
            }
        } catch (e: Exception) {
            ChatGptAuthResult.Error("Error de red OAuth: ${e.message ?: e::class.java.simpleName}")
        }
    }

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val current = _session.value ?: return@withContext null
        if (!current.isExpired()) return@withContext current.accessToken
        val refresh = current.refreshToken ?: return@withContext null

        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", current.issuedClientId)
            .add("refresh_token", refresh)
            .add("resource", ChatGptOAuthConfig.RESOURCE_API_V1)
            .build()

        val req = Request.Builder().url(tokenEndpoint).post(form).build()
        runCatching {
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val json = JSONObject(res.body.string())
                val newAccess = json.optString("access_token").takeIf { it.isNotBlank() } ?: return@use null
                val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: current.refreshToken
                val expiresIn = json.optLong("expires_in", 3600L)
                val newScopes = json.optString("scope")
                    .split(" ")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                    .ifEmpty { current.scopes }
                val updated = current.copy(
                    accessToken = newAccess,
                    refreshToken = newRefresh,
                    expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L,
                    scopes = newScopes
                )
                store.saveSession(updated)
                _session.value = updated
                newAccess
            }
        }.getOrNull()
    }

    fun signOut() {
        store.clearSession()
        _session.value = null
    }

    companion object {
        internal fun parseQueryFromHttpRequestLine(requestLine: String): Map<String, String> {
            val parts = requestLine.split(" ")
            if (parts.size < 2) return emptyMap()
            val pathAndQuery = parts[1]
            val idx = pathAndQuery.indexOf('?')
            if (idx < 0 || idx + 1 >= pathAndQuery.length) return emptyMap()
            return pathAndQuery.substring(idx + 1)
                .split("&")
                .mapNotNull { pair ->
                    val eq = pair.indexOf('=')
                    if (eq <= 0) null
                    else URLDecoder.decode(pair.substring(0, eq), "UTF-8") to
                        URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                }
                .toMap()
        }

        internal fun decodeJwtClaimsUnverified(jwt: String): JSONObject? = runCatching {
            val segments = jwt.split(".")
            if (segments.size < 2) return null
            val decoded = Base64.getUrlDecoder().decode(segments[1]).toString(Charsets.UTF_8)
            JSONObject(decoded)
        }.getOrNull()

        fun defaultOAuthHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
