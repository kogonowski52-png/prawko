package pl.prawko.app.ui.exam

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.prawko.app.data.DemoQuestionRepository
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.LocalMedia
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionMediaReference
import pl.prawko.app.domain.exam.ExamClock
import pl.prawko.app.domain.exam.ExamDrawResult
import pl.prawko.app.domain.exam.ExamEngine
import pl.prawko.app.domain.exam.ExamSession
import pl.prawko.app.ui.components.DemoBanner
import pl.prawko.app.ui.theme.PrawkoTheme

class RunningExamTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun phasesAndActionsRemainUsableAtThreeWidthsWithLargeFonts() {
        val (initial, questions) = demoExam()
        var layout by mutableStateOf(ExamLayout(360, 1.3f, dark = true))
        var state by mutableStateOf(initial.runningAt(10_000, questions))
        var chosen: Pair<String, AnswerChoice>? = null
        val submitted = mutableListOf<String>()
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                CompositionLocalProvider(LocalDensity provides Density(widthPx / layout.widthDp, layout.fontScale)) {
                    PrawkoTheme(darkTheme = layout.dark) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = { DemoBanner() },
                            contentWindowInsets = WindowInsets.safeDrawing,
                        ) { padding ->
                            ExamContent(
                                state = state,
                                mediaRepository = EmptyMedia,
                                category = LicenseCategory.B,
                                isDemo = true,
                                onBack = {},
                                onRetry = {},
                                onStart = {},
                                onChoose = { id, answer -> chosen = id to answer },
                                onSubmit = { id -> submitted.add(id) },
                                onAbandon = {},
                                onMediaUnavailable = {},
                                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithText("Pytanie 1 z 32").assertIsDisplayed()
        val progress = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pytanie 1 z 32"),
        ).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(1f / 32f, progress.current, 0.0001f)
        compose.onNodeWithContentDescription("Czas egzaminu 24:50").assertIsDisplayed()
        compose.onNodeWithContentDescription("Odpowiedzi za 00:10").assertIsDisplayed()
        compose.onNodeWithText("TAK").assertDoesNotExist()
        compose.onNodeWithText("NIE").assertDoesNotExist()
        compose.onNodeWithText("Dalej bez odpowiedzi").assertDoesNotExist()
        saveScreenshot("exam-dark-360-reading-font-13")

        compose.runOnIdle { state = initial.runningAt(19_999, questions) }
        compose.onNodeWithContentDescription("Odpowiedzi za 00:01").assertIsDisplayed()
        compose.onNodeWithText("TAK").assertDoesNotExist()
        compose.runOnIdle { state = initial.runningAt(20_000, questions) }
        compose.onNodeWithContentDescription("Czas egzaminu 24:40").assertIsDisplayed()
        compose.onNodeWithContentDescription("Na odpowiedź 00:15").assertIsDisplayed()
        compose.onNodeWithText("NIE").assertIsEnabled()
        compose.onNodeWithText("Dalej bez odpowiedzi").assertIsDisplayed().performClick()
        compose.onNodeWithText("Pominąć pytanie?").assertIsDisplayed()
        compose.onNodeWithText("Wybierz odpowiedź").performClick()
        compose.runOnIdle { assertTrue(submitted.isEmpty()) }

        compose.onNodeWithText("TAK").performClick().assertIsSelected()
        compose.onNodeWithText("Zatwierdź i dalej").assertIsNotEnabled()
        val firstId = requireNotNull(initial.currentQuestion).questionId
        compose.runOnIdle {
            assertEquals(firstId to AnswerChoice.YES, chosen)
            state = ExamEngine.choose(initial, firstId, AnswerChoice.YES, clockAt(20_000))
                .runningAt(20_000, questions)
        }
        compose.onNodeWithText("Zatwierdź i dalej").assertIsDisplayed().assertIsEnabled()
        saveScreenshot("exam-dark-360-answer-font-13")
        compose.onNodeWithText("Zatwierdź i dalej").performClick().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf(firstId), submitted)
            state = ExamEngine.submit(state.session, firstId, clockAt(20_000)).runningAt(20_000, questions)
        }
        compose.onNodeWithText("Pytanie 2 z 32").assertIsDisplayed()
        compose.onNodeWithText("TAK").assertDoesNotExist()

        val specialist = ExamEngine.reconcile(initial, clockAt(700_000))
        val specialistId = requireNotNull(specialist.currentQuestion).questionId
        val longQuestion = questions.getValue(specialistId).copy(
            prompt = "DEMO · Przeczytaj trzy dłuższe odpowiedzi i wybierz jedną, aby sprawdzić układ.",
            answers = QuestionAnswers.MultipleChoice(
                optionA = "Odpowiedź testowa ze zdaniem zajmującym więcej miejsca na ekranie.",
                optionB = "Odpowiedź testowa z dodatkowym tekstem do sprawdzenia powiększonej czcionki.",
                optionC = "Ostatnia odpowiedź testowa dostępna po przewinięciu, jeśli ekran jest mały.",
                correct = QuestionAnswers.Choice.C,
            ),
        )
        val expandedQuestions = questions + (specialistId to longQuestion)
        compose.runOnIdle {
            layout = ExamLayout(393, 1.5f, dark = true)
            state = specialist.runningAt(700_000, expandedQuestions)
        }
        compose.onNodeWithContentDescription("Na odpowiedź 00:50").assertIsDisplayed()
        compose.onNodeWithText("Dalej bez odpowiedzi").assertIsDisplayed()
        compose.onNodeWithText("C").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(specialistId to AnswerChoice.C, chosen)
            state = ExamEngine.choose(specialist, specialistId, AnswerChoice.C, clockAt(700_000))
                .runningAt(700_000, expandedQuestions)
        }
        compose.onNodeWithText("Zatwierdź i dalej").assertIsDisplayed().assertIsEnabled()
        saveScreenshot("exam-dark-393-specialist-font-15")

        compose.runOnIdle { layout = ExamLayout(432, 1.3f, dark = false) }
        compose.onNodeWithContentDescription("Opuść egzamin").assertIsDisplayed()
        compose.onNodeWithText("Pytanie 21 z 32").assertIsDisplayed()
        compose.onNodeWithText("Zatwierdź i dalej").assertIsDisplayed().assertIsEnabled()
        saveScreenshot("exam-light-432-specialist-font-13")
    }

    private fun saveScreenshot(name: String) {
        if (Build.VERSION.SDK_INT < 26) return
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val folder = File(path, "stage3a")
        check(folder.isDirectory || folder.mkdirs())
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { output ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun demoExam(): Pair<ExamSession, Map<String, Question>> = runBlocking {
        val catalog = DemoQuestionRepository()
        val version = catalog.activeDatasetVersion.first()
        val questions = catalog.observeQuestions(version).first()
        val deck = ExamEngine.draw(questions, version, LicenseCategory.B, { true }, Random(21)) as ExamDrawResult.Ready
        val session = ExamEngine.start("DEMO-stage3a", version, LicenseCategory.B, deck.questions, clockAt(0))
        session to questions.associateBy(Question::id)
    }

    private fun ExamSession.runningAt(elapsedMs: Long, questions: Map<String, Question>) =
        ExamScreenState.Running(this, questions.getValue(requireNotNull(currentQuestion).questionId), clockAt(elapsedMs))

    private fun clockAt(elapsedMs: Long) = ExamClock(elapsedMs, "DEMO-stage3a", 1_700_000_000_000 + elapsedMs)

    private data class ExamLayout(val widthDp: Int, val fontScale: Float, val dark: Boolean)

    private object EmptyMedia : MediaRepository {
        override suspend fun resolve(datasetVersion: String, reference: QuestionMediaReference): LocalMedia? = null
    }
}
