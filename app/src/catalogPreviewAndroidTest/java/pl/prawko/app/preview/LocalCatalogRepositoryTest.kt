package pl.prawko.app.preview

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.prawko.app.data.RoomQuestionRepository
import pl.prawko.app.data.importing.LocalCatalogImportResult
import pl.prawko.app.data.importing.LocalCatalogImportResult.Failure
import pl.prawko.app.data.importing.LocalCatalogRepository
import pl.prawko.app.data.importing.LocalCatalogSummary
import pl.prawko.app.data.local.DatasetSource
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.data.local.RoomStudyProgressRepository
import pl.prawko.app.domain.AnswerChoice

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class LocalCatalogRepositoryTest {
    @Test
    fun importsLargeCatalogAndPreservesQuestionsAndProgressThroughRejectedImportsAndVersionChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val database = Room.inMemoryDatabaseBuilder(context, PrawkoDatabase::class.java).build()
        try {
            DemoCatalogDocuments(resolver).use { files ->
                val preview = LocalCatalogRepository(resolver, database)
                val progress = RoomStudyProgressRepository(database)
                val official = RoomQuestionRepository(database.questionDao())
                val version = "DEMO-2128"
                val payload = demoCatalog(version, questionCount = 2_128)
                val document = files.create { write(payload.toByteArray()) }
                assertNull(preview.activeCatalog.first())
                val imported = preview.importDocument(document) as LocalCatalogImportResult.Imported
                assertTrue(imported.isNew)
                assertTrue(imported.fileName.startsWith("DEMO-"))
                assertEquals(LocalCatalogSummary(version, DemoSourceReference, 2_128, 2_128), preview.activeCatalog.first())
                assertEquals(DatasetSource.DEMO, database.questionDao().dataset(version)?.source)
                assertNull(official.activeDatasetVersion.first())
                assertTrue(official.observeQuestions(version).first().isEmpty())

                val question = preview.questions.observeQuestions(version).first().first { it.id == "1" }
                progress.recordAnswer(question, AnswerChoice.YES, "DEMO-attempt")

                val conflicting = files.create { write(payload.replaceFirst(DemoQuestionPrefix, "DEMO · Zmienione ").toByteArray()) }
                val malformed = files.create { write("""{"schemaVersion":""".toByteArray()) }
                val oversized = files.create {
                    write(payload.toByteArray())
                    val padding = ByteArray(8_192) { ' '.code.toByte() }
                    repeat(4_096) { write(padding) }
                }
                val wrongCategory = files.create { write(demoCatalog("DEMO-A", category = "A").toByteArray()) }
                val wrongSource = files.create {
                    write(demoCatalog("DEMO-source").replace("\"source\":\"DEMO\"", "\"source\":\"VERIFIED_OFFICIAL\"").toByteArray())
                }

                assertEquals(Failure.VERSION_CONFLICT, preview.importDocument(conflicting))
                assertEquals(Failure.INVALID_DATA, preview.importDocument(malformed))
                assertEquals(Failure.TOO_LARGE, preview.importDocument(oversized))
                assertEquals(Failure.WRONG_CATEGORY, preview.importDocument(wrongCategory))
                assertEquals(Failure.WRONG_SOURCE, preview.importDocument(wrongSource))
                assertEquals(version, preview.activeCatalog.first()?.version)
                assertEquals(question, preview.questions.observeQuestions(version).first().first { it.id == "1" })

                val nextVersion = "DEMO-next"
                val next = files.create { write(demoCatalog(nextVersion).toByteArray()) }
                assertTrue((preview.importDocument(next) as LocalCatalogImportResult.Imported).isNew)
                assertEquals(nextVersion, preview.activeCatalog.first()?.version)
                assertTrue(progress.observeProgress(nextVersion).first().isEmpty())

                assertFalse((preview.importDocument(document) as LocalCatalogImportResult.Imported).isNew)
                assertEquals(version, preview.activeCatalog.first()?.version)
                assertEquals(1, progress.observeProgress(version).first().getValue("1").correctAttempts)
            }
        } finally {
            database.close()
        }
    }
}
