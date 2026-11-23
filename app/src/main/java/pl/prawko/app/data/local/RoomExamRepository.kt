package pl.prawko.app.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.prawko.app.domain.exam.ExamAnswer
import pl.prawko.app.domain.exam.ExamHistoryItem
import pl.prawko.app.domain.exam.ExamQuestion
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamSession

internal class RoomExamRepository(private val database: PrawkoDatabase) : ExamRepository {
    private val dao = database.examAttemptDao()

    override val activeSession: Flow<ExamSession?> = dao.observeActive()
        .map { it?.toSession() }
        .distinctUntilChanged()

    override fun observeSession(sessionId: String): Flow<ExamSession?> =
        dao.observeAttempt(sessionId).map { it?.toSession() }.distinctUntilChanged()

    override fun observeResults(): Flow<List<ExamHistoryItem>> = dao.observeResults()
        .map { rows ->
            rows.map {
                ExamHistoryItem(it.toSession(), requireNotNull(it.attempt.completedAtEpochMs))
            }
        }
        .distinctUntilChanged()

    override suspend fun start(session: ExamSession): Boolean = database.withTransaction {
        require(session.id.isNotBlank() && session.datasetVersion.isNotBlank())
        require(session.completion == null && session.completedAtEpochMs == null)
        require(session.questions.isNotEmpty() && session.answers.isEmpty() && session.selection == null)
        if (dao.insertAttempt(session.toEntity()) == -1L) return@withTransaction false
        dao.insertQuestions(
            session.questions.mapIndexed { position, question ->
                ExamAttemptQuestionEntity(
                    attemptId = session.id,
                    position = position,
                    questionId = question.questionId,
                    type = question.type,
                    points = question.points,
                    correctChoice = question.correctChoice,
                )
            },
        )
        true
    }

    override suspend fun save(previous: ExamSession, session: ExamSession): Boolean = database.withTransaction {
        checkTransition(previous, session)
        val stored = dao.attempt(previous.id)?.toSession() ?: return@withTransaction false
        if (stored == session) return@withTransaction true
        if (stored != previous || stored.completion != null) return@withTransaction false

        dao.updateAttempt(session.toEntity())
        for (position in previous.answers.size until session.answers.size) {
            val answer = session.answers[position]
            check(dao.commitAnswer(session.id, position, answer.choice) == 1)
        }
        true
    }

    private fun checkTransition(previous: ExamSession, session: ExamSession) {
        require(session.id == previous.id && session.datasetVersion == previous.datasetVersion)
        require(session.category == previous.category && session.questions == previous.questions)
        require(session.bootId == previous.bootId && session.startedAtElapsedMs == previous.startedAtElapsedMs)
        require(session.startedAtEpochMs == previous.startedAtEpochMs)
        require((session.completion == null) == (session.completedAtEpochMs == null))
        require(session.answers.size <= session.questions.size)
        require(session.answers.take(previous.answers.size) == previous.answers)
        require(session.answers.zip(session.questions).all { (answer, question) -> answer.questionId == question.questionId })
    }
}

private fun ExamSession.toEntity() = ExamAttemptEntity(
    id = id,
    datasetVersion = datasetVersion,
    category = category,
    bootId = bootId,
    startedAtElapsedMs = startedAtElapsedMs,
    startedAtEpochMs = startedAtEpochMs,
    questionStartedAtElapsedMs = questionStartedAtElapsedMs,
    selection = selection,
    completion = completion,
    completedAtEpochMs = completedAtEpochMs,
    activeSlot = if (completion == null) 1 else null,
)

private fun ExamAttemptWithQuestions.toSession(): ExamSession {
    val ordered = questions.sortedBy { it.position }
    return ExamSession(
        id = attempt.id,
        datasetVersion = attempt.datasetVersion,
        category = attempt.category,
        bootId = attempt.bootId,
        startedAtElapsedMs = attempt.startedAtElapsedMs,
        startedAtEpochMs = attempt.startedAtEpochMs,
        questionStartedAtElapsedMs = attempt.questionStartedAtElapsedMs,
        questions = ordered.map { ExamQuestion(it.questionId, it.type, it.points, it.correctChoice) },
        answers = ordered.filter { it.isAnswered }.map { ExamAnswer(it.questionId, it.answerChoice) },
        selection = attempt.selection,
        completion = attempt.completion,
        completedAtEpochMs = attempt.completedAtEpochMs,
    )
}
