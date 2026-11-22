package pl.prawko.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.prawko.app.domain.QuestionLanguage
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionMediaType
import pl.prawko.app.domain.QuestionType

internal enum class DatasetSource {
    VERIFIED_OFFICIAL,
    DEMO,
}

internal enum class CorrectAnswer {
    YES,
    NO,
    A,
    B,
    C,
}

@Entity(tableName = "question_datasets")
internal data class QuestionDatasetEntity(
    @PrimaryKey val version: String,
    val source: DatasetSource,
    val sourceReference: String,
    val selectedCategory: String?,
    val sourceSha256: String?,
    val payloadSha256: String?,
)

@Entity(
    tableName = "active_question_datasets",
    foreignKeys = [
        ForeignKey(
            entity = QuestionDatasetEntity::class,
            parentColumns = ["version"],
            childColumns = ["datasetVersion"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("datasetVersion")],
)
internal data class ActiveQuestionDatasetEntity(
    @PrimaryKey val category: String,
    val datasetVersion: String,
)

@Entity(
    tableName = "questions",
    primaryKeys = ["datasetVersion", "id"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionDatasetEntity::class,
            parentColumns = ["version"],
            childColumns = ["datasetVersion"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class QuestionEntity(
    val datasetVersion: String,
    val id: String,
    val type: QuestionType,
    val prompt: String,
    val points: Int,
    val correctAnswer: CorrectAnswer,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
)

@Entity(
    tableName = "question_categories",
    primaryKeys = ["datasetVersion", "questionId", "category"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["datasetVersion", "id"],
            childColumns = ["datasetVersion", "questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class QuestionCategoryEntity(
    val datasetVersion: String,
    val questionId: String,
    val category: String,
)

@Entity(
    tableName = "question_translations",
    primaryKeys = ["datasetVersion", "questionId", "language"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["datasetVersion", "id"],
            childColumns = ["datasetVersion", "questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class QuestionTranslationEntity(
    val datasetVersion: String,
    val questionId: String,
    val language: QuestionLanguage,
    val prompt: String?,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
)

@Entity(
    tableName = "question_media",
    primaryKeys = ["datasetVersion", "questionId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["datasetVersion", "id"],
            childColumns = ["datasetVersion", "questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class QuestionMediaEntity(
    val datasetVersion: String,
    val questionId: String,
    val role: QuestionMediaRole,
    val originalName: String,
    val assetPath: String?,
    val type: QuestionMediaType?,
)
