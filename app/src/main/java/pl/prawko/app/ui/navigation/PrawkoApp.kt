package pl.prawko.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.InputStream
import org.json.JSONArray
import pl.prawko.app.domain.Appearance
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.StudyProgressRepository
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.ui.PrawkoUiState
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.dashboard.DashboardScreen
import pl.prawko.app.ui.exam.ExamScreen
import pl.prawko.app.ui.modes.ModeSelectionScreen
import pl.prawko.app.ui.onboarding.OnboardingScreen
import pl.prawko.app.ui.progress.ExamResultScreen
import pl.prawko.app.ui.progress.ProgressScreen
import pl.prawko.app.ui.settings.MediaPackScreen
import pl.prawko.app.ui.settings.SettingsScreen

internal class PrawkoFeatures(
    val questionRepository: QuestionRepository,
    val studyProgressRepository: StudyProgressRepository,
    val examRepository: ExamRepository,
    val mediaRepository: MediaRepository,
    val isDemo: Boolean,
    val installMediaPack: suspend (String, InputStream, String) -> Int,
)

private const val ReviewRoute = "study/review/{version}?ids={ids}"
private const val ResultRoute = "result/{attemptId}"
private const val HistoryRoute = "history"
private const val MediaPacksRoute = "media-packs"

private enum class StudyMode(val route: String) {
    STUDY("study"),
    EXAM("exam"),
}

private enum class AppTab(val route: String, val label: String, val icon: PrawkoSymbol) {
    DASHBOARD("dashboard", "Start", PrawkoSymbol.DASHBOARD),
    MODES("modes", "Tryby", PrawkoSymbol.LAYERS),
    PROGRESS("progress", "Postęp", PrawkoSymbol.PROGRESS),
    SETTINGS("settings", "Ustawienia", PrawkoSymbol.SETTINGS),
}

@Composable
internal fun PrawkoApp(
    state: PrawkoUiState,
    features: PrawkoFeatures,
    onCompleteOnboarding: () -> Unit,
    onAppearanceChange: (Appearance) -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val tab = AppTab.entries.firstOrNull { it.route == currentEntry?.destination?.route }
    val preferences = state.preferences

    LaunchedEffect(state.preferenceWriteFailed) {
        if (state.preferenceWriteFailed) {
            snackbar.showSnackbar("Nie udało się zapisać ustawień. Spróbuj ponownie.")
            onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (features.isDemo) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Box(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "DEMO · Dane syntetyczne",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (preferences?.onboardingCompleted == true && tab != null) {
                AppNavigationBar(tab, navController::selectTab)
            }
        },
    ) { padding ->
        val content = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)

        when {
            preferences == null -> Box(content, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { stateDescription = "Wczytywanie ustawień" },
                )
            }
            !preferences.onboardingCompleted -> OnboardingScreen(
                onComplete = onCompleteOnboarding,
                modifier = content,
            )
            else -> NavHost(
                navController = navController,
                startDestination = AppTab.DASHBOARD.route,
                modifier = content,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
                composable(AppTab.DASHBOARD.route) {
                    DashboardScreen(
                        availability = state.questionAvailability,
                        onExam = { navController.openMode(StudyMode.EXAM) },
                        onStudy = { navController.openMode(StudyMode.STUDY) },
                        onProgress = { navController.selectTab(AppTab.PROGRESS) },
                    )
                }
                composable(AppTab.MODES.route) {
                    ModeSelectionScreen(
                        onBack = { navController.selectTab(AppTab.DASHBOARD) },
                        onStudy = { navController.openMode(StudyMode.STUDY) },
                        onExam = { navController.openMode(StudyMode.EXAM) },
                    )
                }
                composable(AppTab.SETTINGS.route) {
                    SettingsScreen(
                        appearance = preferences.appearance,
                        onAppearanceChange = onAppearanceChange,
                        onMediaPacks = { navController.navigate(MediaPacksRoute) },
                    )
                }
                composable(AppTab.PROGRESS.route) {
                    ProgressScreen(
                        studyProgressRepository = features.studyProgressRepository,
                        examRepository = features.examRepository,
                        questionRepository = features.questionRepository,
                        datasetVersion = state.datasetVersion,
                        isDemo = features.isDemo,
                        onBack = { navController.popBackStack() },
                        onAttemptClick = { navController.navigate("result/${Uri.encode(it)}") },
                        onStudy = { navController.openMode(StudyMode.STUDY) },
                    )
                }
                composable(HistoryRoute) {
                    ProgressScreen(
                        studyProgressRepository = features.studyProgressRepository,
                        examRepository = features.examRepository,
                        questionRepository = features.questionRepository,
                        datasetVersion = state.datasetVersion,
                        isDemo = features.isDemo,
                        onBack = { navController.popBackStack() },
                        onAttemptClick = { navController.navigate("result/${Uri.encode(it)}") },
                        onStudy = { navController.openMode(StudyMode.STUDY) },
                        showHistoryInitially = true,
                    )
                }
                composable(StudyMode.STUDY.route) {
                    StudyDestination(
                        datasetVersion = state.datasetVersion,
                        features = features,
                        onBack = { navController.popBackStack() },
                        onStats = { navController.navigate(AppTab.PROGRESS.route) },
                    )
                }
                composable(
                    route = ReviewRoute,
                    arguments = listOf(
                        navArgument("version") { type = NavType.StringType },
                        navArgument("ids") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val version = requireNotNull(entry.arguments?.getString("version"))
                    val ids = JSONArray(requireNotNull(entry.arguments?.getString("ids")))
                    StudyDestination(
                        datasetVersion = version,
                        features = features,
                        initialMistakeIds = List(ids.length(), ids::getString).toSet(),
                        onBack = { navController.popBackStack() },
                        onStats = { navController.navigate(AppTab.PROGRESS.route) },
                    )
                }
                composable(StudyMode.EXAM.route) {
                    ExamScreen(
                        questionRepository = features.questionRepository,
                        examRepository = features.examRepository,
                        mediaRepository = features.mediaRepository,
                        isDemo = features.isDemo,
                        onBack = { navController.popBackStack() },
                        onResult = { id ->
                            navController.navigate("result/${Uri.encode(id)}") {
                                popUpTo(StudyMode.EXAM.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    route = ResultRoute,
                    arguments = listOf(navArgument("attemptId") { type = NavType.StringType }),
                ) { entry ->
                    ExamResultScreen(
                        attemptId = requireNotNull(entry.arguments?.getString("attemptId")),
                        examRepository = features.examRepository,
                        questionRepository = features.questionRepository,
                        isDemo = features.isDemo,
                        onBack = { navController.popBackStack() },
                        onHistory = {
                            navController.navigate(HistoryRoute) {
                                popUpTo(AppTab.DASHBOARD.route)
                                launchSingleTop = true
                            }
                        },
                        onPracticeMistakes = { version, ids ->
                            val encodedIds = Uri.encode(JSONArray(ids.toList().sorted()).toString())
                            navController.navigate("study/review/${Uri.encode(version)}?ids=$encodedIds")
                        },
                    )
                }
                composable(MediaPacksRoute) {
                    MediaPackScreen(
                        datasetVersion = state.datasetVersion,
                        installMediaPack = features.installMediaPack,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onSelect(tab) },
                icon = { PrawkoIcon(symbol = tab.icon) },
                label = { Text(tab.label) },
            )
        }
    }
}

private fun NavHostController.selectTab(tab: AppTab) {
    if (currentDestination?.route == tab.route) return

    navigate(tab.route) {
        popUpTo(AppTab.DASHBOARD.route)
        launchSingleTop = true
    }
}

private fun NavHostController.openMode(mode: StudyMode) {
    navigate(mode.route) { launchSingleTop = true }
}
