package pl.prawko.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pl.prawko.app.domain.Appearance
import pl.prawko.app.ui.PrawkoUiState
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.dashboard.DashboardScreen
import pl.prawko.app.ui.modes.ModeSelectionScreen
import pl.prawko.app.ui.modes.QuestionAvailabilityScreen
import pl.prawko.app.ui.onboarding.OnboardingScreen
import pl.prawko.app.ui.settings.SettingsScreen

enum class StudyMode(val route: String) {
    STUDY("study"),
    EXAM("exam"),
}

private enum class AppTab(val route: String, val label: String, val icon: PrawkoSymbol) {
    DASHBOARD("dashboard", "Start", PrawkoSymbol.DASHBOARD),
    MODES("modes", "Tryby", PrawkoSymbol.LAYERS),
    SETTINGS("settings", "Ustawienia", PrawkoSymbol.SETTINGS),
}

@Composable
fun PrawkoApp(
    state: PrawkoUiState,
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
                    )
                }
                StudyMode.entries.forEach { mode ->
                    composable(mode.route) {
                        QuestionAvailabilityScreen(
                            mode = mode,
                            availability = state.questionAvailability,
                            onBack = { navController.popBackStack() },
                        )
                    }
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
