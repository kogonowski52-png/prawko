package pl.prawko.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import pl.prawko.app.domain.Appearance
import pl.prawko.app.domain.PreferencesRepository
import pl.prawko.app.domain.UserPreferences

internal val Context.prawkoPreferences by preferencesDataStore(name = "prawko_preferences")

internal class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {
    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { stored ->
            UserPreferences(
                onboardingCompleted = stored[OnboardingCompleted] ?: false,
                appearance = Appearance.entries.firstOrNull { it.name == stored[AppearanceMode] }
                    ?: Appearance.SYSTEM,
            )
        }

    override suspend fun completeOnboarding() {
        dataStore.edit { stored -> stored[OnboardingCompleted] = true }
    }

    override suspend fun setAppearance(appearance: Appearance) {
        dataStore.edit { stored -> stored[AppearanceMode] = appearance.name }
    }

    private companion object {
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val AppearanceMode = stringPreferencesKey("appearance")
    }
}
