package pl.prawko.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.prawko.app.domain.Appearance
import pl.prawko.app.domain.UserPreferences

class DataStorePreferencesRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun onboardingAndAppearanceSurviveReopeningPreferences() = runTest {
        val file = File(folder.root, "prawko.preferences_pb")
        val firstStoreJob = Job()
        val firstStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(coroutineContext + firstStoreJob),
            produceFile = { file },
        )

        try {
            val repository = DataStorePreferencesRepository(firstStore)
            assertEquals(UserPreferences(), repository.preferences.first())
            repository.completeOnboarding()
            repository.setAppearance(Appearance.DARK)
        } finally {
            firstStoreJob.cancelAndJoin()
        }

        val reopenedStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val reopenedRepository = DataStorePreferencesRepository(reopenedStore)
        assertEquals(
            UserPreferences(onboardingCompleted = true, appearance = Appearance.DARK),
            reopenedRepository.preferences.first(),
        )
    }
}
