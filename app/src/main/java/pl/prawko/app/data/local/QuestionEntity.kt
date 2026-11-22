package pl.prawko.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.QuestionMediaType

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
    val category: LicenseCategory,
    val prompt: String,
    val points: Int,
    val correctAnswer: CorrectAnswer,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val mediaType: QuestionMediaType?,
    val mediaPath: String?,
)
