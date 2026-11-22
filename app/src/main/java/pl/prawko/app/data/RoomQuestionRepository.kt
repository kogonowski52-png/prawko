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
import pl.prawko.app.domain.QuestionRepository

internal class RoomQuestionRepository(
    private val dao: QuestionDao,
) : QuestionRepository {
    override val availability: Flow<QuestionAvailability> =
        dao.observeAvailability(LicenseCategory.B, DatasetSource.VERIFIED_OFFICIAL)
            .map { hasQuestions ->
                if (hasQuestions) QuestionAvailability.AVAILABLE else QuestionAvailability.UNAVAILABLE
            }
            .catch { error ->
                if (error is SQLiteException) emit(QuestionAvailability.UNAVAILABLE) else throw error
            }
            .distinctUntilChanged()

    override fun observeQuestions(datasetVersion: String): Flow<List<Question>> =
        dao.observeQuestions(datasetVersion, LicenseCategory.B, DatasetSource.VERIFIED_OFFICIAL)
            .map { rows -> rows.map(QuestionEntity::toDomain) }
}

private fun QuestionEntity.toDomain(): Question {
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
    return Question(
        id = id,
        datasetVersion = datasetVersion,
        category = category,
        prompt = prompt,
        points = points,
        answers = answers,
        media = mediaType?.let { QuestionMedia(it, requireNotNull(mediaPath)) },
    )
}
