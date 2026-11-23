package pl.prawko.app.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.StudyProgressRepository
import pl.prawko.app.domain.StudyQuestionProgress
import pl.prawko.app.domain.choices
import pl.prawko.app.domain.correctChoice

internal data class StudySeries(
    val questionIndices: IntArray,
    val index: Int = 0,
    val correctCount: Int = 0,
    val selected: AnswerChoice? = null,
    val attemptId: String? = null,
    val checked: Boolean = false,
) {
    val completed get() = index == questionIndices.size
}

internal data class StudyState(
    val filters: StudyFilters,
    val series: StudySeries?,
    val progress: Map<String, StudyQuestionProgress> = emptyMap(),
    val progressReady: Boolean = false,
    val progressFailed: Boolean = false,
    val saving: Boolean = false,
    val answerSaveFailed: Boolean = false,
    val favoriteChanges: Set<String> = emptySet(),
    val favoriteError: String? = null,
)

internal class StudyViewModel(
    questions: List<Question>,
    initialMistakeIds: Set<String>?,
    private val repository: StudyProgressRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val datasetVersion = questions.first().datasetVersion
    val pool = studyPool(questions, initialMistakeIds)

    private val _state = MutableStateFlow(StudyState(restoreFilters(), restoreSeries()))
    val state = _state.asStateFlow()
    private var observer: Job? = null

    init {
        observeProgress()
        val restored = state.value.series
        if (restored?.attemptId != null && !restored.checked) checkAnswer()
    }

    fun currentQuestion(series: StudySeries): Question? =
        series.questionIndices.getOrNull(series.index)?.let(pool::get)

    fun observeProgress() {
        observer?.cancel()
        _state.update { it.copy(progressFailed = false) }
        observer = viewModelScope.launch {
            try {
                repository.observeProgress(datasetVersion).collect { progress ->
                    _state.update { it.copy(progress = progress, progressReady = true, progressFailed = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(progressFailed = true) }
            }
        }
    }

    fun updateFilters(filters: StudyFilters) {
        _state.update { it.copy(filters = filters) }
        saved[CollectionKey] = filters.collection.name
        saved[TypeKey] = filters.type?.name
        saved[PointsKey] = filters.points
        saved[OrderKey] = filters.order.name
    }

    fun start() {
        val state = state.value
        if (!state.progressReady || state.progressFailed || state.series != null) return
        val queue = studyQuestionIndices(pool, state.progress, state.filters)
        if (queue.isNotEmpty()) saveSeries(StudySeries(state.filters.order.applyTo(queue)))
    }

    fun select(choice: AnswerChoice) {
        val series = state.value.series ?: return
        val question = currentQuestion(series) ?: return
        if (series.attemptId == null && choice in question.answers.choices) {
            saveSeries(series.copy(selected = choice))
        }
    }

    fun checkAnswer() {
        val series = state.value.series ?: return
        val question = currentQuestion(series) ?: return
        val choice = series.selected ?: return
        if (series.checked || state.value.saving) return

        val attemptId = series.attemptId ?: UUID.randomUUID().toString()
        saveSeries(series.copy(attemptId = attemptId))
        _state.update { it.copy(saving = true, answerSaveFailed = false) }

        viewModelScope.launch {
            try {
                repository.recordAnswer(question, choice, attemptId)
                val correctCount = series.correctCount + if (choice == question.answers.correctChoice) 1 else 0
                saveSeries(series.copy(attemptId = attemptId, checked = true, correctCount = correctCount))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(answerSaveFailed = true) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun next() {
        val series = state.value.series ?: return
        if (!series.checked || state.value.saving) return
        saveSeries(
            series.copy(
                index = series.index + 1,
                selected = null,
                attemptId = null,
                checked = false,
            ),
        )
    }

    fun clearSeries() {
        if (state.value.saving) return
        saveSeries(null)
        _state.update { it.copy(answerSaveFailed = false, favoriteError = null) }
    }

    fun toggleFavorite(question: Question) {
        val current = state.value
        if (!current.progressReady || question.id in current.favoriteChanges) return
        val desired = current.progress[question.id]?.isFavorite != true
        _state.update { it.copy(favoriteChanges = it.favoriteChanges + question.id, favoriteError = null) }

        viewModelScope.launch {
            try {
                repository.setFavorite(datasetVersion, question.id, desired)
                _state.update { state ->
                    val current = state.progress[question.id] ?: StudyQuestionProgress(
                        attempts = 0,
                        correctAttempts = 0,
                        lastCorrect = null,
                        isFavorite = false,
                    )
                    state.copy(progress = state.progress + (question.id to current.copy(isFavorite = desired)))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(favoriteError = question.id) }
            } finally {
                _state.update { it.copy(favoriteChanges = it.favoriteChanges - question.id) }
            }
        }
    }

    private fun restoreFilters() = StudyFilters(
        collection = saved.get<String>(CollectionKey)
            ?.let { name -> StudyCollection.entries.find { it.name == name } }
            ?: StudyCollection.ALL,
        type = saved.get<String>(TypeKey)?.let { name -> QuestionType.entries.find { it.name == name } },
        points = saved.get<Int>(PointsKey)?.takeIf { it in 1..3 },
        order = saved.get<String>(OrderKey)
            ?.let { name -> StudyOrder.entries.find { it.name == name } }
            ?: StudyOrder.SEQUENTIAL,
    )

    private fun restoreSeries(): StudySeries? {
        if (saved.get<String>(VersionKey) != datasetVersion) return null
        val indices = saved.get<IntArray>(QueueKey)
            ?.takeIf { it.isNotEmpty() && it.all(pool.indices::contains) }
            ?: return null
        val index = saved.get<Int>(IndexKey)?.takeIf { it in 0..indices.size } ?: return null
        val correctCount = (saved.get<Int>(CorrectKey) ?: 0).coerceIn(0, indices.size)
        val attemptId = saved.get<String>(AttemptKey)
        val checked = saved.get<Boolean>(CheckedKey) == true
        val selection = saved.get<String>(SelectedKey)?.let { name ->
            indices.getOrNull(index)?.let(pool::get)?.answers?.choices?.find { it.name == name }
        }
        return StudySeries(indices, index, correctCount, selection, attemptId, checked)
    }

    private fun saveSeries(series: StudySeries?) {
        saved[VersionKey] = datasetVersion
        saved[QueueKey] = series?.questionIndices
        saved[IndexKey] = series?.index
        saved[CorrectKey] = series?.correctCount
        saved[SelectedKey] = series?.selected?.name
        saved[AttemptKey] = series?.attemptId
        saved[CheckedKey] = series?.checked
        _state.update { it.copy(series = series) }
    }

    private companion object {
        const val CollectionKey = "study_collection"
        const val TypeKey = "study_question_type"
        const val PointsKey = "study_point_filter"
        const val OrderKey = "study_order"
        const val VersionKey = "study_dataset"
        const val QueueKey = "study_queue"
        const val IndexKey = "study_index"
        const val CorrectKey = "study_correct"
        const val SelectedKey = "study_selected"
        const val AttemptKey = "study_attempt"
        const val CheckedKey = "study_checked"
    }
}
