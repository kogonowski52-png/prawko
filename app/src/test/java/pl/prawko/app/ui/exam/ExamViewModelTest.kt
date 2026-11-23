package pl.prawko.app.ui.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.LocalMedia
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.domain.QuestionMediaReference
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionMediaType
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.availableChoices
import pl.prawko.app.domain.exam.ExamClock
import pl.prawko.app.domain.exam.ExamCompletion
import pl.prawko.app.domain.exam.ExamHistoryItem
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamRules
import pl.prawko.app.domain.exam.ExamSession

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelTest {
    @Test
    fun completesAllQuestionsAndPersistsResultAfterRecreatingTheViewModel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = SessionStore()
        val catalog = DemoCatalog(demoQuestions())
        val time = MutableClock()
        var model = model(catalog, store, time)
        try {
            runCurrent()
            assertTrue((model.state.value as ExamScreenState.Intro).shortages.isEmpty())
            model.start()
            runCurrent()

            repeat(ExamRules.QUESTION_COUNT) { number ->
                val running = model.state.value as ExamScreenState.Running
                val question = requireNotNull(running.session.currentQuestion)
                if (question.type == QuestionType.BASIC) time.elapsed += ExamRules.BASIC_READING_TIME_MS
                time.elapsed += 1_000L
                val nextChoice = if (number == 0) {
                    question.type.availableChoices.first { it != question.correctChoice }
                } else {
                    question.correctChoice
                }
                model.choose(question.questionId, nextChoice)
                model.submit(question.questionId)
                runCurrent()

                if (number == 0) {
                    model.viewModelScope.cancel()
                    time.elapsed += 2_000L
                    model = model(catalog, store, time)
                    runCurrent()
                    val resumed = model.state.value as ExamScreenState.Running
                    assertEquals(1, resumed.session.currentIndex)
                    assertEquals(nextChoice, resumed.session.answers.single().choice)
                    assertEquals(2_000L, resumed.clock.elapsedRealtimeMs - resumed.session.questionStartedAtElapsedMs)
                    assertTrue(resumed.session.remainingMs(resumed.clock) < ExamRules.TOTAL_TIME_MS)
                }
            }

            val result = model.state.value as ExamScreenState.Result
            val stored = store.sessions.value.getValue(result.sessionId)
            assertEquals(ExamCompletion.COMPLETED, stored.completion)
            assertEquals(32, stored.answers.size)
            assertEquals(1, stored.mistakes.size)
            assertTrue(stored.passed)
            assertEquals(ExamRules.MAX_SCORE - stored.questions.first().points, stored.score)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reportsMissingMediaAndNeverReservesAnExamSlot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val original = demoQuestions()
        val first = original.first().copy(
            mediaReferences = listOf(
                QuestionMediaReference(QuestionMediaRole.QUESTION, "DEMO.wmv", null, QuestionMediaType.VIDEO),
            ),
        )
        val store = SessionStore()
        val model = model(DemoCatalog(listOf(first) + original.drop(1)), store, MutableClock())
        try {
            runCurrent()
            val intro = model.state.value as ExamScreenState.Intro
            assertEquals(1, intro.shortages.single().unavailableMedia)
            assertEquals(1, intro.shortages.single().missing)
            model.start()
            runCurrent()
            assertTrue(store.sessions.value.isEmpty())
            assertEquals(intro, model.state.value)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun model(catalog: QuestionRepository, store: ExamRepository, time: ExamTimeSource) =
        ExamViewModel(catalog, store, NoMedia, LicenseCategory.B, time, SavedStateHandle())

    private class DemoCatalog(private val questions: List<Question>) : QuestionRepository {
        override val activeDatasetVersion = flowOf(Version)
        override val availability = flowOf(QuestionAvailability.AVAILABLE)
        override fun observeQuestions(datasetVersion: String) =
            flowOf(if (datasetVersion == Version) questions else emptyList())
    }

    private object NoMedia : MediaRepository {
        override suspend fun resolve(datasetVersion: String, reference: QuestionMediaReference): LocalMedia? = null
    }

    private class MutableClock(var elapsed: Long = 0L) : ExamTimeSource {
        override fun now() = ExamClock(elapsed, "DEMO-boot-id", 5_000_000L + elapsed)
    }

    private class SessionStore : ExamRepository {
        val sessions = MutableStateFlow<Map<String, ExamSession>>(emptyMap())
        override val activeSession = sessions.map { it.values.singleOrNull { entry -> entry.completion == null } }
        override fun observeSession(sessionId: String) = sessions.map { it[sessionId] }
        override fun observeResults() = sessions.map { stored ->
            stored.values.mapNotNull { session ->
                session.completedAtEpochMs?.let { ExamHistoryItem(session, it) }
            }
        }

        override suspend fun start(session: ExamSession): Boolean {
            if (sessions.value.values.any { it.completion == null }) return false
            sessions.value += session.id to session
            return true
        }

        override suspend fun save(previous: ExamSession, session: ExamSession): Boolean {
            if (sessions.value[previous.id] != previous) return false
            sessions.value += session.id to session
            return true
        }
    }

    private fun demoQuestions() = buildList {
        val quotas = mapOf(QuestionType.BASIC to listOf(10, 6, 4), QuestionType.SPECIALIST to listOf(6, 4, 2))
        quotas.forEach { (type, counts) ->
            counts.forEachIndexed { bucket, count ->
                repeat(count) { index ->
                    add(
                        Question(
                            id = "DEMO-${type.name}-$bucket-$index",
                            datasetVersion = Version,
                            category = LicenseCategory.B,
                            prompt = "DEMO · pytanie typu ${type.name}, pozycja $index",
                            points = 3 - bucket,
                            answers = when (type) {
                                QuestionType.BASIC -> QuestionAnswers.YesNo(true)
                                QuestionType.SPECIALIST -> QuestionAnswers.MultipleChoice(
                                    "DEMO A", "DEMO B", "DEMO C", QuestionAnswers.Choice.A,
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        const val Version = "DEMO-test-exam"
    }
}
