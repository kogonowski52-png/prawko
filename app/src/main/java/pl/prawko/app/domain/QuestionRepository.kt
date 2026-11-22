package pl.prawko.app.domain

import kotlinx.coroutines.flow.Flow

enum class QuestionAvailability {
    UNAVAILABLE,
    AVAILABLE,
}

interface QuestionRepository {
    /** Reports whether the local database contains verified official category B questions. */
    val availability: Flow<QuestionAvailability>

    fun observeQuestions(datasetVersion: String): Flow<List<Question>>
}
