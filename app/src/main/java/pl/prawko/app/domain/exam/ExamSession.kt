package pl.prawko.app.domain.exam

import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.availableChoices

data class ExamClock(
    val elapsedRealtimeMs: Long,
    val bootId: String,
    val epochMillis: Long,
)

data class ExamQuestion(
    val questionId: String,
    val type: QuestionType,
    val points: Int,
    val correctChoice: AnswerChoice,
) {
    init {
        require(questionId.isNotBlank())
        require(points in 1..3)
        require(correctChoice in type.availableChoices)
    }
}

data class ExamAnswer(val questionId: String, val choice: AnswerChoice?)

enum class ExamPhase {
    READING,
    ANSWERING,
    FINISHED,
}

enum class ExamCompletion {
    COMPLETED,
    TIME_EXPIRED,
    DEVICE_RESTARTED,
    MEDIA_UNAVAILABLE,
    ABANDONED,
}

/**
 * Persisted answers form the completed prefix of the question order. Only the current
 * answer can change. All elapsed timestamps belong to [bootId].
 */
data class ExamSession(
    val id: String,
    val datasetVersion: String,
    val category: LicenseCategory,
    val bootId: String,
    val startedAtElapsedMs: Long,
    val startedAtEpochMs: Long,
    val questionStartedAtElapsedMs: Long,
    val questions: List<ExamQuestion>,
    val answers: List<ExamAnswer> = emptyList(),
    val selection: AnswerChoice? = null,
    val completion: ExamCompletion? = null,
    val completedAtEpochMs: Long? = null,
) {
    val currentIndex: Int
        get() = answers.size

    val currentQuestion: ExamQuestion?
        get() = if (completion == null) questions.getOrNull(currentIndex) else null

    val score: Int
        get() = questions.zip(answers).sumOf { (question, answer) ->
            if (answer.questionId == question.questionId && answer.choice == question.correctChoice) {
                question.points
            } else {
                0
            }
        }

    val passed: Boolean
        get() = (completion == ExamCompletion.COMPLETED || completion == ExamCompletion.TIME_EXPIRED) &&
            score >= ExamRules.PASS_SCORE

    val mistakes: List<ExamAnswer>
        get() = questions.zip(answers).mapNotNull { (question, answer) ->
            answer.takeIf { it.questionId == question.questionId && it.choice != question.correctChoice }
        }

    internal val deadline: Long
        get() = startedAtElapsedMs + ExamRules.TOTAL_TIME_MS

    internal val answerDeadline: Long
        get() = questionStartedAtElapsedMs + when (currentQuestion?.type) {
            QuestionType.BASIC -> ExamRules.BASIC_READING_TIME_MS + ExamRules.BASIC_ANSWER_TIME_MS
            QuestionType.SPECIALIST -> ExamRules.SPECIALIST_TIME_MS
            null -> 0
        }

    fun phase(clock: ExamClock): ExamPhase {
        val question = currentQuestion ?: return ExamPhase.FINISHED
        val elapsed = clock.elapsedRealtimeMs
        if (!isSameBoot(clock) || elapsed >= deadline) return ExamPhase.FINISHED
        return if (question.type == QuestionType.BASIC && elapsed < readingDeadline) {
            ExamPhase.READING
        } else {
            ExamPhase.ANSWERING
        }
    }

    fun phaseRemainingMs(clock: ExamClock): Long = when (phase(clock)) {
        ExamPhase.READING -> minOf(readingDeadline, deadline).remainingAt(clock)
        ExamPhase.ANSWERING -> minOf(answerDeadline, deadline).remainingAt(clock)
        ExamPhase.FINISHED -> 0
    }

    fun remainingMs(clock: ExamClock): Long =
        if (completion == null && isSameBoot(clock)) deadline.remainingAt(clock) else 0

    internal fun isSameBoot(clock: ExamClock): Boolean =
        bootId == clock.bootId && clock.elapsedRealtimeMs >= startedAtElapsedMs

    private val readingDeadline: Long
        get() = questionStartedAtElapsedMs + ExamRules.BASIC_READING_TIME_MS
}

private fun Long.remainingAt(clock: ExamClock): Long = (this - clock.elapsedRealtimeMs).coerceAtLeast(0)
