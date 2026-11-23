package pl.prawko.app.preview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.prawko.app.data.createCatalogPreview
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.data.local.RoomExamRepository
import pl.prawko.app.data.local.RoomStudyProgressRepository
import pl.prawko.app.data.media.LocalMediaRepository
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.domain.StudyQuestionProgress
import pl.prawko.app.domain.UserPreferences
import pl.prawko.app.ui.PrawkoUiState
import pl.prawko.app.ui.navigation.PrawkoApp
import pl.prawko.app.ui.navigation.PrawkoFeatures
import pl.prawko.app.ui.theme.PrawkoTheme

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class LocalCatalogNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectsDocumentStudiesAndBlocksExamWhenRequiredMediaAreUnavailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, PrawkoDatabase::class.java).build()
        val version = "DEMO-wybor-pliku"
        try {
            DemoCatalogDocuments(context.contentResolver).use { documents ->
                val uri = documents.create { write(demoCatalog(version).toByteArray()) }
                val picker = object : ActivityResultRegistryOwner {
                    override val activityResultRegistry = object : ActivityResultRegistry() {
                        override fun <I, O> onLaunch(
                            requestCode: Int,
                            contract: ActivityResultContract<I, O>,
                            input: I,
                            options: ActivityOptionsCompat?,
                        ) {
                            assertTrue(contract is ActivityResultContracts.OpenDocument)
                            assertEquals(listOf("application/json", "*/*"), (input as Array<*>).toList())
                            dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
                        }
                    }
                }
                val preview = createCatalogPreview(context) { database }
                val progress = RoomStudyProgressRepository(database)
                val features = PrawkoFeatures(
                    questionRepository = preview.questions,
                    studyProgressRepository = progress,
                    examRepository = RoomExamRepository(database),
                    mediaRepository = LocalMediaRepository(context),
                    isDemo = false,
                    installMediaPack = { _, _, _ -> error("No media pack for these questions") },
                    catalogPreviewSettings = preview.settings,
                )
                compose.setContent {
                    val activeVersion by preview.questions.activeDatasetVersion.collectAsStateWithLifecycle(initialValue = null)
                    PrawkoTheme {
                        CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                            PrawkoApp(
                                state = PrawkoUiState(
                                    preferences = UserPreferences(onboardingCompleted = true),
                                    questionAvailability = if (activeVersion == null) {
                                        QuestionAvailability.UNAVAILABLE
                                    } else {
                                        QuestionAvailability.AVAILABLE
                                    },
                                    datasetVersion = activeVersion,
                                ),
                                features = features,
                                onCompleteOnboarding = {},
                                onAppearanceChange = {},
                                onErrorShown = {},
                            )
                        }
                    }
                }

                compose.onNodeWithText("KATALOG TEST · Dane lokalne").assertIsDisplayed()
                compose.onNodeWithText("Ustawienia").performClick()
                compose.onNodeWithText("Brak wczytanego katalogu").assertIsDisplayed()
                compose.onNodeWithText("Wczytaj lokalny katalog (JSON)").performScrollTo().performClick()
                awaitText("Pytania w katalogu · 32").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Źródło DEMO · lokalny plik").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Wersja $version · kategoria B").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Start").performClick()
                compose.onNodeWithText("Nauka pytań").performScrollTo().performClick()
                awaitText("Rozpocznij naukę").performScrollTo().performClick()
                compose.onNodeWithText(DemoQuestionPrefix + "1").performScrollTo().assertIsDisplayed()
                awaitText("Brak wymaganego zdjęcia", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("TAK").performScrollTo().performClick()
                compose.onNodeWithText("Sprawdź odpowiedź").performClick()
                awaitText("Dobra odpowiedź").performScrollTo().assertIsDisplayed()
                assertEquals(StudyQuestionProgress(1, 1, true, false), runBlocking {
                    progress.observeProgress(version).first().getValue("1")
                })
                compose.onNodeWithText("Następne pytanie").performClick()
                awaitText("Brak wymaganego filmu", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription("Wróć").performClick()
                compose.onNodeWithText("Zakończ serię").performClick()
                compose.onNodeWithContentDescription("Wróć").performClick()

                compose.onNodeWithText("Zobacz egzamin").performScrollTo().performClick()
                awaitText("Za mało gotowych pytań").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Brakuje zdjęć lub filmów", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Rozpocznij egzamin").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithText("KATALOG TEST · Dane lokalne").assertIsDisplayed()
            }
        } finally {
            database.close()
        }
    }

    private fun awaitText(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.waitUntil(timeoutMillis = 45_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithText(text, substring = substring)
    }
}
