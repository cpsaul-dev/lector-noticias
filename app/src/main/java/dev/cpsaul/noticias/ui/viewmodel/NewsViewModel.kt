package dev.cpsaul.noticias.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cpsaul.noticias.ai.AIResult
import dev.cpsaul.noticias.ai.ActionKind
import dev.cpsaul.noticias.ai.AndroidKeystoreChatGptSessionStore
import dev.cpsaul.noticias.ai.ChatGptAuthResult
import dev.cpsaul.noticias.ai.ChatGptOAuthManager
import dev.cpsaul.noticias.ai.ChatGptOAuthSession
import dev.cpsaul.noticias.ai.ChatGptResponsesConnector
import dev.cpsaul.noticias.ai.CompositeAiConnector
import dev.cpsaul.noticias.ai.DefaultAiHubConnector
import dev.cpsaul.noticias.ai.ExecutionTarget
import dev.cpsaul.noticias.data.factcheck.FactCheckRepository
import dev.cpsaul.noticias.data.local.AiSourceMode
import dev.cpsaul.noticias.data.local.AppPreferences
import dev.cpsaul.noticias.data.local.AppSettingsState
import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.data.local.FactCheckConfig
import dev.cpsaul.noticias.data.local.FeedEntity
import dev.cpsaul.noticias.data.local.ImageDiskCache
import dev.cpsaul.noticias.data.local.NewsDatabase
import dev.cpsaul.noticias.data.local.ThemeMode
import dev.cpsaul.noticias.data.remote.FeedNetworkFetcher
import dev.cpsaul.noticias.data.repository.NewsRepository
import dev.cpsaul.noticias.diagnostics.ErrorLog
import dev.cpsaul.noticias.domain.engine.ClaimReviewEntry
import dev.cpsaul.noticias.domain.engine.DailyBriefingSelector
import dev.cpsaul.noticias.domain.engine.StoryCluster
import dev.cpsaul.noticias.domain.engine.TimelineReconstructor
import dev.cpsaul.noticias.domain.engine.VisualStoryClusterer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class ArticleAiState(
    val isLoading: Boolean = false,
    val summaryText: String? = null,
    val summaryOrigin: ExecutionTarget? = null,
    val rewrittenTitle: String? = null,
    val quickActionText: String? = null,
    val quickActionOrigin: ExecutionTarget? = null,
    val coverageMatrixText: String? = null,
    val statusMessage: String? = null
)

data class ArticleFactCheckState(
    val isChecking: Boolean = false,
    val matches: List<ClaimReviewEntry> = emptyList(),
    val checkedOnce: Boolean = false
)

data class NewsUiState(
    val feeds: List<FeedEntity> = emptyList(),
    val articles: List<ArticleEntity> = emptyList(),
    val briefingArticles: List<ArticleEntity> = emptyList(),
    val bookmarkedArticles: List<ArticleEntity> = emptyList(),
    val clusters: List<StoryCluster> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val isSyncing: Boolean = false,
    val snackbarMessage: String? = null,
    val settings: AppSettingsState = AppSettingsState(),
    val cloudAllowedInAiHub: Boolean = false,
    val chatGptSession: ChatGptOAuthSession? = null,
    val isAuthInProgress: Boolean = false,
    val savedErrorReportsCount: Int = 0,
    val cacheSizeBytes: Long = 0L,
    val articleAiStates: Map<String, ArticleAiState> = emptyMap(),
    val articleFactCheckStates: Map<String, ArticleFactCheckState> = emptyMap()
) {
    val categories: List<String>
        get() = feeds.map { it.category }.distinct().sorted()

    fun filteredArticles(): List<ArticleEntity> {
        val feedCategoryMap = feeds.associate { it.id to it.category }
        val q = searchQuery.trim().lowercase()
        return articles.filter { article ->
            val matchesCat = selectedCategory == null || feedCategoryMap[article.feedId] == selectedCategory
            val matchesQuery = q.isBlank() ||
                article.title.lowercase().contains(q) ||
                article.contentMarkdown.lowercase().contains(q) ||
                (article.summary?.lowercase()?.contains(q) == true)
            matchesCat && matchesQuery
        }
    }

    fun timelineFor(article: ArticleEntity): List<ArticleEntity> {
        val tid = article.timelineId ?: return emptyList()
        return TimelineReconstructor.buildChronology(articles, tid)
    }
}

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = AppPreferences(appContext)
    private val database = NewsDatabase.getInstance(appContext)
    private val imageCache = ImageDiskCache(File(appContext.filesDir, "article_images"))
    private val chatGptStore = AndroidKeystoreChatGptSessionStore(appContext)
    val chatGptOAuthManager = ChatGptOAuthManager(chatGptStore)

    private val aiHubConnector = DefaultAiHubConnector.create(appContext)
    private val chatGptConnector = ChatGptResponsesConnector(chatGptOAuthManager)
    private val compositeAiConnector = CompositeAiConnector(
        aiHubConnector = aiHubConnector,
        chatGptConnector = chatGptConnector,
        activeSourceProvider = { preferences.state.value.aiSourceMode }
    )
    private val factCheckRepo = FactCheckRepository(database.newsDao())
    private val repository = NewsRepository(
        dao = database.newsDao(),
        fetcher = FeedNetworkFetcher(imageDiskCache = imageCache),
        imageDiskCache = imageCache,
        factCheckRepository = factCheckRepo,
        aiConnector = compositeAiConnector
    )

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var activeAiJob: Job? = null

    init {
        ErrorLog.isEnabled = preferences.state.value.saveErrorReports
        viewModelScope.launch {
            repository.ensureSeedData(appContext)
            refreshDiagnosticsAndCloudState()
        }
        viewModelScope.launch {
            combine(
                repository.observeFeeds(),
                repository.observeArticles(),
                repository.observeBookmarkedArticles(),
                preferences.state,
                chatGptOAuthManager.session
            ) { feeds, articles, bookmarked, settings, session ->
                Tuple5(feeds, articles, bookmarked, settings, session)
            }.collect { (feeds, articles, bookmarked, settings, session) ->
                val briefing = DailyBriefingSelector.selectBriefing(articles)
                val clusters = VisualStoryClusterer.cluster(articles)
                _uiState.update { state ->
                    state.copy(
                        feeds = feeds,
                        articles = articles,
                        briefingArticles = briefing,
                        bookmarkedArticles = bookmarked,
                        clusters = clusters,
                        settings = settings,
                        chatGptSession = session
                    )
                }
            }
        }
    }

    fun refreshDiagnosticsAndCloudState() {
        viewModelScope.launch {
            val cloudGranted = runCatching { aiHubConnector.isCloudGranted() }.getOrDefault(false)
            val reports = ErrorLog.reportCount(appContext)
            val cacheBytes = repository.imageCacheSizeBytes()
            _uiState.update {
                it.copy(
                    cloudAllowedInAiHub = cloudGranted,
                    savedErrorReportsCount = reports,
                    cacheSizeBytes = cacheBytes
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val dumpUrl = preferences.state.value.factCheckConfig.claimReviewDumpUrl
                val summary = repository.syncAllFeeds(dumpUrl)
                val msg = if (summary.errors.isEmpty()) {
                    "Sincronización completada (+${summary.newArticlesCount})"
                } else {
                    "Sincronizado (+${summary.newArticlesCount}), ${summary.errors.size} avisos"
                }
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        snackbarMessage = msg,
                        cacheSizeBytes = repository.imageCacheSizeBytes()
                    )
                }
            } catch (e: Exception) {
                ErrorLog.record(appContext, "Sincronización manual", e)
                _uiState.update { it.copy(isSyncing = false, snackbarMessage = "Error al sincronizar") }
            }
        }
    }

    fun addFeed(title: String, url: String, category: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            repository.addFeed(title, url, category)
            _uiState.update { it.copy(snackbarMessage = "Fuente añadida") }
        }
    }

    fun deleteFeed(feedId: String) {
        viewModelScope.launch {
            repository.deleteFeed(feedId)
        }
    }

    fun importOpml(xml: String) {
        viewModelScope.launch {
            val count = runCatching { repository.importOpml(xml) }.getOrDefault(0)
            _uiState.update { it.copy(snackbarMessage = "OPML importado ($count)") }
        }
    }

    fun exportOpml(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val xml = repository.exportOpml()
            onResult(xml)
        }
    }

    fun markArticleRead(article: ArticleEntity) {
        if (article.isRead) return
        viewModelScope.launch {
            repository.markRead(article.id, true)
        }
    }

    fun toggleBookmark(article: ArticleEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(article)
        }
    }

    /**
     * Issue #3 (§7 Estándar AiHub): cancelación inmediata y segura de la tarea de IA en curso.
     */
    fun cancelActiveAiTask(articleId: String, cancelledMessage: String) {
        activeAiJob?.cancel()
        activeAiJob = null
        updateArticleAi(articleId) {
            it.copy(isLoading = false, statusMessage = cancelledMessage)
        }
        _uiState.update { it.copy(snackbarMessage = cancelledMessage) }
    }

    fun summarizeArticle(article: ArticleEntity) {
        launchCancellableAiTask(article.id) {
            when (val res = repository.summarizeArticle(article)) {
                is AIResult.Success -> updateArticleAi(article.id) {
                    it.copy(
                        isLoading = false,
                        summaryText = res.data,
                        summaryOrigin = res.executedOn,
                        statusMessage = null
                    )
                }
                is AIResult.Error -> updateArticleAi(article.id) {
                    it.copy(isLoading = false, statusMessage = res.exception.message)
                }
            }
        }
    }

    fun rewriteHeadline(article: ArticleEntity) {
        launchCancellableAiTask(article.id) {
            when (val res = repository.rewriteHeadline(article)) {
                is AIResult.Success -> updateArticleAi(article.id) {
                    it.copy(isLoading = false, rewrittenTitle = res.data, statusMessage = null)
                }
                is AIResult.Error -> updateArticleAi(article.id) {
                    it.copy(isLoading = false, statusMessage = res.exception.message)
                }
            }
        }
    }

    fun runQuickAction(article: ArticleEntity, action: ActionKind) {
        launchCancellableAiTask(article.id) {
            when (val res = repository.runQuickAction(article, action)) {
                is AIResult.Success -> updateArticleAi(article.id) {
                    it.copy(
                        isLoading = false,
                        quickActionText = res.data,
                        quickActionOrigin = res.executedOn,
                        statusMessage = null
                    )
                }
                is AIResult.Error -> updateArticleAi(article.id) {
                    it.copy(isLoading = false, statusMessage = res.exception.message)
                }
            }
        }
    }

    fun analyzeCoverageMatrix(article: ArticleEntity) {
        val cluster = _uiState.value.clusters.firstOrNull { c -> c.articles.any { it.id == article.id } }
        val headlinesBySource = cluster?.articles?.associate { it.feedId to it.title }
            ?: mapOf(article.feedId to article.title)
        launchCancellableAiTask(article.id) {
            when (val res = repository.fetchCoverageMatrix(cluster?.clusterId ?: article.id, headlinesBySource)) {
                is AIResult.Success -> updateArticleAi(article.id) {
                    it.copy(isLoading = false, coverageMatrixText = res.data, statusMessage = null)
                }
                is AIResult.Error -> updateArticleAi(article.id) {
                    it.copy(isLoading = false, statusMessage = res.exception.message)
                }
            }
        }
    }

    fun verifyArticle(article: ArticleEntity, languageCode: String) {
        viewModelScope.launch {
            updateArticleFactCheck(article.id) { it.copy(isChecking = true) }
            try {
                val matches = repository.verifyArticle(
                    article = article,
                    config = preferences.state.value.factCheckConfig,
                    languageCode = languageCode
                )
                updateArticleFactCheck(article.id) {
                    it.copy(isChecking = false, matches = matches, checkedOnce = true)
                }
            } catch (e: Exception) {
                ErrorLog.record(appContext, "Verificación de noticias", e)
                updateArticleFactCheck(article.id) { it.copy(isChecking = false, checkedOnce = true) }
            }
        }
    }

    fun startChatGptSignIn(onLaunchBrowser: (String) -> Unit) {
        if (_uiState.value.isAuthInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthInProgress = true) }
            when (val result = chatGptOAuthManager.signInWithLoopback(onLaunchBrowser = onLaunchBrowser)) {
                is ChatGptAuthResult.Success -> {
                    preferences.setAiSourceMode(AiSourceMode.CHATGPT_OAUTH)
                    _uiState.update {
                        it.copy(
                            isAuthInProgress = false,
                            snackbarMessage = result.session.email?.let { mail -> "ChatGPT: $mail" } ?: "ChatGPT conectado"
                        )
                    }
                }
                is ChatGptAuthResult.Error -> {
                    _uiState.update {
                        it.copy(isAuthInProgress = false, snackbarMessage = result.message)
                    }
                }
            }
        }
    }

    fun signOutChatGpt() {
        chatGptOAuthManager.signOut()
        if (preferences.state.value.aiSourceMode == AiSourceMode.CHATGPT_OAUTH) {
            preferences.setAiSourceMode(AiSourceMode.AIHUB)
        }
        _uiState.update { it.copy(snackbarMessage = "ChatGPT desconectado") }
    }

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
    fun setAiSourceMode(mode: AiSourceMode) = preferences.setAiSourceMode(mode)

    fun updateFactCheckConfig(transform: (FactCheckConfig) -> FactCheckConfig) {
        preferences.updateFactCheckConfig(transform)
    }

    fun setSaveErrorReports(enabled: Boolean) {
        ErrorLog.isEnabled = enabled
        preferences.setSaveErrorReports(enabled)
    }

    fun clearErrorReports() {
        ErrorLog.clearReports(appContext)
        _uiState.update { it.copy(savedErrorReportsCount = 0) }
    }

    fun clearCaches() {
        viewModelScope.launch {
            repository.clearCaches()
            _uiState.update { it.copy(cacheSizeBytes = 0L, snackbarMessage = "Caché vaciada") }
        }
    }

    fun setDisplayName(name: String) = preferences.setDisplayName(name)

    fun setProfilePhotoFromUri(uri: Uri, errorMessage: String) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val scale = 512f / maxOf(info.size.width, info.size.height)
                            if (scale < 1f) {
                                decoder.setTargetSize(
                                    (info.size.width * scale).toInt(),
                                    (info.size.height * scale).toInt()
                                )
                            }
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        null
                    } ?: return@runCatching null
                    ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                }.getOrNull()
            }
            if (bytes != null) {
                preferences.setProfilePhoto(bytes)
            } else {
                _uiState.update { it.copy(snackbarMessage = errorMessage) }
            }
        }
    }

    fun removeProfilePhoto() = preferences.setProfilePhoto(null)

    private fun launchCancellableAiTask(articleId: String, block: suspend () -> Unit) {
        activeAiJob?.cancel()
        updateArticleAi(articleId) { it.copy(isLoading = true, statusMessage = null) }
        activeAiJob = viewModelScope.launch {
            try {
                block()
            } catch (_: CancellationException) {
                updateArticleAi(articleId) { it.copy(isLoading = false) }
            } catch (e: Exception) {
                ErrorLog.record(appContext, "Tarea de IA", e)
                updateArticleAi(articleId) { it.copy(isLoading = false, statusMessage = e.message) }
            }
        }
    }

    private fun updateArticleAi(articleId: String, transform: (ArticleAiState) -> ArticleAiState) {
        _uiState.update { state ->
            val current = state.articleAiStates[articleId] ?: ArticleAiState()
            state.copy(articleAiStates = state.articleAiStates + (articleId to transform(current)))
        }
    }

    private fun updateArticleFactCheck(articleId: String, transform: (ArticleFactCheckState) -> ArticleFactCheckState) {
        _uiState.update { state ->
            val current = state.articleFactCheckStates[articleId] ?: ArticleFactCheckState()
            state.copy(articleFactCheckStates = state.articleFactCheckStates + (articleId to transform(current)))
        }
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
