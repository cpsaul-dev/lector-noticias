package dev.cpsaul.noticias

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cpsaul.noticias.diagnostics.ErrorLog
import dev.cpsaul.noticias.sync.PeriodicSyncWorker
import dev.cpsaul.noticias.ui.components.MainHeader
import dev.cpsaul.noticias.ui.components.NavDestination
import dev.cpsaul.noticias.ui.components.PillNavigationBar
import dev.cpsaul.noticias.ui.screens.AboutScreen
import dev.cpsaul.noticias.ui.screens.ArticleDetailScreen
import dev.cpsaul.noticias.ui.screens.BriefingScreen
import dev.cpsaul.noticias.ui.screens.ClustersScreen
import dev.cpsaul.noticias.ui.screens.FeedScreen
import dev.cpsaul.noticias.ui.screens.FeedsManageScreen
import dev.cpsaul.noticias.ui.screens.ProfileScreen
import dev.cpsaul.noticias.ui.screens.SavedScreen
import dev.cpsaul.noticias.ui.screens.SettingsScreen
import dev.cpsaul.noticias.ui.theme.PulsoTheme
import dev.cpsaul.noticias.ui.viewmodel.ArticleAiState
import dev.cpsaul.noticias.ui.viewmodel.ArticleFactCheckState
import dev.cpsaul.noticias.ui.viewmodel.NewsViewModel

private sealed interface AppRoute {
    data object Home : AppRoute
    data class ArticleDetail(val articleId: String) : AppRoute
    data object Profile : AppRoute
    data object Settings : AppRoute
    data object FeedsManage : AppRoute
    data object About : AppRoute
}

class MainActivity : AppCompatActivity() {

    private val viewModel: NewsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ErrorLog.installUncaughtHandler(applicationContext)
        PeriodicSyncWorker.schedule(applicationContext)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PulsoTheme(themeMode = uiState.settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PulsoAppShell(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun PulsoAppShell(viewModel: NewsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isExpandedWidth = LocalConfiguration.current.screenWidthDp >= 840

    var selectedTab by remember { mutableIntStateOf(0) }
    var currentRoute by remember { mutableStateOf<AppRoute>(AppRoute.Home) }
    var selectedArticleIdForWide by remember { mutableStateOf<String?>(null) }

    val photoErrorText = stringResource(R.string.profile_photo_error)

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    fun openUrl(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }

    fun shareOpml(opmlXml: String) {
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/xml"
                putExtra(Intent.EXTRA_SUBJECT, "pulso-feeds.opml")
                putExtra(Intent.EXTRA_TEXT, opmlXml)
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        }
    }

    BackHandler(enabled = currentRoute != AppRoute.Home) {
        currentRoute = when (currentRoute) {
            AppRoute.About, AppRoute.FeedsManage -> AppRoute.Settings
            AppRoute.Settings -> AppRoute.Profile
            else -> AppRoute.Home
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(0, R.string.nav_briefing, Icons.Default.Newspaper),
            NavDestination(1, R.string.nav_feed, Icons.Default.DynamicFeed),
            NavDestination(2, R.string.nav_clusters, Icons.Default.Hub),
            NavDestination(3, R.string.nav_saved, Icons.Default.Bookmark)
        )
    }

    // §6.2 Empuje lateral sin rebote (nunca superposición ni scale/fade)
    val pushSpec = remember {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { outerPadding ->
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                val forward = targetState != AppRoute.Home && initialState == AppRoute.Home
                if (forward) {
                    slideInHorizontally(pushSpec) { it } togetherWith slideOutHorizontally(pushSpec) { -it }
                } else {
                    slideInHorizontally(pushSpec) { -it } togetherWith slideOutHorizontally(pushSpec) { it }
                }
            },
            label = "pulso_route_push",
            modifier = Modifier.fillMaxSize()
        ) { route ->
            when (route) {
                AppRoute.Home -> {
                    val onSelectArticle: (String) -> Unit = { id ->
                        uiState.articles.firstOrNull { it.id == id }?.let { viewModel.markArticleRead(it) }
                        if (isExpandedWidth) {
                            selectedArticleIdForWide = id
                        } else {
                            currentRoute = AppRoute.ArticleDetail(id)
                        }
                    }

                    if (isExpandedWidth) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                destinations.forEach { dest ->
                                    NavigationRailItem(
                                        selected = selectedTab == dest.index,
                                        onClick = { selectedTab = dest.index },
                                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                        label = { Text(stringResource(dest.labelRes)) }
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                                HomeTabScaffold(
                                    selectedTab = selectedTab,
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onSelectArticle = onSelectArticle,
                                    onProfileClick = { currentRoute = AppRoute.Profile },
                                    bottomPadding = outerPadding
                                )
                            }
                            val wideArticle = uiState.articles.firstOrNull { it.id == selectedArticleIdForWide }
                                ?: uiState.briefingArticles.firstOrNull()
                            if (wideArticle != null) {
                                Box(modifier = Modifier.weight(0.55f).fillMaxHeight()) {
                                    ArticleDetailScreen(
                                        article = wideArticle,
                                        timelineArticles = uiState.timelineFor(wideArticle),
                                        aiState = uiState.articleAiStates[wideArticle.id] ?: ArticleAiState(),
                                        factCheckState = uiState.articleFactCheckStates[wideArticle.id] ?: ArticleFactCheckState(),
                                        cloudAllowed = uiState.cloudAllowedInAiHub || uiState.chatGptSession?.hasPlanUsagePermission == true,
                                        showBackButton = false,
                                        onBack = {},
                                        onToggleBookmark = { viewModel.toggleBookmark(wideArticle) },
                                        onSummarize = { viewModel.summarizeArticle(wideArticle) },
                                        onRewriteHeadline = { viewModel.rewriteHeadline(wideArticle) },
                                        onQuickAction = { viewModel.runQuickAction(wideArticle, it) },
                                        onAnalyzeCoverage = { viewModel.analyzeCoverageMatrix(wideArticle) },
                                        onStopAi = { viewModel.cancelActiveAiTask(wideArticle.id, it) },
                                        onVerifyClaims = { viewModel.verifyArticle(wideArticle, it) },
                                        onOpenUrl = ::openUrl,
                                        onSelectRelatedArticle = { selectedArticleIdForWide = it.id }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HomeTabScaffold(
                                selectedTab = selectedTab,
                                uiState = uiState,
                                viewModel = viewModel,
                                onSelectArticle = onSelectArticle,
                                onProfileClick = { currentRoute = AppRoute.Profile },
                                bottomPadding = outerPadding
                            )
                            PillNavigationBar(
                                destinations = destinations,
                                selectedIndex = selectedTab,
                                onSelect = { selectedTab = it },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .navigationBarsPadding()
                                    .padding(start = 16.dp, bottom = 14.dp)
                            )
                        }
                    }
                }

                is AppRoute.ArticleDetail -> {
                    val article = uiState.articles.firstOrNull { it.id == route.articleId }
                    if (article != null) {
                        ArticleDetailScreen(
                            article = article,
                            timelineArticles = uiState.timelineFor(article),
                            aiState = uiState.articleAiStates[article.id] ?: ArticleAiState(),
                            factCheckState = uiState.articleFactCheckStates[article.id] ?: ArticleFactCheckState(),
                            cloudAllowed = uiState.cloudAllowedInAiHub || uiState.chatGptSession?.hasPlanUsagePermission == true,
                            showBackButton = true,
                            onBack = { currentRoute = AppRoute.Home },
                            onToggleBookmark = { viewModel.toggleBookmark(article) },
                            onSummarize = { viewModel.summarizeArticle(article) },
                            onRewriteHeadline = { viewModel.rewriteHeadline(article) },
                            onQuickAction = { viewModel.runQuickAction(article, it) },
                            onAnalyzeCoverage = { viewModel.analyzeCoverageMatrix(article) },
                            onStopAi = { viewModel.cancelActiveAiTask(article.id, it) },
                            onVerifyClaims = { viewModel.verifyArticle(article, it) },
                            onOpenUrl = ::openUrl,
                            onSelectRelatedArticle = { currentRoute = AppRoute.ArticleDetail(it.id) }
                        )
                    } else {
                        currentRoute = AppRoute.Home
                    }
                }

                AppRoute.Profile -> ProfileScreen(
                    profile = uiState.settings.userProfile,
                    onBack = { currentRoute = AppRoute.Home },
                    onOpenSettings = {
                        viewModel.refreshDiagnosticsAndCloudState()
                        currentRoute = AppRoute.Settings
                    },
                    onSetName = viewModel::setDisplayName,
                    onSetPhoto = { uri -> viewModel.setProfilePhotoFromUri(uri, photoErrorText) },
                    onRemovePhoto = viewModel::removeProfilePhoto
                )

                AppRoute.Settings -> SettingsScreen(
                    uiState = uiState,
                    onBack = { currentRoute = AppRoute.Profile },
                    onOpenFeedsManage = { currentRoute = AppRoute.FeedsManage },
                    onOpenAbout = { currentRoute = AppRoute.About },
                    onStartChatGptSignIn = { viewModel.startChatGptSignIn(::openUrl) },
                    onSignOutChatGpt = viewModel::signOutChatGpt,
                    onSelectAiSource = viewModel::setAiSourceMode,
                    onUpdateFactCheckConfig = viewModel::updateFactCheckConfig,
                    onSelectTheme = viewModel::setThemeMode,
                    onSetSaveErrorReports = viewModel::setSaveErrorReports,
                    onClearErrorReports = viewModel::clearErrorReports,
                    onClearCaches = viewModel::clearCaches,
                    onSyncNow = viewModel::syncNow
                )

                AppRoute.FeedsManage -> FeedsManageScreen(
                    feeds = uiState.feeds,
                    onBack = { currentRoute = AppRoute.Settings },
                    onDeleteFeed = viewModel::deleteFeed,
                    onImportOpml = viewModel::importOpml,
                    onExportOpml = { viewModel.exportOpml(::shareOpml) }
                )

                AppRoute.About -> AboutScreen(
                    onBack = { currentRoute = AppRoute.Settings }
                )
            }
        }
    }
}

@Composable
private fun HomeTabScaffold(
    selectedTab: Int,
    uiState: dev.cpsaul.noticias.ui.viewmodel.NewsUiState,
    viewModel: NewsViewModel,
    onSelectArticle: (String) -> Unit,
    onProfileClick: () -> Unit,
    bottomPadding: PaddingValues
) {
    val tabTitle = when (selectedTab) {
        0 -> stringResource(R.string.app_name)
        1 -> stringResource(R.string.nav_feed)
        2 -> stringResource(R.string.nav_clusters)
        else -> stringResource(R.string.nav_saved)
    }
    Scaffold(
        topBar = {
            MainHeader(
                title = tabTitle,
                profile = uiState.settings.userProfile,
                onProfileClick = onProfileClick
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(top = innerPadding.calculateTopPadding())
        when (selectedTab) {
            0 -> BriefingScreen(
                articles = uiState.briefingArticles,
                isSyncing = uiState.isSyncing,
                onSelectArticle = { onSelectArticle(it.id) },
                onSyncNow = viewModel::syncNow,
                bottomPadding = bottomPadding,
                modifier = contentModifier
            )
            1 -> FeedScreen(
                articles = uiState.filteredArticles(),
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                searchQuery = uiState.searchQuery,
                onSearchChange = viewModel::setSearchQuery,
                onSelectCategory = viewModel::selectCategory,
                onSelectArticle = { onSelectArticle(it.id) },
                onAddFeed = viewModel::addFeed,
                bottomPadding = bottomPadding,
                modifier = contentModifier
            )
            2 -> ClustersScreen(
                clusters = uiState.clusters,
                onSelectArticle = { onSelectArticle(it.id) },
                bottomPadding = bottomPadding,
                modifier = contentModifier
            )
            else -> SavedScreen(
                articles = uiState.bookmarkedArticles,
                onSelectArticle = { onSelectArticle(it.id) },
                bottomPadding = bottomPadding,
                modifier = contentModifier
            )
        }
    }
}
