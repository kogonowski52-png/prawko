package pl.prawko.app.data.local

import androidx.room.withTransaction
import pl.prawko.app.data.importing.CatalogBundle
import pl.prawko.app.domain.QuestionLanguage
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionMediaType
import pl.prawko.app.domain.QuestionType

internal class CatalogVersionConflictException : IllegalStateException("Catalog version already exists with a different payload")

/** Installs a validated bundle as an immutable version, then activates the selected category. */
internal suspend fun PrawkoDatabase.installCatalog(
    bundle: CatalogBundle,
    payloadSha256: String,
    activateDemo: Boolean = false,
): Boolean = withTransaction {
    val dao = questionDao()
    val version = bundle.datasetVersion
    val source = DatasetSource.valueOf(bundle.source)
    val active = ActiveQuestionDatasetEntity(bundle.category, version)
    val existing = dao.dataset(version)
    if (existing != null) {
        if (existing.payloadSha256 != payloadSha256 || existing.source != source) {
            throw CatalogVersionConflictException()
        }
        if (source == DatasetSource.DEMO && activateDemo) dao.activateDataset(active)
        return@withTransaction false
    }

    dao.insertDataset(
        QuestionDatasetEntity(
            version = version,
            source = source,
            sourceReference = bundle.sourceReference,
            selectedCategory = bundle.category,
            sourceSha256 = bundle.sourceSha256,
            payloadSha256 = payloadSha256,
        ),
    )
    dao.insertQuestions(
        bundle.questions.map { question ->
            QuestionEntity(
                datasetVersion = version,
                id = question.id,
                type = QuestionType.valueOf(question.type),
                prompt = question.prompt,
                points = question.points,
                correctAnswer = CorrectAnswer.valueOf(question.correctAnswer),
                optionA = question.optionA,
                optionB = question.optionB,
                optionC = question.optionC,
            )
        },
    )
    dao.insertCategories(
        bundle.questions.flatMap { question ->
            question.categories.map { QuestionCategoryEntity(version, question.id, it) }
        },
    )
    dao.insertTranslations(
        bundle.questions.flatMap { question ->
            question.translations.map { text ->
                QuestionTranslationEntity(
                    datasetVersion = version,
                    questionId = question.id,
                    language = QuestionLanguage.valueOf(text.language),
                    prompt = text.prompt,
                    optionA = text.optionA,
                    optionB = text.optionB,
                    optionC = text.optionC,
                )
            }
        },
    )
    dao.insertMedia(
        bundle.questions.flatMap { question ->
            question.media.map { media ->
                val fileName = media.assetPath ?: media.originalName
                QuestionMediaEntity(
                    datasetVersion = version,
                    questionId = question.id,
                    role = QuestionMediaRole.valueOf(media.role),
                    originalName = media.originalName,
                    assetPath = media.assetPath,
                    type = when (fileName.substringAfterLast('.', "").lowercase()) {
                        "jpg", "jpeg" -> QuestionMediaType.IMAGE
                        "wmv", "mp4" -> QuestionMediaType.VIDEO
                        else -> null
                    },
                )
            }
        },
    )
    if (source == DatasetSource.VERIFIED_OFFICIAL || activateDemo) {
        dao.activateDataset(active)
    }
    true
}
