package pl.prawko.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.prawko.app.data.importing.CatalogBundle
import pl.prawko.app.data.importing.CatalogMedia
import pl.prawko.app.data.importing.CatalogQuestion
import pl.prawko.app.data.importing.CatalogTranslation
import pl.prawko.app.data.local.DatasetSource
import pl.prawko.app.data.local.MIGRATION_1_2
import pl.prawko.app.data.local.MIGRATION_2_3
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.data.local.installCatalog
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.domain.QuestionLanguage
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionMediaType
import pl.prawko.app.domain.QuestionType

@RunWith(AndroidJUnit4::class)
class RoomQuestionRepositoryTest {
    private val appContext get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun catalogInstallationStoresLinkedRowsAndExcludesDemoFromOfficialQueries() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(appContext, PrawkoDatabase::class.java).build()
        try {
            val dao = database.questionDao()
            val repository = RoomQuestionRepository(dao)
            assertEquals(QuestionAvailability.UNAVAILABLE, repository.availability.first())

            val demo = demoCatalog()
            assertTrue(database.installCatalog(demo, HASH))
            assertFalse(database.installCatalog(demo, HASH))
            val conflict = runCatching { database.installCatalog(demo, "c".repeat(64)) }
            assertTrue(conflict.exceptionOrNull() is IllegalStateException)

            val dataset = requireNotNull(dao.dataset(demo.datasetVersion))
            assertEquals(HASH, dataset.payloadSha256)
            assertEquals(demo.sourceSha256, dataset.sourceSha256)
            val question = dao.observeQuestions(demo.datasetVersion, "B", DatasetSource.DEMO).first().single()
            assertEquals("DEMO-01", question.id)
            assertEquals(QuestionType.BASIC, question.type)
            assertEquals(listOf("B", "B1"), dao.categories(demo.datasetVersion).map { it.category })
            val translation = dao.translations(demo.datasetVersion).single()
            assertEquals(QuestionLanguage.EN, translation.language)
            assertEquals("DEMO prompt", translation.prompt)
            assertNull(translation.optionA)
            val media = dao.media(demo.datasetVersion).associateBy { it.role }
            assertEquals("DEMO zdjęcie.JPG", media.getValue(QuestionMediaRole.QUESTION).originalName)
            assertEquals("media/demo.jpg", media.getValue(QuestionMediaRole.QUESTION).assetPath)
            assertEquals(QuestionMediaType.IMAGE, media.getValue(QuestionMediaRole.QUESTION).type)
            assertNull(media.getValue(QuestionMediaRole.PJM_QUESTION).assetPath)

            assertNull(repository.activeDatasetVersion.first())
            assertEquals(QuestionAvailability.UNAVAILABLE, repository.availability.first())
            assertTrue(repository.observeQuestions(demo.datasetVersion).first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun failedInsertRollsBackWholeVersion() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(appContext, PrawkoDatabase::class.java).build()
        try {
            val demo = demoCatalog()
            val badQuestion = demo.questions.single().copy(categories = listOf("B", "B"))
            val failure = runCatching {
                database.installCatalog(demo.copy(questions = listOf(badQuestion)), HASH)
            }
            assertTrue(failure.exceptionOrNull() is SQLiteConstraintException)
            assertNull(database.questionDao().dataset(demo.datasetVersion))
            assertTrue(database.installCatalog(demo, HASH))
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationPreservesLegacyRowsAndDoesNotActivateDemoData() = runBlocking {
        val name = "prawko-migration-${UUID.randomUUID()}.db"
        try {
            withContext(Dispatchers.IO) { createLegacyDatabase(name) }
            val migrated = Room.databaseBuilder(appContext, PrawkoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
            try {
                val dao = migrated.questionDao()
                val dataset = requireNotNull(dao.dataset("DEMO-legacy"))
                assertEquals("B", dataset.selectedCategory)
                assertNull(dataset.sourceSha256)
                val question = dao.observeQuestions(dataset.version, "B", DatasetSource.DEMO).first().single()
                assertEquals("DEMO-legacy-01", question.id)
                assertEquals(QuestionType.BASIC, question.type)
                assertEquals(2, question.points)
                assertEquals("B", dao.categories(dataset.version).single().category)
                assertEquals("imagery/DEMO.JPG", dao.media(dataset.version).single().assetPath)
                assertNull(RoomQuestionRepository(dao).activeDatasetVersion.first())
            } finally {
                migrated.close()
            }
        } finally {
            appContext.deleteDatabase(name)
        }
    }

    private fun createLegacyDatabase(name: String) {
        val file = appContext.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE question_datasets (
                    version TEXT NOT NULL PRIMARY KEY,
                    source TEXT NOT NULL,
                    sourceReference TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE questions (
                    datasetVersion TEXT NOT NULL,
                    id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    points INTEGER NOT NULL,
                    correctAnswer TEXT NOT NULL,
                    optionA TEXT,
                    optionB TEXT,
                    optionC TEXT,
                    mediaType TEXT,
                    mediaPath TEXT,
                    PRIMARY KEY (datasetVersion, id),
                    FOREIGN KEY (datasetVersion) REFERENCES question_datasets(version) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO question_datasets VALUES ('DEMO-legacy', 'DEMO', 'DEMO migracja')")
            db.execSQL(
                """
                INSERT INTO questions (datasetVersion, id, category, prompt, points, correctAnswer, mediaType, mediaPath)
                VALUES ('DEMO-legacy', 'DEMO-legacy-01', 'B', 'DEMO pytanie', 2, 'NO', 'IMAGE', 'imagery/DEMO.JPG')
                """.trimIndent(),
            )
            db.version = 1
        }
    }

    private fun demoCatalog() = CatalogBundle(
        datasetVersion = "DEMO-2026",
        category = "B",
        source = "DEMO",
        sourceReference = "DEMO — test wewnętrzny",
        sourceSha256 = "a".repeat(64),
        questions = listOf(
            CatalogQuestion(
                id = "DEMO-01",
                prompt = "DEMO — treść testowa",
                points = 1,
                type = "BASIC",
                correctAnswer = "YES",
                optionA = null,
                optionB = null,
                optionC = null,
                categories = listOf("B", "B1"),
                translations = listOf(CatalogTranslation("EN", "DEMO prompt", null, null, null)),
                media = listOf(
                    CatalogMedia("QUESTION", "DEMO zdjęcie.JPG", "media/demo.jpg"),
                    CatalogMedia("PJM_QUESTION", "DEMO PJM.wmv", null),
                ),
            ),
        ),
    )

    private companion object {
        val HASH = "b".repeat(64)
    }
}
