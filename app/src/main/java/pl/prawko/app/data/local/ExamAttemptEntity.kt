package pl.prawko.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.exam.ExamCompletion

@Entity(
    tableName = "exam_attempts",
    indices = [
        Index(value = ["activeSlot"], unique = true),
        Index(value = ["completedAtEpochMs"]),
    ],
)
internal data class ExamAttemptEntity(
    @PrimaryKey val id: String,
    val datasetVersion: String,
    val category: LicenseCategory,
    val bootId: String,
    val startedAtElapsedMs: Long,
    val startedAtEpochMs: Long,
    val questionStartedAtElapsedMs: Long,
    val selection: AnswerChoice?,
    val completion: ExamCompletion?,
    val completedAtEpochMs: Long?,
    val activeSlot: Int?,
)

@Entity(
    tableName = "exam_attempt_questions",
    primaryKeys = ["attemptId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ExamAttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["attemptId", "questionId"], unique = true)],
)
internal data class ExamAttemptQuestionEntity(
    val attemptId: String,
    val position: Int,
    val questionId: String,
    val type: QuestionType,
    val points: Int,
    val correctChoice: AnswerChoice,
    val isAnswered: Boolean = false,
    val answerChoice: AnswerChoice? = null,
)

internal data class ExamAttemptWithQuestions(
    @Embedded val attempt: ExamAttemptEntity,
    @Relation(parentColumn = "id", entityColumn = "attemptId")
    val questions: List<ExamAttemptQuestionEntity>,
)
