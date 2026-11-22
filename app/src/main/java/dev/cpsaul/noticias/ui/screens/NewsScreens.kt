package dev.cpsaul.noticias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cpsaul.noticias.R
import dev.cpsaul.noticias.ai.ActionKind
import dev.cpsaul.noticias.ai.ExecutionTarget
import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.domain.engine.ClaimReviewEntry
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor
import dev.cpsaul.noticias.domain.engine.StoryCluster
import dev.cpsaul.noticias.ui.components.MarkdownArticleBody
import dev.cpsaul.noticias.ui.components.SubScreenTopAppBar
import dev.cpsaul.noticias.ui.viewmodel.ArticleAiState
import dev.cpsaul.noticias.ui.viewmodel.ArticleFactCheckState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BriefingScreen(
    articles: List<ArticleEntity>,
    isSyncing: Boolean,
    onSelectArticle: (ArticleEntity) -> Unit,
    onSyncNow: () -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.briefing_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.briefing_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isSyncing) {
                        LoadingIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        FilledTonalButton(onClick = onSyncNow) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_sync_now))
                        }
                    }
                }
            }
        }

        if (articles.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.briefing_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(articles, key = { it.id }) { article ->
                ArticleListCard(article = article, onClick = { onSelectArticle(article) })
            }
        }
    }
}

@Composable
fun FeedScreen(
    articles: List<ArticleEntity>,
    categories: List<String>,
    selectedCategory: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectArticle: (ArticleEntity) -> Unit,
    onAddFeed: (title: String, url: String, category: String) -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(stringResource(R.string.feed_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_add))
                }
            }
        }

        if (categories.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text(stringResource(R.string.feed_all_categories)) }
                        )
                    }
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onSelectCategory(if (selectedCategory == category) null else category) },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }

        items(articles, key = { it.id }) { article ->
            ArticleListCard(article = article, onClick = { onSelectArticle(article) })
        }
    }

    if (showAddDialog) {
        AddFeedDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url, cat ->
                onAddFeed(title, url, cat)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ClustersScreen(
    clusters: List<StoryCluster>,
    onSelectArticle: (ArticleEntity) -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.clusters_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(clusters, key = { it.clusterId }) { cluster ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = cluster.primaryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = pluralStringResource(R.plurals.articles_count, cluster.articles.size, cluster.articles.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    cluster.articles.forEach { article ->
                        AssistChip(
                            onClick = { onSelectArticle(article) },
                            label = {
                                Text(
                                    text = article.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedScreen(
    articles: List<ArticleEntity>,
    onSelectArticle: (ArticleEntity) -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (articles.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.briefing_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(articles, key = { it.id }) { article ->
                ArticleListCard(article = article, onClick = { onSelectArticle(article) })
            }
        }
    }
}

@Composable
private fun ArticleListCard(
    article: ArticleEntity,
    onClick: () -> Unit
) {
    // §5.B: Interfaz limpia sin semáforos invasivos de sesgo en el feed principal
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.SemiBold,
                color = if (article.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = article.summary ?: ReadabilityMarkdownExtractor.extractLeadParagraph(article.contentMarkdown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!article.author.isNullOrBlank()) {
                Text(
                    text = article.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleDetailScreen(
    article: ArticleEntity,
    timelineArticles: List<ArticleEntity>,
    aiState: ArticleAiState,
    factCheckState: ArticleFactCheckState,
    cloudAllowed: Boolean,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSummarize: () -> Unit,
    onRewriteHeadline: () -> Unit,
    onQuickAction: (ActionKind) -> Unit,
    onAnalyzeCoverage: () -> Unit,
    onStopAi: (String) -> Unit,
    onVerifyClaims: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSelectRelatedArticle: (ArticleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFactualHeadline by remember(article.id) { mutableStateOf(false) }
    val localeLang = LocalConfiguration.current.locales[0].language.ifBlank { "es" }
    val cancelledText = stringResource(R.string.ai_operation_cancelled)
    val effectiveRewrittenTitle = aiState.rewrittenTitle ?: article.rewrittenTitle

    LaunchedEffect(article.id) {
        if (!factCheckState.checkedOnce && !factCheckState.isChecking) {
            onVerifyClaims(localeLang)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showBackButton) {
                SubScreenTopAppBar(
                    title = stringResource(R.string.app_name),
                    onBack = onBack
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Titular original + toggle comparativo fáctico (§2 4.B)
                item {
                    val displayedTitle = if (showFactualHeadline && !effectiveRewrittenTitle.isNullOrBlank()) {
                        effectiveRewrittenTitle
                    } else {
                        article.title
                    }
                    Text(
                        text = displayedTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!effectiveRewrittenTitle.isNullOrBlank()) {
                            FilterChip(
                                selected = showFactualHeadline,
                                onClick = { showFactualHeadline = !showFactualHeadline },
                                label = { Text(stringResource(R.string.headline_toggle)) }
                            )
                        } else {
                            AssistChip(
                                onClick = onRewriteHeadline,
                                label = { Text(stringResource(R.string.headline_generate)) }
                            )
                        }
                    }
                }

                // Indicador de carga IA + botón Stop / Detener obligatorio (Issue #3 §7)
                if (aiState.isLoading) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LoadingIndicator(modifier = Modifier.size(28.dp))
                                    Text(
                                        text = stringResource(R.string.ai_generating),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                OutlinedButton(onClick = { onStopAi(cancelledText) }) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_stop_ai))
                                }
                            }
                        }
                    }
                }

                // Resumen ejecutivo + chip de origen EU AI Act (§8.3)
                item {
                    val summaryContent = aiState.summaryText
                        ?: article.summary
                        ?: ReadabilityMarkdownExtractor.extractLeadParagraph(article.contentMarkdown)
                    val originLabel = when (aiState.summaryOrigin) {
                        ExecutionTarget.LOCAL_ONNX_GEMMA -> stringResource(R.string.ai_origin_on_device)
                        ExecutionTarget.CLOUD_GATEWAY -> stringResource(R.string.ai_origin_cloud)
                        ExecutionTarget.CHATGPT_OAUTH -> stringResource(R.string.ai_origin_chatgpt)
                        ExecutionTarget.DETERMINISTIC_FALLBACK, null -> stringResource(R.string.summary_lead_fallback)
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.summary_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = originLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(text = summaryContent, style = MaterialTheme.typography.bodyMedium)
                            if (!aiState.statusMessage.isNullOrBlank()) {
                                Text(
                                    text = aiState.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Acciones rápidas estructuradas (§2 6.B)
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { onQuickAction(ActionKind.EXPLAIN_CONCEPT) },
                            label = { Text(stringResource(R.string.quick_action_explain)) }
                        )
                        AssistChip(
                            onClick = { onQuickAction(ActionKind.EXTRACTION_FACTS) },
                            label = { Text(stringResource(R.string.quick_action_facts)) }
                        )
                        AssistChip(
                            onClick = { onQuickAction(ActionKind.TRANSLATE) },
                            label = { Text(stringResource(R.string.quick_action_translate)) }
                        )
                    }
                    if (!aiState.quickActionText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = aiState.quickActionText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Tarjeta discreta de Verificaciones de Noticias (ClaimReview / Full Fact / Google Fact Check)
                item {
                    FactCheckSectionCard(
                        state = factCheckState,
                        onOpenSource = onOpenUrl
                    )
                }

                // Matriz de cobertura y pluralidad (solo si nube autorizada §2 8.A)
                if (cloudAllowed) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.coverage_matrix_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!aiState.coverageMatrixText.isNullOrBlank()) {
                                    Text(
                                        text = aiState.coverageMatrixText,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    FilledTonalButton(onClick = onAnalyzeCoverage) {
                                        Text(stringResource(R.string.coverage_matrix_analyze))
                                    }
                                }
                            }
                        }
                    }
                }

                // Cuerpo Markdown offline completo (§2 10.A)
                item {
                    MarkdownArticleBody(
                        markdown = article.contentMarkdown,
                        imageLocalPath = article.imageLocalPath
                    )
                }

                // Reconstrucción de cronología dinámica (§2 3.A)
                if (timelineArticles.size > 1) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.timeline_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                timelineArticles.forEach { step ->
                                    TextButton(
                                        onClick = { onSelectRelatedArticle(step) },
                                        enabled = step.id != article.id
                                    ) {
                                        Text(
                                            text = "• ${step.title}",
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // §6.2 Barra flotante Material 3 Expressive (`HorizontalFloatingToolbar`)
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                IconButton(onClick = onSummarize) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.summary_generate))
                }
                IconButton(onClick = { onVerifyClaims(localeLang) }) {
                    Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = stringResource(R.string.article_verify))
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(
                            if (article.isBookmarked) R.string.article_bookmarked else R.string.article_bookmark
                        )
                    )
                }
                if (article.url.startsWith("http")) {
                    IconButton(onClick = { onOpenUrl(article.url) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.article_open_original))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FactCheckSectionCard(
    state: ArticleFactCheckState,
    onOpenSource: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FactCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.factcheck_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when {
                state.isChecking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                    Text(
                        text = stringResource(R.string.factcheck_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.matches.isEmpty() -> Text(
                    text = stringResource(R.string.factcheck_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> state.matches.forEach { entry: ClaimReviewEntry ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${entry.ratingLabel} · ${entry.publisherName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = entry.claimText,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (entry.reviewUrl.startsWith("http")) {
                                TextButton(onClick = { onOpenSource(entry.reviewUrl) }) {
                                    Text(stringResource(R.string.factcheck_open_source))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFeedDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feed_name_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.feed_url_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.feed_category_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url, category) },
                enabled = url.trim().startsWith("http")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
