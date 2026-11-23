package pl.prawko.app.data.importing

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.properties.ReadOnlyProperty

private const val MaxPackageBytes = 32 * 1024 * 1024L
private const val MaxQuestions = 50_000
private const val MaxTextLength = 8_192

private val DatasetVersionPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
private val CategoryPattern = Regex("[A-Z][A-Z0-9]*(?:\\+[A-Z][A-Z0-9]*)?")
private val Sha256Pattern = Regex("[0-9a-f]{64}")
private val MediaPathPattern = Regex("media/[0-9a-f]{64}\\.(jpg|mp4)")
private val Languages = setOf("EN", "DE", "UK")
private val MediaRoles = setOf("QUESTION", "PJM_QUESTION", "PJM_A", "PJM_B", "PJM_C")

internal class CatalogTooLargeException : IOException("Catalog exceeds 32 MiB")

/** Checks the asset format while hashing the exact bytes presented to Room. Owns [input]. */
internal fun parseCatalog(input: InputStream): ParsedCatalog {
    val digest = MessageDigest.getInstance("SHA-256")
    val stream = DigestInputStream(CappedInputStream(input), digest)
    val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
    val bundle = JsonReader(InputStreamReader(stream, utf8)).use { reader ->
        reader.strictness = Strictness.STRICT
        val catalog = reader.readBundle()
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected data after catalog" }
        catalog
    }
    return ParsedCatalog(bundle, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
}

private fun JsonReader.readBundle(): CatalogBundle {
    val record = JsonRecord()
    val schemaVersion by record.field("schemaVersion") { integer() }
    val datasetVersion by record.field("datasetVersion") { text(80) }
    val category by record.field("category") { text(16) }
    val source by record.field("source") { text(17) }
    val sourceReference by record.field("sourceReference") { text(2_048) }
    val sourceSha256 by record.field("sourceSha256") { text(64) }
    val questions by record.field("questions") { array(MaxQuestions) { readQuestion() } }
    record.read(this)

    require(schemaVersion == 1) { "Unsupported catalog schema" }
    require(DatasetVersionPattern.matches(datasetVersion)) { "Invalid dataset version" }
    require(CategoryPattern.matches(category)) { "Invalid selected category" }
    require(source == "VERIFIED_OFFICIAL" || source == "DEMO") { "Invalid dataset source" }
    require(Sha256Pattern.matches(sourceSha256)) { "Invalid source SHA-256" }
    require(questions.isNotEmpty()) { "Empty catalog" }
    require(questions.distinctBy { it.id }.size == questions.size) { "Repeated question ID" }
    require(questions.all { category in it.categories }) { "A question lacks the selected category" }
    return CatalogBundle(datasetVersion, category, source, sourceReference, sourceSha256, questions)
}

private fun JsonReader.readQuestion(): CatalogQuestion {
    val record = JsonRecord()
    val id by record.field("id") { text(128) }
    val prompt by record.field("prompt") { text() }
    val points by record.field("points") { integer() }
    val type by record.field("type") { text(10) }
    val correctAnswer by record.field("correctAnswer") { text(3) }
    val optionA by record.field("optionA") { nullableText() }
    val optionB by record.field("optionB") { nullableText() }
    val optionC by record.field("optionC") { nullableText() }
    val categories by record.field("categories") { array(128) { text(16) } }
    val translations by record.field("translations") { array(Languages.size) { readTranslation() } }
    val media by record.field("media") { array(MediaRoles.size) { readMedia() } }
    record.read(this)

    require(id == id.trim() && id.none(Char::isISOControl)) { "Invalid question ID" }
    require(points in 1..3) { "Invalid points" }
    require(categories.isNotEmpty() && categories.all(CategoryPattern::matches)) { "Invalid categories" }
    require(categories.toSet().size == categories.size) { "Repeated question category" }
    require(translations.distinctBy { it.language }.size == translations.size) { "Repeated translation" }
    require(media.distinctBy { it.role }.size == media.size) { "Repeated media role" }

    val options = listOf(optionA, optionB, optionC)
    when (type) {
        "BASIC" -> require(correctAnswer in listOf("YES", "NO") && options.all { it == null }) {
            "BASIC question must have a YES or NO answer"
        }
        "SPECIALIST" -> require(correctAnswer in listOf("A", "B", "C") && options.all { it != null }) {
            "SPECIALIST question must have three choices and an A/B/C answer"
        }
        else -> throw IllegalArgumentException("Unknown question type")
    }
    return CatalogQuestion(id, prompt, points, type, correctAnswer, optionA, optionB, optionC, categories, translations, media)
}

private fun JsonReader.readTranslation(): CatalogTranslation {
    val record = JsonRecord()
    val language by record.field("language") { text(2) }
    val prompt by record.field("prompt") { nullableText() }
    val optionA by record.field("optionA") { nullableText() }
    val optionB by record.field("optionB") { nullableText() }
    val optionC by record.field("optionC") { nullableText() }
    record.read(this)

    require(language in Languages) { "Invalid translation language" }
    require(listOf(prompt, optionA, optionB, optionC).any { it != null }) { "Empty translation" }
    return CatalogTranslation(language, prompt, optionA, optionB, optionC)
}

private fun JsonReader.readMedia(): CatalogMedia {
    val record = JsonRecord()
    val role by record.field("role") { text(12) }
    val originalName by record.field("originalName") { text(1_024) }
    val assetPath by record.field("assetPath") { nullableText(80) }
    record.read(this)

    require(role in MediaRoles) { "Invalid media role" }
    require(originalName.none(Char::isISOControl)) { "Invalid original media name" }
    assetPath?.let { require(MediaPathPattern.matches(it)) { "Invalid media path" } }
    return CatalogMedia(role, originalName, assetPath)
}

private fun JsonReader.integer(): Int {
    require(peek() == JsonToken.NUMBER) { "Expected an integer at $path" }
    return requireNotNull(nextString().toIntOrNull()) { "Invalid integer at $path" }
}

private fun JsonReader.text(maxLength: Int = MaxTextLength): String {
    require(peek() == JsonToken.STRING) { "Expected text at $path" }
    return nextString().also { value ->
        require(value.length <= maxLength && value.isNotBlank()) { "Invalid text length at $path" }
        require(value.none { it.isISOControl() && it != '\t' && it != '\r' && it != '\n' }) {
            "Invalid character at $path"
        }
        if (value.any { it in '\uD800'..'\uDFFF' }) {
            require(Charsets.UTF_8.newEncoder().canEncode(value)) { "Invalid Unicode at $path" }
        }
    }
}

private fun JsonReader.nullableText(maxLength: Int = MaxTextLength): String? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        text(maxLength)
    }

private inline fun <T> JsonReader.array(limit: Int, readItem: () -> T): List<T> {
    require(peek() == JsonToken.BEGIN_ARRAY) { "Expected an array at $path" }
    beginArray()
    val items = mutableListOf<T>()
    while (hasNext()) {
        require(items.size < limit) { "Too many entries at $path" }
        items.add(readItem())
    }
    endArray()
    return items
}

/** Reads declared fields in any JSON object order and detects duplicate, unknown and missing keys. */
private class JsonRecord {
    private val fields = mutableMapOf<String, JsonReader.() -> Unit>()

    fun <T> field(name: String, readValue: JsonReader.() -> T): ReadOnlyProperty<Any?, T> {
        var parsed: Result<T>? = null
        fields[name] = { parsed = Result.success(readValue()) }
        return ReadOnlyProperty { _, _ -> checkNotNull(parsed).getOrThrow() }
    }

    fun read(reader: JsonReader) {
        require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Expected an object at ${reader.path}" }
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            val readValue = requireNotNull(fields.remove(name)) { "Unexpected or repeated field at ${reader.path}" }
            reader.readValue()
        }
        reader.endObject()
        require(fields.isEmpty()) { "Missing required fields: ${fields.keys.joinToString()}" }
    }
}

private class CappedInputStream(input: InputStream) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int = super.read().also { if (it >= 0) countBytes(1) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        super.read(buffer, offset, length).also { if (it > 0) countBytes(it) }

    private fun countBytes(count: Int) {
        bytesRead += count
        if (bytesRead > MaxPackageBytes) throw CatalogTooLargeException()
    }
}
