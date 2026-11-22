package pl.prawko.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuestionTest {
    private val demoQuestion = Question(
        id = "DEMO-01",
        datasetVersion = "DEMO-test",
        category = LicenseCategory.B,
        prompt = "DEMO — przykładowa treść testowa modelu",
        points = 1,
        answers = QuestionAnswers.YesNo(correctIsYes = true),
    )

    @Test
    fun acceptsEachSupportedPointValue() {
        assertEquals(listOf(1, 2, 3), (1..3).map { demoQuestion.copy(points = it).points })
    }

    @Test
    fun rejectsUnsupportedPointValues() {
        assertThrows(IllegalArgumentException::class.java) { demoQuestion.copy(points = 0) }
        assertThrows(IllegalArgumentException::class.java) { demoQuestion.copy(points = 4) }
    }

    @Test
    fun multipleChoiceRequiresThreeNonemptyOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            QuestionAnswers.MultipleChoice(
                optionA = "DEMO A",
                optionB = " ",
                optionC = "DEMO C",
                correct = QuestionAnswers.Choice.A,
            )
        }
    }
}
