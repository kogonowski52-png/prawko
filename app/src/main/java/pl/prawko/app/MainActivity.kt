package pl.prawko.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.prawko.app.domain.Appearance
import pl.prawko.app.ui.PrawkoViewModel
import pl.prawko.app.ui.navigation.PrawkoApp
import pl.prawko.app.ui.navigation.PrawkoFeatures
import pl.prawko.app.ui.theme.PrawkoTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as PrawkoApplication).container

    private val viewModel by viewModels<PrawkoViewModel> {
        viewModelFactory {
            initializer {
                PrawkoViewModel(container.preferencesRepository, container.questionRepository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val features = remember {
                PrawkoFeatures(
                    questionRepository = container.questionRepository,
                    studyProgressRepository = container.studyProgressRepository,
                    examRepository = container.examRepository,
                    mediaRepository = container.mediaRepository,
                    isDemo = BuildConfig.PRAWKO_DEMO,
                    installMediaPack = { version, input, checksum ->
                        container.mediaRepository.installPack(version, input, checksum).fileCount
                    },
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (state.preferences?.appearance) {
                Appearance.LIGHT -> false
                Appearance.DARK -> true
                Appearance.SYSTEM, null -> systemDark
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            PrawkoTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PrawkoApp(
                        state = state,
                        features = features,
                        onCompleteOnboarding = viewModel::completeOnboarding,
                        onAppearanceChange = viewModel::setAppearance,
                        onErrorShown = viewModel::clearError,
                    )
                }
            }
        }
    }
}
