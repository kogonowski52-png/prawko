package pl.prawko.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.StudyProgressRepository
import pl.prawko.app.domain.StudyQuestionProgress
import pl.prawko.app.domain.exam.ExamCompletion
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamSession

internal sealed interface ProgressLoad<out T> {
    data object Loading : ProgressLoad<Nothing>
    data object Failed : ProgressLoad<Nothing>
    data class Ready<T>(val data: T) : ProgressLoad<T>
}

internal data class StudyTotals(
    val answeredQuestions: Int,
    val attempts: Long,
    val correct: Long,
    val favorites: Int,
    val mistakes: Int,
    val types: List<StudyTypeTotal>,
) {
    val accuracyPercentage: Int? = if (attempts == 0L) null else (correct * 100 / attempts).toInt()
}

internal data class StudyTypeTotal(val type: QuestionType, val answered: Int, val total: Int)

internal fun studyTotals(
    questions: List<Question>,
    progressById: Map<String, StudyQuestionProgress>,
): StudyTotals {
    val progress = questions.mapNotNull { progressById[it.id] }
    return StudyTotals(
        answeredQuestions = progress.count { it.attempts > 0 },
        attempts = progress.sumOf { it.attempts.toLong() },
        correct = progress.sumOf { it.correctAttempts.toLong() },
        favorites = progress.count { it.isFavorite },
        mistakes = progress.count { it.lastCorrect == false },
        types = QuestionType.entries.map { type ->
            val matching = questions.filter { it.type == type }
            StudyTypeTotal(type, matching.count { (progressById[it.id]?.attempts ?: 0) > 0 }, matching.size)
        },
    )
}

internal data class ReviewedAnswer(
    val number: Int,
    val questionId: String,
    val question: Question?,
    val type: QuestionType,
    val points: Int,
    val chosen: AnswerChoice?,
    val correct: AnswerChoice,
) {
    val earned = if (chosen == correct) points else 0
}

internal data class ExamReview(val session: ExamSession, val answers: List<ReviewedAnswer>) {
    val isScored = session.completion == ExamCompletion.COMPLETED || session.completion == ExamCompletion.TIME_EXPIRED
    val correctCount = answers.count { it.chosen == it.correct }
    val incorrectCount = answers.count { it.chosen != null && it.chosen != it.correct }
    val noAnswerCount = answers.count { it.chosen == null }
    val maxScore = answers.sumOf { it.points }
    val mistakes = answers.filter { it.chosen != it.correct }
    val reviewableMistakeIds = mistakes.mapNotNullTo(mutableSetOf()) { it.question?.id }
}

internal fun examReview(session: ExamSession, questions: List<Question>): ExamReview {
    val byId = questions.associateBy(Question::id)
    val choices = session.answers.associateBy { it.questionId }
    return ExamReview(
        session = session,
        answers = session.questions.mapIndexed { index, question ->
            ReviewedAnswer(
                number = index + 1,
                questionId = question.questionId,
                question = byId[question.questionId],
                type = question.type,
                points = question.points,
                chosen = choices[question.questionId]?.choice,
                correct = question.correctChoice,
            )
        },
    )
}

internal class ProgressViewModel(
    studyRepository: StudyProgressRepository,
    examRepository: ExamRepository,
    questionRepository: QuestionRepository,
    datasetVersion: String?,
) : ViewModel() {
    private val studyRefresh = MutableStateFlow(0)
    private val historyRefresh = MutableStateFlow(0)

    val study = studyRefresh.loadProgress {
        if (datasetVersion == null) {
            flowOf(null)
        } else {
            combine(
                questionRepository.observeQuestions(datasetVersion),
                studyRepository.observeProgress(datasetVersion),
                ::studyTotals,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressLoad.Loading)

    val history = historyRefresh.loadProgress(examRepository::observeResults)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressLoad.Loading)

    fun reloadStudy() = studyRefresh.update { it + 1 }

    fun reloadHistory() = historyRefresh.update { it + 1 }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExamResultViewModel(
    attemptId: String,
    examRepository: ExamRepository,
    questionRepository: QuestionRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    val review = refresh.loadProgress {
        examRepository.observeSession(attemptId).flatMapLatest { session ->
            if (session?.completion == null) {
                flowOf(null)
            } else {
                questionRepository.observeQuestions(session.datasetVersion).map { questions ->
                    examReview(session, questions)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressLoad.Loading)

    fun reload() = refresh.update { it + 1 }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<Int>.loadProgress(source: () -> Flow<T>): Flow<ProgressLoad<T>> = flatMapLatest {
    source()
        .map<T, ProgressLoad<T>> { ProgressLoad.Ready(it) }
        .onStart { emit(ProgressLoad.Loading) }
        .catch { emit(ProgressLoad.Failed) }
}
