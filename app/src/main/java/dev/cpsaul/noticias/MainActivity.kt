package dev.cpsaul.noticias

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.domain.engine.DailyBriefingSelector
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor
import dev.cpsaul.noticias.domain.engine.VisualStoryClusterer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewsReaderApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsReaderApp(
    articles: List<ArticleEntity> = sampleArticles(),
    cloudAllowedInAiHub: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedArticle by remember { mutableStateOf<ArticleEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; selectedArticle = null },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_briefing)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; selectedArticle = null },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_clusters)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; selectedArticle = null },
                    icon = {},
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { padding ->
        val current = selectedArticle
        if (current != null) {
            ArticleDetailView(
                article = current,
                cloudAllowedInAiHub = cloudAllowedInAiHub,
                modifier = Modifier.padding(padding)
            )
        } else {
            when (selectedTab) {
                0 -> DailyBriefingView(
                    articles = DailyBriefingSelector.selectBriefing(articles),
                    onSelectArticle = { selectedArticle = it },
                    modifier = Modifier.padding(padding)
                )
                1 -> StoryClustersView(
                    articles = articles,
                    onSelectArticle = { selectedArticle = it },
                    modifier = Modifier.padding(padding)
                )
                else -> AiSettingsSectionView(
                    cloudAllowedInAiHub = cloudAllowedInAiHub,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun DailyBriefingView(
    articles: List<ArticleEntity>,
    onSelectArticle: (ArticleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = stringResource(R.string.briefing_title), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.briefing_subtitle), style = MaterialTheme.typography.bodyMedium)
        }
        items(articles, key = { it.id }) { article ->
            Card(onClick = { onSelectArticle(article) }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = article.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.summary ?: ReadabilityMarkdownExtractor.extractLeadParagraph(article.contentMarkdown),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun StoryClustersView(
    articles: List<ArticleEntity>,
    onSelectArticle: (ArticleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val clusters = remember(articles) { VisualStoryClusterer.cluster(articles) }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(clusters, key = { it.clusterId }) { cluster ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = cluster.primaryTitle, style = MaterialTheme.typography.titleMedium)
                    cluster.articles.forEach { article ->
                        AssistChip(
                            onClick = { onSelectArticle(article) },
                            label = { Text(article.title) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleDetailView(
    article: ArticleEntity,
    cloudAllowedInAiHub: Boolean,
    modifier: Modifier = Modifier
) {
    var showFactualHeadline by remember { mutableStateOf(false) }
    var quickActionOutput by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val displayedTitle = if (showFactualHeadline && !article.rewrittenTitle.isNullOrBlank()) {
                article.rewrittenTitle
            } else {
                article.title
            }
            Text(text = displayedTitle, style = MaterialTheme.typography.headlineSmall)
            if (!article.rewrittenTitle.isNullOrBlank()) {
                FilterChip(
                    selected = showFactualHeadline,
                    onClick = { showFactualHeadline = !showFactualHeadline },
                    label = { Text(stringResource(R.string.headline_toggle)) }
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.summary_title), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.summary ?: ReadabilityMarkdownExtractor.extractLeadParagraph(article.contentMarkdown),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { quickActionOutput = ReadabilityMarkdownExtractor.extractKeyFigures(article.contentMarkdown) },
                    label = { Text(stringResource(R.string.quick_action_facts)) }
                )
                AssistChip(
                    onClick = { quickActionOutput = ReadabilityMarkdownExtractor.extractLeadParagraph(article.contentMarkdown) },
                    label = { Text(stringResource(R.string.quick_action_explain)) }
                )
            }
            quickActionOutput?.let { out ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(text = out, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (cloudAllowedInAiHub) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(R.string.coverage_matrix_title), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            Text(text = article.contentMarkdown, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun AiSettingsSectionView(
    cloudAllowedInAiHub: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.settings_section_ai), style = MaterialTheme.typography.labelLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.settings_ai_source), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.settings_ai_source_nucleo), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.settings_ai_cloud_row) + ": " +
                        if (cloudAllowedInAiHub) stringResource(R.string.settings_ai_cloud_allowed)
                        else stringResource(R.string.settings_ai_cloud_disallowed),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(text = stringResource(R.string.settings_section_about), style = MaterialTheme.typography.labelLarge)
        Text(text = stringResource(R.string.settings_license_value), style = MaterialTheme.typography.bodySmall)
    }
}

private fun sampleArticles(): List<ArticleEntity> = listOf(
    ArticleEntity(
        id = "a1",
        feedId = "f1",
        timelineId = "t-energia",
        title = "La red europea alcanza un 62 % de generación renovable en el trimestre",
        rewrittenTitle = "La generación eléctrica renovable en la UE registró un 62 % en el trimestre",
        contentMarkdown = "La red eléctrica europea registró un 62 % de generación renovable durante el último trimestre según los datos consolidados de los operadores.\n\nEl incremento estuvo liderado por la energía solar y eólica con 140 millones de MWh.",
        url = "https://example.org/energia-1",
        publishedAt = 1_793_966_400_000L
    )
)
