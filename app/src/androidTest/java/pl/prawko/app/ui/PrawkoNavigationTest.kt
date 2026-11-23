package pl.prawko.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import pl.prawko.app.data.DemoQuestionRepository
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.data.local.RoomExamRepository
import pl.prawko.app.data.local.RoomStudyProgressRepository
import pl.prawko.app.data.media.LocalMediaRepository
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.StudyQuestionProgress
import pl.prawko.app.domain.UserPreferences
import pl.prawko.app.domain.exam.ExamClock
import pl.prawko.app.domain.exam.ExamDrawResult
import pl.prawko.app.domain.exam.ExamEngine
import pl.prawko.app.domain.exam.ExamRules
import pl.prawko.app.ui.navigation.PrawkoApp
import pl.prawko.app.ui.navigation.PrawkoFeatures
import pl.prawko.app.ui.theme.PrawkoTheme

class PrawkoNavigationTest {
    private val compose = createComposeRule()
    private lateinit var database: PrawkoDatabase
    private val databaseRule = object : ExternalResource() {
        override fun before() {
            database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                PrawkoDatabase::class.java,
            ).build()
        }

        override fun after() = database.close()
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(databaseRule).around(compose)

    @Test
    fun completingOnboardingOpensDashboardAndExamExplainsMissingQuestions() {
        showApp(UserPreferences())

        compose.onNodeWithText("Przejdź do pulpitu").performScrollTo().performClick()
        compose.onNodeWithText("Zobacz egzamin").performScrollTo().performClick()
        awaitText("Brak bazy pytań").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("Wróć").performScrollTo().performClick()
        compose.onNodeWithText("Tryby").assertIsSelected()
        compose.onNodeWithText("Ustawienia").performClick()
        compose.onNodeWithText("Ciemny").performScrollTo().performClick().assertIsSelected()
    }

    @Test
    fun demoStudyPersistsProgressAndExamHistoryOpensMistakeReview() {
        seedDemoExam()
        val version = showApp(UserPreferences(onboardingCompleted = true), demo = true)!!
        compose.onNodeWithText("DEMO · Dane syntetyczne").assertIsDisplayed()
        compose.onNodeWithText("Nauka pytań").performScrollTo().performClick()
        awaitText("Rozpocznij naukę").performScrollTo().performClick()
        compose.onNodeWithText("NIE").performScrollTo().performClick()
        compose.onNodeWithText("Sprawdź odpowiedź").performClick()
        awaitText("Dobra odpowiedź").performScrollTo().assertIsDisplayed()

        val progress = runBlocking { RoomStudyProgressRepository(database).observeProgress(version).first() }
        assertEquals(StudyQuestionProgress(1, 1, true, false), progress.getValue("DEMO-B-1-0"))

        compose.onNodeWithContentDescription("Wróć").performClick()
        compose.onNodeWithText("Zakończ serię").performClick()
        compose.onNodeWithText("Zobacz statystyki").performScrollTo().performClick()
        awaitText("Przerobione pytania").assertIsDisplayed()
        compose.onNodeWithText("Historia").performClick()
        awaitText("0 / 74 pkt", substring = true).performClick()
        compose.onNodeWithText("Wynik egzaminu").assertIsDisplayed()
        awaitText("Przećwicz błędy").performScrollTo().performClick()
        awaitText("Powtórka błędów").assertIsDisplayed()
    }

    private fun awaitText(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        try {
            compose.waitUntil(timeoutMillis = 45_000) {
                compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: AssertionError) {
            compose.onRoot().printToLog("PrawkoNavigationTest")
            throw error
        }
        return compose.onNodeWithText(text, substring = substring)
    }

    private fun seedDemoExam() = runBlocking {
        val catalog = DemoQuestionRepository()
        val version = catalog.activeDatasetVersion.first()
        val selected = ExamEngine.draw(
            catalog.observeQuestions(version).first(), version, LicenseCategory.B, { true },
        ) as ExamDrawResult.Ready
        val clock = ExamClock(0, "DEMO-boot", 1_700_000_000_000)
        val started = ExamEngine.start("DEMO-history", version, LicenseCategory.B, selected.questions, clock)
        val result = ExamEngine.reconcile(started, clock.copy(elapsedRealtimeMs = ExamRules.TOTAL_TIME_MS))
        val exams = RoomExamRepository(database)
        assertTrue(exams.start(started))
        assertTrue(exams.save(started, result))
    }

    private fun showApp(initialPreferences: UserPreferences, demo: Boolean = false): String? {
        val repository = if (demo) DemoQuestionRepository() else EmptyQuestionRepository
        val version = runBlocking { repository.activeDatasetVersion.first() }
        val availability = runBlocking { repository.availability.first() }
        val features = PrawkoFeatures(
            questionRepository = repository,
            studyProgressRepository = RoomStudyProgressRepository(database),
            examRepository = RoomExamRepository(database),
            mediaRepository = LocalMediaRepository(ApplicationProvider.getApplicationContext()),
            isDemo = demo,
            installMediaPack = { _, _, _ -> error("No pack is selected in this test") },
        )
        val preferences = mutableStateOf(initialPreferences)
        compose.setContent {
            PrawkoTheme {
                PrawkoApp(
                    state = PrawkoUiState(
                        preferences = preferences.value,
                        questionAvailability = availability,
                        datasetVersion = version,
                    ),
                    features = features,
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
        return version
    }

    private object EmptyQuestionRepository : QuestionRepository {
        override val activeDatasetVersion = flowOf<String?>(null)
        override val availability = flowOf(QuestionAvailability.UNAVAILABLE)
        override fun observeQuestions(datasetVersion: String) = flowOf(emptyList<Question>())
    }
}
