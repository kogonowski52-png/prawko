package pl.prawko.app.domain

enum class AnswerChoice(val label: String) {
    YES("TAK"),
    NO("NIE"),
    A("A"),
    B("B"),
    C("C"),
}

data class QuestionKey(val datasetVersion: String, val questionId: String)

val Question.key: QuestionKey
    get() = QuestionKey(datasetVersion, id)

val QuestionAnswers.correctChoice: AnswerChoice
    get() = when (this) {
        is QuestionAnswers.YesNo -> if (correctIsYes) AnswerChoice.YES else AnswerChoice.NO
        is QuestionAnswers.MultipleChoice -> when (correct) {
            QuestionAnswers.Choice.A -> AnswerChoice.A
            QuestionAnswers.Choice.B -> AnswerChoice.B
            QuestionAnswers.Choice.C -> AnswerChoice.C
        }
    }

private val YesNoChoices = listOf(AnswerChoice.YES, AnswerChoice.NO)
private val MultipleChoices = listOf(AnswerChoice.A, AnswerChoice.B, AnswerChoice.C)

val QuestionType.availableChoices: List<AnswerChoice>
    get() = when (this) {
        QuestionType.BASIC -> YesNoChoices
        QuestionType.SPECIALIST -> MultipleChoices
    }

val QuestionAnswers.choices: List<AnswerChoice>
    get() = when (this) {
        is QuestionAnswers.YesNo -> YesNoChoices
        is QuestionAnswers.MultipleChoice -> MultipleChoices
    }

fun QuestionAnswers.textFor(choice: AnswerChoice): String {
    require(choice in choices)
    return when (this) {
        is QuestionAnswers.YesNo -> choice.label
        is QuestionAnswers.MultipleChoice -> when (choice) {
            AnswerChoice.A -> optionA
            AnswerChoice.B -> optionB
            AnswerChoice.C -> optionC
            else -> error("Unsupported choice for a multiple-choice question")
        }
    }
}
