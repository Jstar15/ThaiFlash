package com.thaiflash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thaiflash.app.audio.TtsManager
import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.ReviewFocus
import com.thaiflash.app.data.model.SetStudyOrder
import com.thaiflash.app.data.model.StudyDirection
import com.thaiflash.app.data.repository.FlashcardRepository
import com.thaiflash.app.data.repository.SettingsStore
import com.thaiflash.app.domain.srs.ReviewScheduler
import com.thaiflash.app.ui.navigation.Screen
import com.thaiflash.app.ui.review.ReviewHubScreen
import com.thaiflash.app.ui.search.SearchScreen
import com.thaiflash.app.ui.sets.SetDetailScreen
import com.thaiflash.app.ui.sets.SetsScreen
import com.thaiflash.app.ui.settings.SettingsScreen
import com.thaiflash.app.ui.study.ListenSetScreen
import com.thaiflash.app.ui.study.StudyScreen

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        ttsManager = TtsManager(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val repository = remember { FlashcardRepository(this) }
                    val settingsStore = remember {
                        SettingsStore(this).also { store ->
                            ttsManager.speechRate = store.current.speechRate
                            ttsManager.applyPreferredVoice(store.current.selectedVoice)
                        }
                    }

                    ThaiFlashApp(
                        repository = repository,
                        settingsStore = settingsStore,
                        ttsManager = ttsManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}

@Composable
private fun ThaiFlashApp(
    repository: FlashcardRepository,
    settingsStore: SettingsStore,
    ttsManager: TtsManager
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val db by repository.db.collectAsState()
    val settings by settingsStore.settings.collectAsState()

    val navigationItems = listOf(
        Pair(Screen.Sets, Icons.Default.Style),
        Pair(Screen.Review, Icons.Default.Replay),
        Pair(Screen.Search, Icons.Default.Search)
    )

    val isFullScreenStudy = currentRoute?.startsWith("study_set/") == true ||
        currentRoute?.startsWith("listen_set/") == true ||
        currentRoute?.startsWith("review_session/") == true

    fun navigateToTab(screen: Screen) {
        if (currentRoute != screen.route) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        topBar = {
            if (currentRoute != Screen.Settings.route && !isFullScreenStudy) {
                SettingsGearBar(
                    onSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isFullScreenStudy && currentRoute != Screen.Settings.route) {
                NavigationBar {
                    navigationItems.forEach { (screen, icon) ->
                        val onSetDetail = currentRoute?.startsWith("set/") == true
                        val isSelected = currentRoute == screen.route ||
                            (screen == Screen.Sets && onSetDetail)

                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = screen.title) },
                            label = { Text(screen.title, maxLines = 1) },
                            selected = isSelected,
                            onClick = { navigateToTab(screen) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Sets.route,
            modifier = Modifier
                .padding(innerPadding)
                .then(
                    if (isFullScreenStudy || currentRoute == Screen.Settings.route) {
                        Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    } else {
                        Modifier
                    }
                )
        ) {
            composable(Screen.Sets.route) {
                SetsScreen(
                    repository = repository,
                    settingsStore = settingsStore,
                    ttsManager = ttsManager,
                    onOpenSet = { setId ->
                        navController.navigate(Screen.SetDetail.routeFor(setId)) {
                            launchSingleTop = true
                        }
                    },
                    onQuickStudySet = { setId ->
                        navController.navigate(Screen.StudySet.routeFor(setId, SetStudyOrder.IN_ORDER)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.SetDetail.route,
                arguments = listOf(navArgument(Screen.SetDetail.ARG_SET_ID) { type = NavType.StringType })
            ) { entry ->
                val setId = entry.arguments?.getString(Screen.SetDetail.ARG_SET_ID).orEmpty()
                SetDetailScreen(
                    setId = setId,
                    repository = repository,
                    settingsStore = settingsStore,
                    ttsManager = ttsManager,
                    onBack = { navController.popBackStack() },
                    onStudySet = { order ->
                        navController.navigate(Screen.StudySet.routeFor(setId, order)) {
                            launchSingleTop = true
                        }
                    },
                    onListenSet = { order ->
                        navController.navigate(Screen.ListenSet.routeFor(setId, order)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.ListenSet.route,
                arguments = listOf(
                    navArgument(Screen.ListenSet.ARG_SET_ID) { type = NavType.StringType },
                    navArgument(Screen.ListenSet.ARG_ORDER) { type = NavType.StringType }
                )
            ) { entry ->
                val setId = entry.arguments?.getString(Screen.ListenSet.ARG_SET_ID).orEmpty()
                val orderName = entry.arguments?.getString(Screen.ListenSet.ARG_ORDER).orEmpty()
                val order = runCatching { SetStudyOrder.valueOf(orderName) }.getOrDefault(SetStudyOrder.IN_ORDER)
                val set = db.sets.firstOrNull { it.id == setId }
                val orderedCards = remember(setId, order) {
                    val raw = db.cards.filter { it.setId == setId }
                    when (order) {
                        SetStudyOrder.IN_ORDER -> raw
                        SetStudyOrder.SHUFFLED -> raw.shuffled()
                        SetStudyOrder.STRUGGLING_FIRST -> raw.sortedBy {
                            when (it.rating) {
                                CardRating.BAD -> 0
                                CardRating.OK -> 1
                                CardRating.NEW -> 2
                                CardRating.GOOD -> 3
                            }
                        }
                    }
                }

                ListenSetScreen(
                    title = set?.let { "${it.emoji} ${it.name}" } ?: "Listen Set",
                    cards = orderedCards,
                    settingsStore = settingsStore,
                    ttsManager = ttsManager,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.StudySet.route,
                arguments = listOf(
                    navArgument(Screen.StudySet.ARG_SET_ID) { type = NavType.StringType },
                    navArgument(Screen.StudySet.ARG_ORDER) { type = NavType.StringType }
                )
            ) { entry ->
                val setId = entry.arguments?.getString(Screen.StudySet.ARG_SET_ID).orEmpty()
                val orderName = entry.arguments?.getString(Screen.StudySet.ARG_ORDER).orEmpty()
                val order = runCatching { SetStudyOrder.valueOf(orderName) }.getOrDefault(SetStudyOrder.IN_ORDER)
                val set = db.sets.firstOrNull { it.id == setId }
                val initialCards = remember(setId, order) {
                    val raw = db.cards.filter { it.setId == setId }
                    when (order) {
                        SetStudyOrder.IN_ORDER -> raw
                        SetStudyOrder.SHUFFLED -> raw.shuffled()
                        SetStudyOrder.STRUGGLING_FIRST -> raw.sortedBy {
                            when (it.rating) {
                                CardRating.BAD -> 0
                                CardRating.OK -> 1
                                CardRating.NEW -> 2
                                CardRating.GOOD -> 3
                            }
                        }
                    }
                }

                StudyScreen(
                    title = set?.let { "${it.emoji} ${it.name}" } ?: "Study",
                    initialCards = initialCards,
                    initialDirection = settings.defaultDirection,
                    isReviewMode = false,
                    repository = repository,
                    settingsStore = settingsStore,
                    ttsManager = ttsManager,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Review.route) {
                ReviewHubScreen(
                    repository = repository,
                    settingsStore = settingsStore,
                    onStartReview = { focus, direction ->
                        navController.navigate(Screen.ReviewSession.routeFor(focus, direction)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.ReviewSession.route,
                arguments = listOf(
                    navArgument(Screen.ReviewSession.ARG_FOCUS) { type = NavType.StringType },
                    navArgument(Screen.ReviewSession.ARG_DIRECTION) { type = NavType.StringType }
                )
            ) { entry ->
                val focusName = entry.arguments?.getString(Screen.ReviewSession.ARG_FOCUS).orEmpty()
                val dirName = entry.arguments?.getString(Screen.ReviewSession.ARG_DIRECTION).orEmpty()
                val focus = runCatching { ReviewFocus.valueOf(focusName) }.getOrDefault(ReviewFocus.SMART_10)
                val direction = runCatching { StudyDirection.valueOf(dirName) }
                    .getOrDefault(settings.defaultDirection)

                var sessionNonce by remember { mutableIntStateOf(0) }
                val activeSetIds = remember(db.sets) {
                    db.sets.filter { it.isActiveInReview() }.map { it.id }.toSet()
                }
                val reviewCards = remember(focus, sessionNonce, activeSetIds) {
                    val activeCards = db.cards.filter { it.setId in activeSetIds }
                    ReviewScheduler.buildReviewDeck(
                        allCards = activeCards,
                        focus = focus,
                        sessionSize = 10
                    )
                }

                StudyScreen(
                    title = when (focus) {
                        ReviewFocus.SMART_10 -> "Smart Review"
                        ReviewFocus.RED_ONLY -> "Red Focus Review"
                        ReviewFocus.NEW_CARDS -> "New Cards Review"
                    },
                    initialCards = reviewCards,
                    initialDirection = direction,
                    isReviewMode = true,
                    repository = repository,
                    settingsStore = settingsStore,
                    ttsManager = ttsManager,
                    onBack = { navController.popBackStack() },
                    onReviewMore = { sessionNonce++ }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    repository = repository,
                    settingsStore = settingsStore,
                    ttsManager = ttsManager
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    ttsManager = ttsManager,
                    settingsStore = settingsStore,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun SettingsGearBar(onSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(44.dp)
            .padding(end = 10.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
