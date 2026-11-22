package pl.prawko.app.domain

enum class LicenseCategory {
    B,
}

enum class QuestionType {
    BASIC,
    SPECIALIST,
}

enum class QuestionLanguage {
    EN,
    DE,
    UK,
}

enum class QuestionMediaRole {
    QUESTION,
    PJM_QUESTION,
    PJM_A,
    PJM_B,
    PJM_C,
}

data class QuestionTranslation(
    val language: QuestionLanguage,
    val prompt: String?,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
)

data class QuestionMediaReference(
    val role: QuestionMediaRole,
    val originalName: String,
    val assetPath: String?,
    val type: QuestionMediaType?,
)

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
    val type: QuestionType = when (answers) {
        is QuestionAnswers.YesNo -> QuestionType.BASIC
        is QuestionAnswers.MultipleChoice -> QuestionType.SPECIALIST
    },
    val categories: Set<String> = setOf(category.name),
    val translations: List<QuestionTranslation> = emptyList(),
    val mediaReferences: List<QuestionMediaReference> = emptyList(),
) {
    init {
        require(id.isNotBlank() && datasetVersion.isNotBlank() && prompt.isNotBlank())
        require(points in 1..3)
    }
}
