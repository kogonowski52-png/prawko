package pl.prawko.app.domain.exam

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionMediaReference
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionMediaType
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.availableChoices

class ExamEngineTest {
    @Test
    fun drawsExactSectionsAndPointQuotasFromActiveCategoryForEverySeed() {
        val extra = testQuestion("DEMO-extra", QuestionType.BASIC, 3)
        val candidates = pool() + listOf(
            extra,
            extra,
            testQuestion("DEMO-older", QuestionType.BASIC, 3).copy(datasetVersion = "DEMO-2026-older"),
            testQuestion("DEMO-B1", QuestionType.BASIC, 3).copy(categories = setOf("B1")),
        )

        repeat(10) { seed ->
            val picked = (draw(candidates, seed = seed) as ExamDrawResult.Ready).questions

            assertEquals(32, picked.size)
            assertEquals(32, picked.map { it.questionId }.toSet().size)
            assertEquals(74, picked.sumOf { it.points })
            assertTrue(picked.take(20).all { it.type == QuestionType.BASIC })
            assertTrue(picked.drop(20).all { it.type == QuestionType.SPECIALIST })
            assertEquals(
                mapOf(
                    (QuestionType.BASIC to 3) to 10,
                    (QuestionType.BASIC to 2) to 6,
                    (QuestionType.BASIC to 1) to 4,
                    (QuestionType.SPECIALIST to 3) to 6,
                    (QuestionType.SPECIALIST to 2) to 4,
                    (QuestionType.SPECIALIST to 1) to 2,
                ),
                picked.groupingBy { it.type to it.points }.eachCount(),
            )
            assertFalse(picked.any { it.questionId in setOf("DEMO-older", "DEMO-B1") })
        }
    }

    @Test
    fun blocksStartWhenRequiredMediaIsUnmappedOrFailsLocalMediaVerification() {
        val missingPath = pool().first().copy(mediaReferences = listOf(video(path = null)))
        val missingFile = pool()[1].copy(mediaReferences = listOf(video(path = "media/demo.mp4")))
        val optionalPjm = pool()[2].copy(
            mediaReferences = listOf(video(path = null).copy(role = QuestionMediaRole.PJM_QUESTION)),
        )
        val candidates = listOf(missingPath, missingFile, optionalPjm) + pool().drop(3) +
            testQuestion("DEMO-extra-specialist", QuestionType.SPECIALIST, 3)

        val result = draw(candidates, playable = { it.id != missingFile.id }) as ExamDrawResult.Insufficient

        assertEquals(listOf(ExamShortage(QuestionType.BASIC, 3, 10, 8, 2)), result.shortages)
        assertEquals(2, result.shortages.single().missing)
    }

    @Test
    fun reportsEveryMissingBucketWithoutCountingRepeatedQuestionIds() {
        val result = draw(List(40) { pool().first() }) as ExamDrawResult.Insufficient

        assertEquals(6, result.shortages.size)
        assertEquals(1, result.shortages.first().available)
        assertEquals(9, result.shortages.first().missing)
        assertEquals(31, result.shortages.sumOf { it.missing })
    }

    @Test
    fun readingLocksChoicesAndBasicQuestionBecomesAnswerableAtTwentySeconds() {
        val started = start()
        val firstId = started.currentQuestion!!.questionId

        assertEquals(ExamPhase.READING, started.phase(clock(0)))
        assertEquals(20_000L, started.phaseRemainingMs(clock(0)))
        assertEquals(started, ExamEngine.choose(started, firstId, AnswerChoice.YES, clock(19_999)))
        assertEquals(started, ExamEngine.submit(started, firstId, clock(19_999)))
        assertEquals(ExamPhase.ANSWERING, started.phase(clock(20_000)))
        assertEquals(15_000L, started.phaseRemainingMs(clock(20_000)))
        assertEquals(started, ExamEngine.choose(started, firstId, AnswerChoice.A, clock(20_000)))

        val chosen = ExamEngine.choose(started, firstId, AnswerChoice.YES, clock(20_000))
        val changed = ExamEngine.choose(chosen, firstId, AnswerChoice.NO, clock(34_999))
        val next = ExamEngine.submit(changed, firstId, clock(34_999))

        assertEquals(listOf(ExamAnswer(firstId, AnswerChoice.NO)), next.answers)
        assertNull(next.selection)
        assertEquals(ExamPhase.READING, next.phase(clock(34_999)))
        assertEquals(next, ExamEngine.choose(next, firstId, AnswerChoice.YES, clock(60_000)))
        assertEquals(next, ExamEngine.submit(next, firstId, clock(60_000)))
    }

    @Test
    fun accountsForMissedWindowsAtTheirOriginalDeadlinesAfterProcessRestoration() {
        val started = start()
        val first = started.currentQuestion!!
        val chosen = ExamEngine.choose(started, first.questionId, first.correctChoice, clock(25_000))
        val resumed = ExamEngine.reconcile(chosen, clock(107_000, epochMs = 0))

        assertEquals(3, resumed.currentIndex)
        assertEquals(first.correctChoice, resumed.answers[0].choice)
        assertNull(resumed.answers[1].choice)
        assertNull(resumed.answers[2].choice)
        assertEquals(first.points, resumed.score)
        assertEquals(105_000L, resumed.questionStartedAtElapsedMs)
        assertEquals(18_000L, resumed.phaseRemainingMs(clock(107_000)))
        assertEquals(1_393_000L, resumed.remainingMs(clock(107_000)))
        assertEquals(resumed, ExamEngine.reconcile(resumed, clock(107_000, epochMs = 1)))
    }

    @Test
    fun answerAtAnExpiredDeadlineCannotOverridePreviouslySelectedChoice() {
        val started = start()
        val firstId = started.currentQuestion!!.questionId
        val chosen = ExamEngine.choose(started, firstId, AnswerChoice.YES, clock(20_000))

        val advanced = ExamEngine.choose(chosen, firstId, AnswerChoice.NO, clock(35_000))

        assertEquals(listOf(ExamAnswer(firstId, AnswerChoice.YES)), advanced.answers)
        assertEquals(35_000L, advanced.questionStartedAtElapsedMs)
        assertNull(advanced.selection)
    }

    @Test
    fun specialistQuestionHasOneFiftySecondAnswerWindow() {
        val state = ExamEngine.reconcile(start(), clock(700_000))
        val current = state.currentQuestion!!

        assertEquals(20, state.currentIndex)
        assertEquals(QuestionType.SPECIALIST, current.type)
        assertEquals(ExamPhase.ANSWERING, state.phase(clock(700_000)))
        assertEquals(50_000L, state.phaseRemainingMs(clock(700_000)))

        val chosen = ExamEngine.choose(state, current.questionId, current.correctChoice, clock(749_999))
        val advanced = ExamEngine.reconcile(chosen, clock(750_000))

        assertEquals(21, advanced.currentIndex)
        assertEquals(current.correctChoice, advanced.answers.last().choice)
        assertEquals(current.points, advanced.score)
    }

    @Test
    fun globalDeadlineTruncatesQuestionsAndLeavesRemainingAnswersBlank() {
        val restored = start().copy(questionStartedAtElapsedMs = ExamRules.TOTAL_TIME_MS - 10_000)
        val oneMillisecondLeft = clock(ExamRules.TOTAL_TIME_MS - 1)

        assertEquals(1L, restored.phaseRemainingMs(oneMillisecondLeft))
        assertEquals(1L, restored.remainingMs(oneMillisecondLeft))

        val finished = ExamEngine.reconcile(restored, clock(ExamRules.TOTAL_TIME_MS))

        assertEquals(ExamCompletion.TIME_EXPIRED, finished.completion)
        assertEquals(32, finished.answers.size)
        assertEquals(32, finished.mistakes.size)
        assertEquals(0, finished.score)
        assertEquals(Epoch.start + ExamRules.TOTAL_TIME_MS, finished.completedAtEpochMs)
        assertEquals(0L, finished.remainingMs(clock(ExamRules.TOTAL_TIME_MS + 1)))
    }

    @Test
    fun scoringHasAnInclusiveSixtyEightPointThresholdAndMaximumOfSeventyFour() {
        val started = start()
        val triples = started.questions.filter { it.points == 3 }.take(2).map { it.questionId }
        val single = started.questions.first { it.points == 1 }.questionId

        val perfect = finishWithMistakes(started, emptySet())
        val exactlyPassing = finishWithMistakes(started, triples.toSet())
        val failing = finishWithMistakes(started, (triples + single).toSet())

        assertEquals(74, perfect.score)
        assertEquals(ExamCompletion.COMPLETED, perfect.completion)
        assertTrue(perfect.passed)
        assertTrue(perfect.mistakes.isEmpty())
        assertEquals(68, exactlyPassing.score)
        assertTrue(exactlyPassing.passed)
        assertEquals(triples.toSet(), exactlyPassing.mistakes.map { it.questionId }.toSet())
        assertEquals(67, failing.score)
        assertFalse(failing.passed)
        assertEquals(3, failing.mistakes.size)
    }

    @Test
    fun changedBootNeverResetsTimeOrAwardsPendingChoice() {
        val started = start()
        val first = started.currentQuestion!!
        val chosen = ExamEngine.choose(started, first.questionId, first.correctChoice, clock(20_000))
        val advanced = ExamEngine.submit(chosen, first.questionId, clock(20_000))
        val second = advanced.currentQuestion!!
        val pending = ExamEngine.choose(advanced, second.questionId, second.correctChoice, clock(40_000))

        val restarted = ExamEngine.reconcile(pending, clock(50_000, boot = "DEMO-restarted"))

        assertEquals(ExamCompletion.DEVICE_RESTARTED, restarted.completion)
        assertEquals(0L, restarted.remainingMs(clock(50_000, boot = "DEMO-restarted")))
        assertEquals(32, restarted.answers.size)
        assertEquals(first.correctChoice, restarted.answers.first().choice)
        assertNull(restarted.answers[1].choice)
        assertEquals(first.points, restarted.score)
        assertFalse(restarted.passed)
        assertEquals(restarted, ExamEngine.reconcile(restarted, clock(99_999)))
    }

    @Test
    fun mediaFailureEndsAttemptWithoutConvertingPartialScoreToPass() {
        val started = start()
        var state = started
        var elapsed = 0L
        repeat(31) {
            val question = state.currentQuestion!!
            if (question.type == QuestionType.BASIC) elapsed += 20_000
            state = ExamEngine.choose(state, question.questionId, question.correctChoice, clock(elapsed))
            state = ExamEngine.submit(state, question.questionId, clock(elapsed))
        }
        assertTrue(state.score >= 68)
        assertNotNull(state.currentQuestion)

        val interrupted = ExamEngine.interrupt(state, ExamCompletion.MEDIA_UNAVAILABLE, clock(elapsed))

        assertEquals(ExamCompletion.MEDIA_UNAVAILABLE, interrupted.completion)
        assertEquals(state.score, interrupted.score)
        assertNull(interrupted.answers.last().choice)
        assertFalse(interrupted.passed)
    }

    private fun finishWithMistakes(started: ExamSession, mistakes: Set<String>): ExamSession {
        var state = started
        var now = 0L
        for (question in state.questions) {
            if (question.type == QuestionType.BASIC) now += 20_000
            val answer = if (question.questionId in mistakes) {
                question.type.availableChoices.first { it != question.correctChoice }
            } else {
                question.correctChoice
            }
            state = ExamEngine.choose(state, question.questionId, answer, clock(now))
            state = ExamEngine.submit(state, question.questionId, clock(now))
        }
        return state
    }

    private fun start(): ExamSession = ExamEngine.start(
        sessionId = "DEMO-session",
        datasetVersion = Version,
        category = LicenseCategory.B,
        questions = (draw(pool()) as ExamDrawResult.Ready).questions,
        clock = clock(0),
    )

    private fun draw(
        questions: List<Question>,
        seed: Int = 11,
        playable: (Question) -> Boolean = { true },
    ): ExamDrawResult = ExamEngine.draw(questions, Version, LicenseCategory.B, playable, Random(seed))

    private fun pool(): List<Question> = buildList {
        for ((type, counts) in listOf(
            QuestionType.BASIC to listOf(3 to 10, 2 to 6, 1 to 4),
            QuestionType.SPECIALIST to listOf(3 to 6, 2 to 4, 1 to 2),
        )) {
            for ((points, count) in counts) {
                repeat(count) { add(testQuestion("DEMO-${type.name}-$points-$it", type, points)) }
            }
        }
    }

    private fun testQuestion(id: String, type: QuestionType, points: Int): Question = Question(
        id = id,
        datasetVersion = Version,
        category = LicenseCategory.B,
        prompt = "DEMO — pytanie techniczne $id",
        points = points,
        answers = when (type) {
            QuestionType.BASIC -> QuestionAnswers.YesNo(correctIsYes = true)
            QuestionType.SPECIALIST -> QuestionAnswers.MultipleChoice(
                "DEMO A", "DEMO B", "DEMO C", QuestionAnswers.Choice.B,
            )
        },
    )

    private fun video(path: String?): QuestionMediaReference = QuestionMediaReference(
        role = QuestionMediaRole.QUESTION,
        originalName = "DEMO.wmv",
        assetPath = path,
        type = QuestionMediaType.VIDEO,
    )

    private fun clock(elapsed: Long, boot: String = "DEMO-boot", epochMs: Long = Epoch.start + elapsed) =
        ExamClock(elapsed, boot, epochMs)

    private object Epoch {
        const val start = 1_000_000L
    }

    private companion object {
        const val Version = "DEMO-2026-07-B"
    }
}
