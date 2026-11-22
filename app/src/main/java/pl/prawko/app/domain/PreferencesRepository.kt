package pl.prawko.app.domain

import kotlinx.coroutines.flow.Flow

enum class Appearance {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val appearance: Appearance = Appearance.SYSTEM,
)

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun completeOnboarding()

    suspend fun setAppearance(appearance: Appearance)
}
