package pl.prawko.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.prawko.app.domain.Appearance
import pl.prawko.app.domain.PreferencesRepository
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.UserPreferences

data class PrawkoUiState(
    val preferences: UserPreferences? = null,
    val questionAvailability: QuestionAvailability = QuestionAvailability.UNAVAILABLE,
    val preferenceWriteFailed: Boolean = false,
)

class PrawkoViewModel(
    private val preferencesRepository: PreferencesRepository,
    questionRepository: QuestionRepository,
) : ViewModel() {
    private val preferenceWriteFailed = MutableStateFlow(false)

    val uiState: StateFlow<PrawkoUiState> = combine(
        preferencesRepository.preferences,
        questionRepository.availability,
        preferenceWriteFailed,
        ::PrawkoUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PrawkoUiState(),
    )

    fun completeOnboarding() = savePreference { preferencesRepository.completeOnboarding() }

    fun setAppearance(appearance: Appearance) =
        savePreference { preferencesRepository.setAppearance(appearance) }

    fun clearError() {
        preferenceWriteFailed.value = false
    }

    private fun savePreference(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (_: IOException) {
                preferenceWriteFailed.value = true
            }
        }
    }
}
