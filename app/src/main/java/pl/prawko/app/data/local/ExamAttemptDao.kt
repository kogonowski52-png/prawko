package pl.prawko.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.prawko.app.domain.AnswerChoice

@Dao
internal interface ExamAttemptDao {
    @Transaction
    @Query("SELECT * FROM exam_attempts WHERE activeSlot = 1")
    fun observeActive(): Flow<ExamAttemptWithQuestions?>

    @Transaction
    @Query("SELECT * FROM exam_attempts WHERE id = :id")
    fun observeAttempt(id: String): Flow<ExamAttemptWithQuestions?>

    @Transaction
    @Query("SELECT * FROM exam_attempts WHERE id = :id")
    suspend fun attempt(id: String): ExamAttemptWithQuestions?

    @Transaction
    @Query("SELECT * FROM exam_attempts WHERE completedAtEpochMs IS NOT NULL ORDER BY completedAtEpochMs DESC, id DESC")
    fun observeResults(): Flow<List<ExamAttemptWithQuestions>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttempt(attempt: ExamAttemptEntity): Long

    @Insert
    suspend fun insertQuestions(questions: List<ExamAttemptQuestionEntity>)

    @Update
    suspend fun updateAttempt(attempt: ExamAttemptEntity)

    @Query(
        """
        UPDATE exam_attempt_questions SET isAnswered = 1, answerChoice = :choice
        WHERE attemptId = :attemptId AND position = :position AND isAnswered = 0
        """,
    )
    suspend fun commitAnswer(attemptId: String, position: Int, choice: AnswerChoice?): Int
}
