package pl.prawko.app.domain.exam

import kotlin.random.Random
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.availableChoices
import pl.prawko.app.domain.correctChoice

object ExamRules {
    const val QUESTION_COUNT = 32
    const val MAX_SCORE = 74
    const val PASS_SCORE = 68
    const val TOTAL_TIME_MS = 25 * 60 * 1_000L
    const val BASIC_READING_TIME_MS = 20_000L
    const val BASIC_ANSWER_TIME_MS = 15_000L
    const val SPECIALIST_TIME_MS = 50_000L
}

data class ExamShortage(
    val type: QuestionType,
    val points: Int,
    val required: Int,
    val available: Int,
    val unavailableMedia: Int,
) {
    val missing: Int
        get() = required - available
}

sealed interface ExamDrawResult {
    data class Ready(val questions: List<ExamQuestion>) : ExamDrawResult
    data class Insufficient(val shortages: List<ExamShortage>) : ExamDrawResult
}

private data class ExamQuota(val type: QuestionType, val points: Int, val count: Int)

private val Quotas = listOf(
    ExamQuota(QuestionType.BASIC, 3, 10),
    ExamQuota(QuestionType.BASIC, 2, 6),
    ExamQuota(QuestionType.BASIC, 1, 4),
    ExamQuota(QuestionType.SPECIALIST, 3, 6),
    ExamQuota(QuestionType.SPECIALIST, 2, 4),
    ExamQuota(QuestionType.SPECIALIST, 1, 2),
)

object ExamEngine {
    /** Draws from the supplied active catalog using the caller's local media verification. */
    fun draw(
        questions: List<Question>,
        datasetVersion: String,
        category: LicenseCategory,
        playable: (Question) -> Boolean,
        random: Random = Random.Default,
    ): ExamDrawResult {
        val candidates = questions.asSequence()
            .filter { it.datasetVersion == datasetVersion && category.name in it.categories }
            .filter { it.answers.correctChoice in it.type.availableChoices }
            .distinctBy { it.id }
            .groupBy { it.type to it.points }
        val selected = mutableListOf<ExamQuestion>()
        val shortages = mutableListOf<ExamShortage>()
        for (quota in Quotas) {
            val bucket = candidates[quota.type to quota.points].orEmpty()
            val eligible = bucket.filter { question ->
                question.mediaReferences.none { it.role == QuestionMediaRole.QUESTION && it.assetPath == null } &&
                    playable(question)
            }
            if (eligible.size < quota.count) {
                shortages += ExamShortage(
                    type = quota.type,
                    points = quota.points,
                    required = quota.count,
                    available = eligible.size,
                    unavailableMedia = bucket.size - eligible.size,
                )
            } else {
                selected += eligible.shuffled(random).take(quota.count).map { question ->
                    ExamQuestion(question.id, question.type, question.points, question.answers.correctChoice)
                }
            }
        }
        if (shortages.isNotEmpty()) return ExamDrawResult.Insufficient(shortages)
        return ExamDrawResult.Ready(
            selected.groupBy { it.type }.values.flatMap { questionsOfType -> questionsOfType.shuffled(random) },
        )
    }

    fun start(
        sessionId: String,
        datasetVersion: String,
        category: LicenseCategory,
        questions: List<ExamQuestion>,
        clock: ExamClock,
    ): ExamSession {
        require(sessionId.isNotBlank() && datasetVersion.isNotBlank() && clock.bootId.isNotBlank())
        require(clock.elapsedRealtimeMs >= 0)
        require(questions.size == ExamRules.QUESTION_COUNT && questions.distinctBy { it.questionId }.size == questions.size)
        require(Quotas.all { quota -> questions.count { it.type == quota.type && it.points == quota.points } == quota.count })
        require(questions.take(20).all { it.type == QuestionType.BASIC })
        return ExamSession(
            id = sessionId,
            datasetVersion = datasetVersion,
            category = category,
            bootId = clock.bootId,
            startedAtElapsedMs = clock.elapsedRealtimeMs,
            startedAtEpochMs = clock.epochMillis,
            questionStartedAtElapsedMs = clock.elapsedRealtimeMs,
            questions = questions.toList(),
        )
    }

    /**
     * Resolves all missed deadlines at their original elapsed times. Reopening the app
     * cannot extend either the current question or the overall attempt.
     */
    fun reconcile(session: ExamSession, clock: ExamClock): ExamSession {
        if (session.completion != null) return session
        if (!session.isSameBoot(clock)) {
            return session.finish(
                reason = ExamCompletion.DEVICE_RESTARTED,
                atEpochMs = maxOf(clock.epochMillis, session.startedAtEpochMs),
            )
        }

        var state = session
        while (state.currentQuestion != null) {
            val cutoff = minOf(state.answerDeadline, state.deadline)
            if (clock.elapsedRealtimeMs < cutoff) break

            state = state.recordCurrentAnswer(cutoff)
            if (cutoff >= state.deadline) {
                return state.finish(ExamCompletion.TIME_EXPIRED, state.epochAt(cutoff))
            }
            if (state.currentIndex == state.questions.size) {
                return state.finish(ExamCompletion.COMPLETED, state.epochAt(cutoff))
            }
        }
        return state
    }

    fun choose(
        session: ExamSession,
        questionId: String,
        choice: AnswerChoice,
        clock: ExamClock,
    ): ExamSession {
        val state = reconcile(session, clock)
        val question = state.currentQuestion ?: return state
        if (question.questionId != questionId || state.phase(clock) != ExamPhase.ANSWERING) return state
        if (choice !in question.type.availableChoices) return state
        return state.copy(selection = choice)
    }

    fun submit(session: ExamSession, questionId: String, clock: ExamClock): ExamSession {
        val state = reconcile(session, clock)
        if (state.currentQuestion?.questionId != questionId || state.phase(clock) != ExamPhase.ANSWERING) return state
        val advanced = state.recordCurrentAnswer(clock.elapsedRealtimeMs)
        return if (advanced.currentIndex == advanced.questions.size) {
            advanced.finish(ExamCompletion.COMPLETED, advanced.epochAt(clock.elapsedRealtimeMs))
        } else {
            advanced
        }
    }

    fun interrupt(session: ExamSession, reason: ExamCompletion, clock: ExamClock): ExamSession {
        require(reason == ExamCompletion.MEDIA_UNAVAILABLE || reason == ExamCompletion.ABANDONED)
        val state = reconcile(session, clock)
        if (state.completion != null) return state
        return state.finish(reason, state.epochAt(clock.elapsedRealtimeMs))
    }
}

private fun ExamSession.recordCurrentAnswer(advanceAt: Long): ExamSession {
    val question = requireNotNull(currentQuestion)
    return copy(
        answers = answers + ExamAnswer(question.questionId, selection),
        selection = null,
        questionStartedAtElapsedMs = advanceAt,
    )
}

private fun ExamSession.epochAt(elapsed: Long): Long = startedAtEpochMs + elapsed - startedAtElapsedMs

private fun ExamSession.finish(reason: ExamCompletion, atEpochMs: Long): ExamSession = copy(
    answers = answers + questions.drop(currentIndex).map { ExamAnswer(it.questionId, null) },
    selection = null,
    completion = reason,
    completedAtEpochMs = atEpochMs,
)
