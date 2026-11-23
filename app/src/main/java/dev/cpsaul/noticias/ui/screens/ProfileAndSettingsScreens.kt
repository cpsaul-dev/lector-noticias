package dev.cpsaul.noticias.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import dev.cpsaul.noticias.BuildConfig
import dev.cpsaul.noticias.R
import dev.cpsaul.noticias.data.local.AiSourceMode
import dev.cpsaul.noticias.data.local.FactCheckConfig
import dev.cpsaul.noticias.data.local.FeedEntity
import dev.cpsaul.noticias.data.local.ThemeMode
import dev.cpsaul.noticias.data.local.UserProfile
import dev.cpsaul.noticias.ui.components.Avatar
import dev.cpsaul.noticias.ui.components.SettingsDivider
import dev.cpsaul.noticias.ui.components.SettingsGroup
import dev.cpsaul.noticias.ui.components.SettingsIconTile
import dev.cpsaul.noticias.ui.components.SettingsRow
import dev.cpsaul.noticias.ui.components.SettingsSectionLabel
import dev.cpsaul.noticias.ui.components.SubScreenTopAppBar
import dev.cpsaul.noticias.ui.viewmodel.NewsUiState

/**
 * §4.1 Pantalla de Perfil con avatar editable (`PickVisualMedia`), nombre editable y entrada a Ajustes.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetName: (String) -> Unit,
    onSetPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSetPhoto(uri)
    }

    Scaffold(
        topBar = {
            SubScreenTopAppBar(
                title = stringResource(R.string.nav_profile),
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            if (profile.photoBytes != null) {
                                showPhotoDialog = true
                            } else {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        }
                ) {
                    Avatar(photo = profile.photoBytes, name = profile.displayName, size = 96.dp)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(30.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = stringResource(R.string.profile_photo_dialog_title),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = profile.displayName.ifBlank { stringResource(R.string.profile_default_title) },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Normal)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Person,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.profile_name_row),
                        value = profile.displayName.ifBlank { stringResource(R.string.profile_name_optional) },
                        showChevron = true,
                        onClick = { showNameDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Settings,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.nav_settings),
                        showChevron = true,
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        var draft by remember(profile.displayName) { mutableStateOf(profile.displayName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.profile_name_row)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 40) draft = it },
                    label = { Text(stringResource(R.string.profile_name_prompt)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSetName(draft)
                    showNameDialog = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text(stringResource(R.string.profile_photo_dialog_title)) },
            text = { Text(stringResource(R.string.profile_photo_dialog_body)) },
            confirmButton = {
                Button(onClick = {
                    showPhotoDialog = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(stringResource(R.string.profile_photo_change))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoDialog = false
                    onRemovePhoto()
                }) {
                    Text(stringResource(R.string.profile_photo_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

/**
 * §3 Pantalla de Ajustes según ESTANDAR_DISENO.md:
 * Cuenta/ChatGPT OAuth → Fuentes/OPML → IA → Verificación → Apariencia (Tema + Idioma) → Privacidad → Datos → Acerca de.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    uiState: NewsUiState,
    onBack: () -> Unit,
    onOpenFeedsManage: () -> Unit,
    onOpenAbout: () -> Unit,
    onStartChatGptSignIn: () -> Unit,
    onSignOutChatGpt: () -> Unit,
    onSelectAiSource: (AiSourceMode) -> Unit,
    onUpdateFactCheckConfig: ((FactCheckConfig) -> FactCheckConfig) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSetSaveErrorReports: (Boolean) -> Unit,
    onClearErrorReports: () -> Unit,
    onClearCaches: () -> Unit,
    onSyncNow: () -> Unit
) {
    var showAiSourceDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFactCheckDialog by remember { mutableStateOf(false) }

    val currentLocaleTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val languageValueLabel = when {
        currentLocaleTag.startsWith("es") -> "Español"
        currentLocaleTag.startsWith("en") -> "English"
        else -> stringResource(R.string.settings_language_system)
    }

    val aiSourceLabel = when (uiState.settings.aiSourceMode) {
        AiSourceMode.AIHUB -> stringResource(R.string.settings_ai_source_nucleo)
        AiSourceMode.CHATGPT_OAUTH -> stringResource(R.string.settings_ai_source_chatgpt)
        AiSourceMode.DISABLED -> stringResource(R.string.settings_ai_source_disabled)
    }

    val reportsValue = if (uiState.savedErrorReportsCount == 0) {
        stringResource(R.string.reports_none)
    } else {
        pluralStringResource(R.plurals.reports_count, uiState.savedErrorReportsCount, uiState.savedErrorReportsCount)
    }

    val cacheMb = String.format("%.1f MB", uiState.cacheSizeBytes / (1024.0 * 1024.0))

    Scaffold(
        topBar = {
            SubScreenTopAppBar(
                title = stringResource(R.string.nav_settings),
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. CUENTA Y CONEXIONES (Sign in with ChatGPT Open Source OAuth)
            item {
                SettingsSectionLabel(stringResource(R.string.settings_section_account))
                SettingsGroup {
                    val session = uiState.chatGptSession
                    SettingsRow(
                        icon = Icons.Default.AccountCircle,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.settings_chatgpt_title),
                        subtitle = session?.email ?: stringResource(R.string.settings_chatgpt_sub),
                        value = if (session != null) {
                            stringResource(R.string.settings_chatgpt_connected)
                        } else {
                            stringResource(R.string.settings_chatgpt_disconnected)
                        },
                        trailing = {
                            when {
                                uiState.isAuthInProgress -> LoadingIndicator(modifier = Modifier.size(24.dp))
                                session != null -> TextButton(onClick = onSignOutChatGpt) {
                                    Text(stringResource(R.string.settings_chatgpt_sign_out), color = MaterialTheme.colorScheme.error)
                                }
                                else -> TextButton(onClick = onStartChatGptSignIn) {
                                    Text(stringResource(R.string.action_add))
                                }
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.RssFeed,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.feed_manage_title),
                        value = "${uiState.feeds.size}",
                        showChevron = true,
                        onClick = onOpenFeedsManage
                    )
                }
            }

            // 2. INTELIGENCIA ARTIFICIAL
            item {
                SettingsSectionLabel(stringResource(R.string.settings_section_ai))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.AutoAwesome,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.settings_ai_source),
                        value = aiSourceLabel,
                        showChevron = true,
                        onClick = { showAiSourceDialog = true }
                    )
                }
            }

            // 3. VERIFICACIÓN DE NOTICIAS (ClaimReview / Data Commons + Full Fact + Google Fact Check)
            item {
                val fc = uiState.settings.factCheckConfig
                SettingsSectionLabel(stringResource(R.string.settings_section_factcheck))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.FactCheck,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.settings_fc_offline),
                        subtitle = stringResource(R.string.settings_fc_offline_sub),
                        trailing = {
                            Switch(
                                checked = fc.offlineCatalogEnabled,
                                onCheckedChange = { checked -> onUpdateFactCheckConfig { it.copy(offlineCatalogEnabled = checked) } }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.FactCheck,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.settings_fc_fullfact),
                        subtitle = stringResource(R.string.settings_fc_fullfact_sub),
                        trailing = {
                            Switch(
                                checked = fc.fullFactEnabled,
                                onCheckedChange = { checked -> onUpdateFactCheckConfig { it.copy(fullFactEnabled = checked) } }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.FactCheck,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = stringResource(R.string.settings_fc_google),
                        subtitle = stringResource(R.string.settings_fc_google_sub),
                        trailing = {
                            Switch(
                                checked = fc.googleFactCheckEnabled,
                                onCheckedChange = { checked -> onUpdateFactCheckConfig { it.copy(googleFactCheckEnabled = checked) } }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Tune,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.settings_fc_config_dialog),
                        showChevron = true,
                        onClick = { showFactCheckDialog = true }
                    )
                }
            }

            // 4. APARIENCIA (Tema segmentado + Idioma ES/EN)
            item {
                SettingsSectionLabel(stringResource(R.string.settings_section_appearance))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.settings_theme),
                        trailing = {
                            SingleChoiceSegmentedButtonRow {
                                val options = listOf(
                                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                                    ThemeMode.LIGHT to R.string.settings_theme_light,
                                    ThemeMode.DARK to R.string.settings_theme_dark
                                )
                                options.forEachIndexed { idx, (mode, labelRes) ->
                                    SegmentedButton(
                                        selected = uiState.settings.themeMode == mode,
                                        onClick = { onSelectTheme(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                                        icon = {}
                                    ) {
                                        Text(stringResource(labelRes), maxLines = 1)
                                    }
                                }
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Language,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.settings_language),
                        value = languageValueLabel,
                        showChevron = true,
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            // 5. PRIVACIDAD (Fila Nube de solo lectura + Informes de errores)
            item {
                SettingsSectionLabel(stringResource(R.string.settings_section_privacy))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Cloud,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.settings_ai_cloud_row),
                        subtitle = stringResource(R.string.settings_ai_cloud_configure),
                        value = if (uiState.cloudAllowedInAiHub) {
                            stringResource(R.string.settings_ai_cloud_allowed)
                        } else {
                            stringResource(R.string.settings_ai_cloud_disallowed)
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.BugReport,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.settings_error_reports),
                        subtitle = stringResource(R.string.settings_error_reports_sub),
                        trailing = {
                            Switch(
                                checked = uiState.settings.saveErrorReports,
                                onCheckedChange = onSetSaveErrorReports
                            )
                        }
                    )
                }
            }

            // 6. DATOS
            item {
                SettingsSectionLabel(stringResource(R.string.settings_section_data))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Sync,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.action_sync_now),
                        trailing = {
                            TextButton(onClick = onSyncNow) {
                                Text(stringResource(R.string.action_sync_now))
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Storage,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.settings_cache_title),
                        subtitle = "${stringResource(R.string.settings_cache_sub)} · $cacheMb",
                        trailing = {
                            TextButton(onClick = onClearCaches) {
                                Text(stringResource(R.string.settings_clear_action))
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Folder,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.settings_saved_reports),
                        subtitle = reportsValue,
                        trailing = {
                            TextButton(onClick = onClearErrorReports) {
                                Text(stringResource(R.string.settings_clear_action))
                            }
                        }
                    )
                }
            }

            // 7. ACERCA DE (siempre última §3.2 y §3.6)
            item {
                SettingsSectionLabel(stringResource(R.string.settings_section_about))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.about_subtitle),
                        showChevron = true,
                        onClick = onOpenAbout
                    )
                }
            }
        }
    }

    if (showAiSourceDialog) {
        val options = listOf(
            AiSourceMode.AIHUB to stringResource(R.string.settings_ai_source_nucleo),
            AiSourceMode.CHATGPT_OAUTH to stringResource(R.string.settings_ai_source_chatgpt),
            AiSourceMode.DISABLED to stringResource(R.string.settings_ai_source_disabled)
        )
        AlertDialog(
            onDismissRequest = { showAiSourceDialog = false },
            title = { Text(stringResource(R.string.settings_ai_source)) },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectAiSource(mode)
                                    showAiSourceDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (uiState.settings.aiSourceMode == mode) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiSourceDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showLanguageDialog) {
        val options = listOf(
            null to stringResource(R.string.settings_language_system),
            "es" to "Español",
            "en" to "English"
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    options.forEach { (tag, label) ->
                        val isSelected = when (tag) {
                            null -> currentLocaleTag.isBlank()
                            else -> currentLocaleTag.startsWith(tag)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val locales = if (tag == null) {
                                        LocaleListCompat.getEmptyLocaleList()
                                    } else {
                                        LocaleListCompat.forLanguageTags(tag)
                                    }
                                    AppCompatDelegate.setApplicationLocales(locales)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showFactCheckDialog) {
        val current = uiState.settings.factCheckConfig
        var ffEndpoint by remember(current) { mutableStateOf(current.fullFactEndpoint) }
        var ffToken by remember(current) { mutableStateOf(current.fullFactToken) }
        var googleKey by remember(current) { mutableStateOf(current.googleFactCheckApiKey) }
        var dumpUrl by remember(current) { mutableStateOf(current.claimReviewDumpUrl) }

        AlertDialog(
            onDismissRequest = { showFactCheckDialog = false },
            title = { Text(stringResource(R.string.settings_fc_config_dialog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ffEndpoint,
                        onValueChange = { ffEndpoint = it },
                        label = { Text(stringResource(R.string.settings_fc_fullfact_endpoint)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ffToken,
                        onValueChange = { ffToken = it },
                        label = { Text(stringResource(R.string.settings_fc_fullfact_token)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = googleKey,
                        onValueChange = { googleKey = it },
                        label = { Text(stringResource(R.string.settings_fc_google_key)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dumpUrl,
                        onValueChange = { dumpUrl = it },
                        label = { Text(stringResource(R.string.settings_fc_dump_url)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateFactCheckConfig {
                        it.copy(
                            fullFactEndpoint = ffEndpoint.trim(),
                            fullFactToken = ffToken.trim(),
                            googleFactCheckApiKey = googleKey.trim(),
                            claimReviewDumpUrl = dumpUrl.trim()
                        )
                    }
                    showFactCheckDialog = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactCheckDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun FeedsManageScreen(
    feeds: List<FeedEntity>,
    onBack: () -> Unit,
    onDeleteFeed: (String) -> Unit,
    onImportOpml: (String) -> Unit,
    onExportOpml: () -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SubScreenTopAppBar(
                title = stringResource(R.string.feed_manage_title),
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showImportDialog = true }) {
                        Text(stringResource(R.string.feed_import_opml))
                    }
                    OutlinedButton(onClick = onExportOpml) {
                        Text(stringResource(R.string.feed_export_opml))
                    }
                }
            }
            items(feeds, key = { it.id }) { feed ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(feed.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${feed.category} · ${feed.url}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onDeleteFeed(feed.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.feed_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var opmlText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.feed_import_opml)) },
            text = {
                OutlinedTextField(
                    value = opmlText,
                    onValueChange = { opmlText = it },
                    minLines = 5,
                    maxLines = 8,
                    label = { Text("OPML XML") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportOpml(opmlText)
                        showImportDialog = false
                    },
                    enabled = opmlText.contains("<outline", ignoreCase = true)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * §3.6 Subpantalla Acerca de (cabecera, descripción, ACERCA DE, REFERENCIAS y COMPONENTES DE TERCEROS).
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            SubScreenTopAppBar(
                title = stringResource(R.string.settings_section_about),
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconTile(
                            icon = Icons.Default.Info,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 44.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            item {
                SettingsSectionLabel(stringResource(R.string.settings_section_about))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.about_version),
                        value = BuildConfig.VERSION_NAME
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.about_platform),
                        value = stringResource(R.string.about_platform_android)
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.about_license),
                        subtitle = stringResource(R.string.settings_license_value)
                    )
                }
            }

            item {
                SettingsSectionLabel(stringResource(R.string.about_section_references))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.FactCheck,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "BABE: Bias Annotations By Experts",
                        subtitle = "Spinde et al., EMNLP 2021"
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.FactCheck,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Full Fact & ClaimReview / Data Commons",
                        subtitle = "Schema.org Open Fact-Checking Standards"
                    )
                }
            }

            item {
                SettingsSectionLabel(stringResource(R.string.about_section_third_party))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Build,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "AndroidX (Compose, Material 3, Room, WorkManager)",
                        subtitle = "Apache 2.0"
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Build,
                        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "OkHttp & Jsoup",
                        subtitle = "Apache 2.0 / MIT"
                    )
                }
            }
        }
    }
}
