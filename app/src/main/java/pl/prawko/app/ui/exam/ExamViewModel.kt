package pl.prawko.app.ui.exam

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.exam.ExamClock
import pl.prawko.app.domain.exam.ExamCompletion
import pl.prawko.app.domain.exam.ExamDrawResult
import pl.prawko.app.domain.exam.ExamEngine
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamSession
import pl.prawko.app.domain.exam.ExamShortage

internal sealed interface ExamScreenState {
    data object Loading : ExamScreenState
    data object MissingCatalog : ExamScreenState
    data object Failed : ExamScreenState

    data class Intro(
        val datasetVersion: String,
        val shortages: List<ExamShortage>,
    ) : ExamScreenState

    data class Running(
        val session: ExamSession,
        val question: Question,
        val clock: ExamClock,
    ) : ExamScreenState

    data class Result(val sessionId: String) : ExamScreenState
}

internal fun interface ExamTimeSource {
    fun now(): ExamClock
}

internal class AndroidExamTimeSource(context: Context) : ExamTimeSource {
    private val contentResolver = context.applicationContext.contentResolver
    private val bootId: String by lazy {
        val bootCount = try {
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: SecurityException) {
            -1
        }
        if (bootCount >= 0) "boot-$bootCount" else ProcessBootFallback
    }

    override fun now() = ExamClock(
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        bootId = bootId,
        epochMillis = System.currentTimeMillis(),
    )

    private companion object {
        // If a device hides the boot counter, a process restart closes the timed attempt.
        val ProcessBootFallback = "process-${UUID.randomUUID()}"
    }
}

internal class ExamViewModel(
    private val questionRepository: QuestionRepository,
    private val examRepository: ExamRepository,
    private val mediaRepository: MediaRepository,
    private val category: LicenseCategory,
    private val timeSource: ExamTimeSource,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ExamScreenState>(ExamScreenState.Loading)
    val state = mutableState.asStateFlow()
    private val transitions = Mutex()
    private var catalogQuestions: Map<String, Question> = emptyMap()
    private var draw: ExamDrawResult.Ready? = null

    init {
        refresh()
        viewModelScope.launch {
            state.map { it is ExamScreenState.Running }
                .distinctUntilChanged()
                .collectLatest { running ->
                    if (!running) return@collectLatest
                    while (isActive) {
                        delay(250)
                        runTransition {
                            val current = mutableState.value as? ExamScreenState.Running ?: return@runTransition
                            val clock = timeSource.now()
                            persist(current.session, ExamEngine.reconcile(current.session, clock), clock)
                        }
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch { runTransition { load() } }
    }

    fun start() {
        viewModelScope.launch {
            runTransition {
                val intro = mutableState.value as? ExamScreenState.Intro ?: return@runTransition
                val ready = draw ?: return@runTransition
                mutableState.value = ExamScreenState.Loading
                val stillActive = questionRepository.activeDatasetVersion.first() == intro.datasetVersion
                val mediaReady = stillActive && ready.questions.all { candidate ->
                    catalogQuestions[candidate.questionId]?.let { mediaRepository.hasRequiredMedia(it) } == true
                }
                if (!mediaReady) {
                    load()
                    return@runTransition
                }
                val id = UUID.randomUUID().toString()
                val clock = timeSource.now()
                val session = ExamEngine.start(id, intro.datasetVersion, category, ready.questions, clock)
                savedState[AttemptId] = id
                if (examRepository.start(session)) present(session, clock) else load()
            }
        }
    }

    fun choose(questionId: String, choice: AnswerChoice) =
        changeSession { session, clock -> ExamEngine.choose(session, questionId, choice, clock) }

    fun submit(questionId: String) =
        changeSession { session, clock -> ExamEngine.submit(session, questionId, clock) }

    fun abandon() =
        changeSession { session, clock -> ExamEngine.interrupt(session, ExamCompletion.ABANDONED, clock) }

    fun mediaUnavailable(questionId: String) =
        changeSession { session, clock ->
            if (session.currentQuestion?.questionId == questionId) {
                ExamEngine.interrupt(session, ExamCompletion.MEDIA_UNAVAILABLE, clock)
            } else {
                session
            }
        }

    private fun changeSession(change: (ExamSession, ExamClock) -> ExamSession) {
        val inputTime = timeSource.now()
        viewModelScope.launch {
            runTransition {
                val active = mutableState.value as? ExamScreenState.Running ?: return@runTransition
                persist(active.session, change(active.session, inputTime), timeSource.now())
            }
        }
    }

    private suspend fun load() {
        mutableState.value = ExamScreenState.Loading
        val active = examRepository.activeSession.first()
        if (active != null) {
            savedState[AttemptId] = active.id
            catalogQuestions = questionRepository.observeQuestions(active.datasetVersion).first()
                .associateBy(Question::id)
            val clock = timeSource.now()
            persist(active, ExamEngine.reconcile(active, clock), clock)
            return
        }
        val recentId = savedState.get<String>(AttemptId)
        if (recentId != null && examRepository.observeSession(recentId).first()?.completion != null) {
            mutableState.value = ExamScreenState.Result(recentId)
            return
        }
        savedState.remove<String>(AttemptId)
        prepare()
    }

    private suspend fun prepare() {
        draw = null
        val version = questionRepository.activeDatasetVersion.first()
        if (version == null) {
            mutableState.value = ExamScreenState.MissingCatalog
            return
        }
        val candidates = questionRepository.observeQuestions(version).first()
            .filter { it.datasetVersion == version && category.name in it.categories }
        if (candidates.isEmpty()) {
            mutableState.value = ExamScreenState.MissingCatalog
            return
        }

        catalogQuestions = candidates.associateBy(Question::id)
        val playable = candidates.mapNotNullTo(mutableSetOf()) { question ->
            question.id.takeIf { mediaRepository.hasRequiredMedia(question) }
        }
        val shortages = when (val plan = ExamEngine.draw(candidates, version, category, { it.id in playable })) {
            is ExamDrawResult.Ready -> {
                draw = plan
                emptyList()
            }
            is ExamDrawResult.Insufficient -> plan.shortages
        }
        mutableState.value = ExamScreenState.Intro(version, shortages)
    }

    private suspend fun persist(previous: ExamSession, next: ExamSession, clock: ExamClock) {
        if (previous != next && !examRepository.save(previous, next)) {
            load()
            return
        }
        present(next, clock)
    }

    private suspend fun present(session: ExamSession, clock: ExamClock) {
        if (session.completion != null) {
            mutableState.value = ExamScreenState.Result(session.id)
            return
        }
        val question = catalogQuestions[session.currentQuestion?.questionId]
        if (question != null) {
            mutableState.value = ExamScreenState.Running(session, question, clock)
            return
        }
        val interrupted = ExamEngine.interrupt(session, ExamCompletion.MEDIA_UNAVAILABLE, clock)
        persist(session, interrupted, clock)
    }

    private suspend fun runTransition(block: suspend () -> Unit) {
        transitions.withLock {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("PrawkoExam", "Nie udało się odczytać lub zapisać stanu egzaminu.", failure)
                mutableState.value = ExamScreenState.Failed
            }
        }
    }

    private companion object {
        const val AttemptId = "examSessionId"
    }
}
