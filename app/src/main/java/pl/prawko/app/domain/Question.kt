package pl.prawko.app.domain

enum class LicenseCategory {
    B,
}

enum class QuestionMediaType {
    IMAGE,
    VIDEO,
}

data class QuestionMedia(
    val type: QuestionMediaType,
    val relativePath: String,
) {
    init {
        require(relativePath.isNotBlank())
    }
}

sealed interface QuestionAnswers {
    data class YesNo(val correctIsYes: Boolean) : QuestionAnswers

    data class MultipleChoice(
        val optionA: String,
        val optionB: String,
        val optionC: String,
        val correct: Choice,
    ) : QuestionAnswers {
        init {
            require(optionA.isNotBlank() && optionB.isNotBlank() && optionC.isNotBlank())
        }
    }

    enum class Choice {
        A,
        B,
        C,
    }
}

data class Question(
    val id: String,
    val datasetVersion: String,
    val category: LicenseCategory,
    val prompt: String,
    val points: Int,
    val answers: QuestionAnswers,
    val media: QuestionMedia? = null,
) {
    init {
        require(id.isNotBlank() && datasetVersion.isNotBlank() && prompt.isNotBlank())
        require(points in 1..3)
    }
}
