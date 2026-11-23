package pl.prawko.app.domain.exam

import kotlinx.coroutines.flow.Flow

data class ExamHistoryItem(
    val session: ExamSession,
    val completedAtEpochMillis: Long,
)

interface ExamRepository {
    val activeSession: Flow<ExamSession?>

    fun observeSession(sessionId: String): Flow<ExamSession?>

    fun observeResults(): Flow<List<ExamHistoryItem>>

    /** Atomically reserves the active slot for a new exam. */
    suspend fun start(session: ExamSession): Boolean

    /** Accepts a transition from [previous] and acknowledges an identical, already saved state. */
    suspend fun save(previous: ExamSession, session: ExamSession): Boolean
}
