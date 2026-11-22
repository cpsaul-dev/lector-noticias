package dev.cpsaul.noticias.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AiSourceMode {
    AIHUB,
    CHATGPT_OAUTH,
    DISABLED
}

data class FactCheckConfig(
    val offlineCatalogEnabled: Boolean = true,
    val fullFactEnabled: Boolean = false,
    val fullFactEndpoint: String = "https://fullfact.org/api/v1/claims/search",
    val fullFactToken: String = "",
    val googleFactCheckEnabled: Boolean = false,
    val googleFactCheckApiKey: String = "",
    val claimReviewDumpUrl: String = ""
)

data class UserProfile(
    val displayName: String = "",
    val photoBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserProfile) return false
        return displayName == other.displayName && photoBytes.contentEquals(other.photoBytes)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val aiSourceMode: AiSourceMode = AiSourceMode.AIHUB,
    val factCheckConfig: FactCheckConfig = FactCheckConfig(),
    val saveErrorReports: Boolean = true,
    val userProfile: UserProfile = UserProfile()
)

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val avatarFile = File(appContext.filesDir, AVATAR_FILE_NAME)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setAiSourceMode(mode: AiSourceMode) {
        prefs.edit().putString(KEY_AI_SOURCE, mode.name).apply()
        _state.value = _state.value.copy(aiSourceMode = mode)
    }

    fun setSaveErrorReports(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ERROR_REPORTS, enabled).apply()
        _state.value = _state.value.copy(saveErrorReports = enabled)
    }

    fun updateFactCheckConfig(transform: (FactCheckConfig) -> FactCheckConfig) {
        val updated = transform(_state.value.factCheckConfig)
        prefs.edit()
            .putBoolean(KEY_FC_OFFLINE, updated.offlineCatalogEnabled)
            .putBoolean(KEY_FC_FULLFACT_ENABLED, updated.fullFactEnabled)
            .putString(KEY_FC_FULLFACT_ENDPOINT, updated.fullFactEndpoint)
            .putString(KEY_FC_FULLFACT_TOKEN, updated.fullFactToken)
            .putBoolean(KEY_FC_GOOGLE_ENABLED, updated.googleFactCheckEnabled)
            .putString(KEY_FC_GOOGLE_KEY, updated.googleFactCheckApiKey)
            .putString(KEY_FC_DUMP_URL, updated.claimReviewDumpUrl)
            .apply()
        _state.value = _state.value.copy(factCheckConfig = updated)
    }

    fun setDisplayName(name: String) {
        val trimmed = name.trim().take(40)
        prefs.edit().putString(KEY_PROFILE_NAME, trimmed).apply()
        _state.value = _state.value.copy(
            userProfile = _state.value.userProfile.copy(displayName = trimmed)
        )
    }

    fun setProfilePhoto(bytes: ByteArray?) {
        if (bytes == null) {
            if (avatarFile.exists()) avatarFile.delete()
        } else {
            avatarFile.writeBytes(bytes)
        }
        _state.value = _state.value.copy(
            userProfile = _state.value.userProfile.copy(photoBytes = bytes)
        )
    }

    private fun loadState(): AppSettingsState {
        val theme = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val aiSource = prefs.getString(KEY_AI_SOURCE, AiSourceMode.AIHUB.name)
            ?.let { runCatching { AiSourceMode.valueOf(it) }.getOrNull() }
            ?: AiSourceMode.AIHUB
        val photo = if (avatarFile.exists()) runCatching { avatarFile.readBytes() }.getOrNull() else null
        return AppSettingsState(
            themeMode = theme,
            aiSourceMode = aiSource,
            factCheckConfig = FactCheckConfig(
                offlineCatalogEnabled = prefs.getBoolean(KEY_FC_OFFLINE, true),
                fullFactEnabled = prefs.getBoolean(KEY_FC_FULLFACT_ENABLED, false),
                fullFactEndpoint = prefs.getString(KEY_FC_FULLFACT_ENDPOINT, "https://fullfact.org/api/v1/claims/search").orEmpty(),
                fullFactToken = prefs.getString(KEY_FC_FULLFACT_TOKEN, "").orEmpty(),
                googleFactCheckEnabled = prefs.getBoolean(KEY_FC_GOOGLE_ENABLED, false),
                googleFactCheckApiKey = prefs.getString(KEY_FC_GOOGLE_KEY, "").orEmpty(),
                claimReviewDumpUrl = prefs.getString(KEY_FC_DUMP_URL, "").orEmpty()
            ),
            saveErrorReports = prefs.getBoolean(KEY_ERROR_REPORTS, true),
            userProfile = UserProfile(
                displayName = prefs.getString(KEY_PROFILE_NAME, "").orEmpty(),
                photoBytes = photo
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "pulso_app_prefs"
        private const val AVATAR_FILE_NAME = "profile_avatar.jpg"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AI_SOURCE = "ai_source_mode"
        private const val KEY_ERROR_REPORTS = "save_error_reports"
        private const val KEY_PROFILE_NAME = "profile_display_name"
        private const val KEY_FC_OFFLINE = "fc_offline_enabled"
        private const val KEY_FC_FULLFACT_ENABLED = "fc_fullfact_enabled"
        private const val KEY_FC_FULLFACT_ENDPOINT = "fc_fullfact_endpoint"
        private const val KEY_FC_FULLFACT_TOKEN = "fc_fullfact_token"
        private const val KEY_FC_GOOGLE_ENABLED = "fc_google_enabled"
        private const val KEY_FC_GOOGLE_KEY = "fc_google_key"
        private const val KEY_FC_DUMP_URL = "fc_dump_url"
    }
}
