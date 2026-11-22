package pl.prawko.app.data

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.prawko.app.data.local.CorrectAnswer
import pl.prawko.app.data.local.DatasetSource
import pl.prawko.app.data.local.QuestionDao
import pl.prawko.app.data.local.QuestionEntity
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.domain.QuestionMedia
import pl.prawko.app.domain.QuestionMediaReference
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.QuestionTranslation

internal class RoomQuestionRepository(
    private val dao: QuestionDao,
) : QuestionRepository {
    override val activeDatasetVersion: Flow<String?> =
        dao.observeActiveVersion(LicenseCategory.B.name)
            .catch { error ->
                if (error is SQLiteException) emit(null) else throw error
            }
            .distinctUntilChanged()

    override val availability: Flow<QuestionAvailability> =
        activeDatasetVersion.map { version ->
            if (version != null) QuestionAvailability.AVAILABLE else QuestionAvailability.UNAVAILABLE
        }.distinctUntilChanged()

    override fun observeQuestions(datasetVersion: String): Flow<List<Question>> =
        dao.observeQuestions(datasetVersion, LicenseCategory.B.name, DatasetSource.VERIFIED_OFFICIAL)
            .map { rows ->
                if (rows.isEmpty()) return@map emptyList()

                // Dataset versions and their linked rows are immutable after atomic installation.
                val categories = dao.categories(datasetVersion).groupBy({ it.questionId }, { it.category })
                val translations = dao.translations(datasetVersion).groupBy({ it.questionId }) {
                    QuestionTranslation(it.language, it.prompt, it.optionA, it.optionB, it.optionC)
                }
                val media = dao.media(datasetVersion).groupBy({ it.questionId }) {
                    QuestionMediaReference(it.role, it.originalName, it.assetPath, it.type)
                }
                rows.map { row ->
                    row.toDomain(
                        categories = categories[row.id].orEmpty().toSet(),
                        translations = translations[row.id].orEmpty(),
                        media = media[row.id].orEmpty(),
                    )
                }
            }
}

private fun QuestionEntity.toDomain(
    categories: Set<String>,
    translations: List<QuestionTranslation>,
    media: List<QuestionMediaReference>,
): Question {
    val answers = when (correctAnswer) {
        CorrectAnswer.YES -> QuestionAnswers.YesNo(correctIsYes = true)
        CorrectAnswer.NO -> QuestionAnswers.YesNo(correctIsYes = false)
        else -> QuestionAnswers.MultipleChoice(
            optionA = requireNotNull(optionA),
            optionB = requireNotNull(optionB),
            optionC = requireNotNull(optionC),
            correct = QuestionAnswers.Choice.valueOf(correctAnswer.name),
        )
    }
    val primaryMedia = media.firstOrNull { it.role == QuestionMediaRole.QUESTION }
    return Question(
        id = id,
        datasetVersion = datasetVersion,
        category = LicenseCategory.B,
        prompt = prompt,
        points = points,
        answers = answers,
        media = if (primaryMedia?.type != null && primaryMedia.assetPath != null) {
            QuestionMedia(primaryMedia.type, primaryMedia.assetPath)
        } else {
            null
        },
        type = type,
        categories = categories,
        translations = translations,
        mediaReferences = media,
    )
}
