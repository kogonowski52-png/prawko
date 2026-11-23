package pl.prawko.app.ui.study

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.StudyQuestionProgress

class StudyQueueTest {
    @Test
    fun collectionFiltersCombineWithTypeAndPoints() {
        val basic = demoQuestion("2", 3)
        val other = demoQuestion("12", 1)
        val specialist = demoQuestion("11", 3).copy(
            type = QuestionType.SPECIALIST,
            answers = QuestionAnswers.MultipleChoice("DEMO A", "DEMO B", "DEMO C", QuestionAnswers.Choice.A),
        )
        val pool = studyPool(listOf(other, basic, specialist, basic), null)
        assertEquals(listOf("2", "11", "12"), pool.map { it.id })

        val progress = mapOf(
            basic.id to StudyQuestionProgress(2, 1, false, true),
            specialist.id to StudyQuestionProgress(1, 1, true, true),
        )
        assertEquals(listOf(0), studyQuestionIndices(pool, progress, StudyFilters(StudyCollection.MISTAKES, points = 3)))
        assertEquals(listOf(1), studyQuestionIndices(pool, progress, StudyFilters(StudyCollection.FAVORITES, QuestionType.SPECIALIST, 3)))
        assertEquals(listOf(other), studyPool(pool, setOf(other.id, "DEMO-inexistent")))
    }

    @Test
    fun shuffleRetainsEachQuestionOnce() {
        val order = (0..15).toList()
        val shuffled = StudyOrder.RANDOM.applyTo(order, Random(11)).toList()
        assertEquals(order, StudyOrder.SEQUENTIAL.applyTo(order).toList())
        assertEquals(order, shuffled.sorted())
        assertFalse(shuffled == order)
    }

    private fun demoQuestion(id: String, points: Int) = Question(
        id = id,
        datasetVersion = "DEMO-study-unit",
        category = LicenseCategory.B,
        prompt = "DEMO · Pytanie $id",
        points = points,
        answers = QuestionAnswers.YesNo(true),
    )
}
