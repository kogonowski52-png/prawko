package pl.prawko.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import pl.prawko.app.domain.UserPreferences
import pl.prawko.app.ui.navigation.PrawkoApp
import pl.prawko.app.ui.theme.PrawkoTheme

class PrawkoNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun completingOnboardingOpensDashboardAndExamExplainsMissingQuestions() {
        showApp(UserPreferences())

        compose.onNodeWithText("Przejdź do pulpitu").performScrollTo().performClick()
        compose.onNodeWithText("Zobacz egzamin").performScrollTo().performClick()
        compose.onNodeWithText("Brak bazy pytań").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wróć").performClick()
        compose.onNodeWithText("Start").assertIsSelected()
    }

    @Test
    fun studyExplainsMissingQuestionsAndSettingsApplyAppearance() {
        showApp(UserPreferences(onboardingCompleted = true))

        compose.onNodeWithText("Tryby").performClick()
        compose.onNodeWithText("Wybierz tryb").assertIsDisplayed()
        compose.onNodeWithText("Nauka").performScrollTo().performClick()
        compose.onNodeWithText("Brak bazy pytań").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wróć").performClick()
        compose.onNodeWithText("Tryby").assertIsSelected()
        compose.onNodeWithText("Ustawienia").performClick()
        compose.onNodeWithText("Ciemny").performScrollTo().performClick().assertIsSelected()
    }

    private fun showApp(initialPreferences: UserPreferences) {
        val preferences = mutableStateOf(initialPreferences)
        compose.setContent {
            PrawkoTheme {
                PrawkoApp(
                    state = PrawkoUiState(preferences = preferences.value),
                    onCompleteOnboarding = {
                        preferences.value = preferences.value.copy(onboardingCompleted = true)
                    },
                    onAppearanceChange = {
                        preferences.value = preferences.value.copy(appearance = it)
                    },
                    onErrorShown = {},
                )
            }
        }
    }
}
