package pl.prawko.app.domain

import kotlinx.coroutines.flow.Flow

enum class QuestionAvailability {
    UNAVAILABLE,
    AVAILABLE,
}

interface QuestionRepository {
    /** The active verified category B version, available as the exam's dataset identifier. */
    val activeDatasetVersion: Flow<String?>

    /** Reports whether the active dataset contains verified official category B questions. */
    val availability: Flow<QuestionAvailability>

    fun observeQuestions(datasetVersion: String): Flow<List<Question>>
}
