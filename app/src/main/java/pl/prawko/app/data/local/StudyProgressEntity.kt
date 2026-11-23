package pl.prawko.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.prawko.app.domain.AnswerChoice

@Entity(
    tableName = "study_answers",
    indices = [
        Index(value = ["attemptId"], unique = true),
        Index(value = ["datasetVersion", "questionId"]),
    ],
)
internal data class StudyAnswerEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val attemptId: String,
    val datasetVersion: String,
    val questionId: String,
    val choice: AnswerChoice,
    val correct: Boolean,
    val answeredAtEpochMs: Long,
)

@Entity(
    tableName = "study_favorites",
    primaryKeys = ["datasetVersion", "questionId"],
)
internal data class StudyFavoriteEntity(
    val datasetVersion: String,
    val questionId: String,
)
