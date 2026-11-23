package pl.prawko.app.data

import kotlinx.coroutines.flow.flowOf
import pl.prawko.app.BuildConfig
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.domain.QuestionRepository

internal fun selectBuildQuestionRepository(official: QuestionRepository): QuestionRepository =
    if (BuildConfig.PRAWKO_DEMO) DemoQuestionRepository() else official

internal class DemoQuestionRepository : QuestionRepository {
    private val version = "DEMO-etap-2"
    override val activeDatasetVersion = flowOf(version)
    override val availability = flowOf(QuestionAvailability.AVAILABLE)

    private val questions = buildList {
        listOf(10, 6, 4).forEachIndexed { bucket, count ->
            val points = 3 - bucket
            repeat(count + 1) { index ->
                val number = points * 17 + index
                add(
                    Question(
                        id = "DEMO-B-$points-$index",
                        datasetVersion = version,
                        category = LicenseCategory.B,
                        prompt = "DEMO · Czy liczba $number jest parzysta?",
                        points = points,
                        answers = QuestionAnswers.YesNo(correctIsYes = number % 2 == 0),
                    ),
                )
            }
        }
        listOf(6, 4, 2).forEachIndexed { bucket, count ->
            val points = 3 - bucket
            repeat(count + 1) { index ->
                val number = points * 13 + index
                val correct = QuestionAnswers.Choice.entries[index % 3]
                val choices = QuestionAnswers.Choice.entries.map {
                    if (it == correct) (number + 2).toString() else (number + it.ordinal + 3).toString()
                }
                add(
                    Question(
                        id = "DEMO-S-$points-$index",
                        datasetVersion = version,
                        category = LicenseCategory.B,
                        prompt = "DEMO · Ile wynosi $number + 2?",
                        points = points,
                        answers = QuestionAnswers.MultipleChoice(choices[0], choices[1], choices[2], correct),
                    ),
                )
            }
        }
    }

    override fun observeQuestions(datasetVersion: String) =
        flowOf(if (datasetVersion == version) questions else emptyList())
}
