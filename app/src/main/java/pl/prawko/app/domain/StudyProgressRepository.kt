package pl.prawko.app.domain

import kotlinx.coroutines.flow.Flow

data class StudyQuestionProgress(
    val attempts: Int,
    val correctAttempts: Int,
    val lastCorrect: Boolean?,
    val isFavorite: Boolean,
)

interface StudyProgressRepository {
    fun observeProgress(datasetVersion: String): Flow<Map<String, StudyQuestionProgress>>

    /** The caller retains the attempt ID until the answer has been saved. */
    suspend fun recordAnswer(question: Question, choice: AnswerChoice, attemptId: String)

    suspend fun setFavorite(datasetVersion: String, questionId: String, isFavorite: Boolean)
}
