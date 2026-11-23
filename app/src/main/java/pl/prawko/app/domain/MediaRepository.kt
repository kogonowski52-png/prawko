package pl.prawko.app.domain

data class LocalMedia(val type: QuestionMediaType, val uri: String)

interface MediaRepository {
    suspend fun resolve(datasetVersion: String, reference: QuestionMediaReference): LocalMedia?

    suspend fun hasRequiredMedia(question: Question): Boolean {
        val reference = question.primaryMediaReference ?: return true
        return resolve(question.datasetVersion, reference) != null
    }
}

val Question.primaryMediaReference: QuestionMediaReference?
    get() = mediaReferences.firstOrNull { it.role == QuestionMediaRole.QUESTION }
        ?: media?.let {
            QuestionMediaReference(
                role = QuestionMediaRole.QUESTION,
                originalName = it.relativePath.substringAfterLast('/'),
                assetPath = it.relativePath,
                type = it.type,
            )
        }
