package pl.prawko.app.preview

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.OutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.Q)
internal class DemoCatalogDocuments(private val resolver: ContentResolver) : Closeable {
    private val documents = mutableListOf<Uri>()

    fun create(write: OutputStream.() -> Unit): Uri {
        val metadata = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "DEMO-${UUID.randomUUID()}.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, metadata))
        documents.add(uri)
        requireNotNull(resolver.openOutputStream(uri)).use(write)
        return uri
    }

    override fun close() {
        documents.forEach { resolver.delete(it, null, null) }
    }
}

internal const val DemoSourceReference = "DEMO — katalog syntetyczny do testów"
internal const val DemoQuestionPrefix = "DEMO · Pytanie matematyczne numer "

/** Includes a full exam's quotas, with a missing photo or clip on every question. */
internal fun demoCatalog(version: String, questionCount: Int = 32, category: String = "B"): String {
    val questions = JSONArray()
    for (index in 0 until questionCount) {
        val bucket = index % 32
        val specialist = bucket >= 20
        val points = when (bucket) {
            in 0..9, in 20..25 -> 3
            in 10..15, in 26..29 -> 2
            else -> 1
        }
        val originalName = if (index % 2 == 0) "DEMO-foto.jpg" else "DEMO-film.wmv"
        val media = JSONObject()
            .put("role", "QUESTION")
            .put("originalName", originalName)
            .put("assetPath", JSONObject.NULL)
        questions.put(
            JSONObject()
                .put("id", (index + 1).toString())
                .put("prompt", DemoQuestionPrefix + (index + 1))
                .put("points", points)
                .put("type", if (specialist) "SPECIALIST" else "BASIC")
                .put("correctAnswer", if (specialist) "A" else "YES")
                .put("optionA", if (specialist) "1" else JSONObject.NULL)
                .put("optionB", if (specialist) "2" else JSONObject.NULL)
                .put("optionC", if (specialist) "3" else JSONObject.NULL)
                .put("categories", JSONArray().put(category))
                .put("translations", JSONArray())
                .put("media", JSONArray().put(media)),
        )
    }
    return JSONObject()
        .put("schemaVersion", 1)
        .put("datasetVersion", version)
        .put("category", category)
        .put("source", "DEMO")
        .put("sourceReference", DemoSourceReference)
        .put("sourceSha256", "a".repeat(64))
        .put("questions", questions)
        .toString()
}
