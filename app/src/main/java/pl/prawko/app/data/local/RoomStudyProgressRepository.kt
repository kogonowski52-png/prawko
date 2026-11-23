package pl.prawko.app.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.StudyProgressRepository
import pl.prawko.app.domain.StudyQuestionProgress
import pl.prawko.app.domain.choices
import pl.prawko.app.domain.correctChoice

internal class RoomStudyProgressRepository(private val database: PrawkoDatabase) : StudyProgressRepository {
    private val dao = database.studyProgressDao()

    override fun observeProgress(datasetVersion: String): Flow<Map<String, StudyQuestionProgress>> =
        dao.observeProgress(datasetVersion)
            .map { rows ->
                rows.associate { row ->
                    row.questionId to StudyQuestionProgress(
                        attempts = row.attempts,
                        correctAttempts = row.correctAttempts,
                        lastCorrect = row.lastCorrect,
                        isFavorite = row.isFavorite,
                    )
                }
            }
            .distinctUntilChanged()

    override suspend fun recordAnswer(question: Question, choice: AnswerChoice, attemptId: String) {
        require(attemptId.isNotBlank() && choice in question.answers.choices)
        database.withTransaction {
            val existing = dao.recordedAnswer(attemptId)
            if (existing != null) {
                check(existing.datasetVersion == question.datasetVersion && existing.questionId == question.id)
                check(existing.choice == choice)
                return@withTransaction
            }
            dao.recordAnswer(
                StudyAnswerEntity(
                    attemptId = attemptId,
                    datasetVersion = question.datasetVersion,
                    questionId = question.id,
                    choice = choice,
                    correct = question.answers.correctChoice == choice,
                    answeredAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun setFavorite(datasetVersion: String, questionId: String, isFavorite: Boolean) {
        require(datasetVersion.isNotBlank() && questionId.isNotBlank())
        if (isFavorite) {
            dao.addFavorite(StudyFavoriteEntity(datasetVersion, questionId))
        } else {
            dao.removeFavorite(datasetVersion, questionId)
        }
    }
}
