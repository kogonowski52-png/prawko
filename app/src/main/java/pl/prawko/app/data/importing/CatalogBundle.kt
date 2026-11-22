package pl.prawko.app.data.importing

internal data class CatalogBundle(
    val datasetVersion: String,
    val category: String,
    val source: String,
    val sourceReference: String,
    val sourceSha256: String,
    val questions: List<CatalogQuestion>,
)

internal data class CatalogQuestion(
    val id: String,
    val prompt: String,
    val points: Int,
    val type: String,
    val correctAnswer: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val categories: List<String>,
    val translations: List<CatalogTranslation>,
    val media: List<CatalogMedia>,
)

internal data class CatalogTranslation(
    val language: String,
    val prompt: String?,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
)

internal data class CatalogMedia(
    val role: String,
    val originalName: String,
    val assetPath: String?,
)

internal data class ParsedCatalog(val bundle: CatalogBundle, val payloadSha256: String)
