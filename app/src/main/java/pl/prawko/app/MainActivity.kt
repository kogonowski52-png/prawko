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
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.prawko.app.domain.Appearance
import pl.prawko.app.ui.PrawkoViewModel
import pl.prawko.app.ui.navigation.PrawkoApp
import pl.prawko.app.ui.theme.PrawkoTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PrawkoViewModel> {
        val container = (application as PrawkoApplication).container
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
                        onCompleteOnboarding = viewModel::completeOnboarding,
                        onAppearanceChange = viewModel::setAppearance,
                        onErrorShown = viewModel::clearError,
                    )
                }
            }
        }
    }
}
