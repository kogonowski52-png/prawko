package pl.prawko.app.ui.study

import kotlin.random.Random
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.StudyQuestionProgress

internal enum class StudyCollection {
    ALL,
    MISTAKES,
    FAVORITES,
}

internal enum class StudyOrder {
    SEQUENTIAL,
    RANDOM,
}

internal data class StudyFilters(
    val collection: StudyCollection = StudyCollection.ALL,
    val type: QuestionType? = null,
    val points: Int? = null,
    val order: StudyOrder = StudyOrder.SEQUENTIAL,
)

internal fun studyPool(questions: List<Question>, mistakeIds: Set<String>?): List<Question> =
    questions
        .filter { mistakeIds == null || it.id in mistakeIds }
        .distinctBy(Question::id)
        .sortedWith(compareBy<Question> { it.id.toLongOrNull() ?: Long.MAX_VALUE }.thenBy(Question::id))

internal fun studyQuestionIndices(
    questions: List<Question>,
    progress: Map<String, StudyQuestionProgress>,
    filters: StudyFilters,
): List<Int> = questions.indices.filter { index ->
    val question = questions[index]
    val work = progress[question.id]
    val matchesCollection = when (filters.collection) {
        StudyCollection.ALL -> true
        StudyCollection.MISTAKES -> work?.lastCorrect == false
        StudyCollection.FAVORITES -> work?.isFavorite == true
    }
    matchesCollection &&
        (filters.type == null || question.type == filters.type) &&
        (filters.points == null || question.points == filters.points)
}

internal fun StudyOrder.applyTo(indices: List<Int>, random: Random = Random.Default): IntArray =
    when (this) {
        StudyOrder.SEQUENTIAL -> indices.toIntArray()
        StudyOrder.RANDOM -> indices.shuffled(random).toIntArray()
    }
