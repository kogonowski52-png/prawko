package pl.prawko.app.data.importing

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.stream.MalformedJsonException
import java.io.EOFException
import java.io.IOException
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.prawko.app.data.RoomQuestionRepository
import pl.prawko.app.data.local.CatalogVersionConflictException
import pl.prawko.app.data.local.DatasetSource
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.data.local.installCatalog
import pl.prawko.app.domain.LicenseCategory

internal data class LocalCatalogSummary(
    val version: String,
    val name: String,
    val questionCount: Int,
    val mediaWithoutPath: Int,
)

internal sealed interface LocalCatalogImportResult {
    data class Imported(val fileName: String, val version: String, val isNew: Boolean) : LocalCatalogImportResult

    enum class Failure : LocalCatalogImportResult {
        UNREADABLE,
        TOO_LARGE,
        INVALID_DATA,
        WRONG_CATEGORY,
        WRONG_SOURCE,
        VERSION_CONFLICT,
        DATABASE_ERROR,
    }
}

internal class LocalCatalogRepository(
    private val resolver: ContentResolver,
    private val database: PrawkoDatabase,
) {
    private val dao = database.questionDao()
    val questions = RoomQuestionRepository(dao, DatasetSource.DEMO)
    val activeCatalog = questions.activeDatasetVersion.map { version ->
        val dataset = version?.let { dao.dataset(it) } ?: return@map null
        LocalCatalogSummary(
            version = dataset.version,
            name = dataset.sourceReference,
            questionCount = dao.questionCount(dataset.version),
            mediaWithoutPath = dao.mediaWithoutPathCount(dataset.version),
        )
    }

    suspend fun importDocument(uri: Uri): LocalCatalogImportResult = withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return@withContext LocalCatalogImportResult.Failure.UNREADABLE
        }
        val parsed = try {
            val input = resolver.openInputStream(uri) ?: return@withContext LocalCatalogImportResult.Failure.UNREADABLE
            parseCatalog(input)
        } catch (_: CatalogTooLargeException) {
            return@withContext LocalCatalogImportResult.Failure.TOO_LARGE
        } catch (_: MalformedJsonException) {
            return@withContext LocalCatalogImportResult.Failure.INVALID_DATA
        } catch (_: EOFException) {
            return@withContext LocalCatalogImportResult.Failure.INVALID_DATA
        } catch (_: CharacterCodingException) {
            return@withContext LocalCatalogImportResult.Failure.INVALID_DATA
        } catch (_: IllegalArgumentException) {
            return@withContext LocalCatalogImportResult.Failure.INVALID_DATA
        } catch (_: IOException) {
            return@withContext LocalCatalogImportResult.Failure.UNREADABLE
        } catch (_: SecurityException) {
            return@withContext LocalCatalogImportResult.Failure.UNREADABLE
        }

        val (bundle, payloadSha256) = parsed
        if (bundle.category != LicenseCategory.B.name) {
            return@withContext LocalCatalogImportResult.Failure.WRONG_CATEGORY
        }
        if (bundle.source != DatasetSource.DEMO.name) {
            return@withContext LocalCatalogImportResult.Failure.WRONG_SOURCE
        }
        val fileName = documentName(uri)
        ensureActive()
        try {
            val installed = database.installCatalog(bundle, payloadSha256, activateDemo = true)
            LocalCatalogImportResult.Imported(fileName, bundle.datasetVersion, installed)
        } catch (_: CatalogVersionConflictException) {
            LocalCatalogImportResult.Failure.VERSION_CONFLICT
        } catch (_: SQLiteException) {
            LocalCatalogImportResult.Failure.DATABASE_ERROR
        }
    }

    private fun documentName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val name = try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: RuntimeException) {
            null // Some providers offer a stream without display metadata.
        }
        return name?.filterNot(Char::isISOControl)?.trim()?.take(160)?.takeIf(String::isNotEmpty) ?: "questions.json"
    }
}
