package pl.prawko.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.prawko.app.data.local.DatasetSource
import pl.prawko.app.data.local.MIGRATION_2_3
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.data.local.RoomExamRepository
import pl.prawko.app.data.local.RoomStudyProgressRepository
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.StudyQuestionProgress
import pl.prawko.app.domain.exam.ExamAnswer
import pl.prawko.app.domain.exam.ExamCompletion
import pl.prawko.app.domain.exam.ExamQuestion
import pl.prawko.app.domain.exam.ExamSession

@RunWith(AndroidJUnit4::class)
class RoomProgressRepositoryTest {
    private val appContext get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun studyAttemptsAndFavoritesSurviveReopeningAndStayScopedToCatalogVersion() = runBlocking {
        val name = "prawko-study-${UUID.randomUUID()}.db"
        val question = demoQuestion()
        try {
            openDatabase(name).useDatabase { database ->
                val progress = RoomStudyProgressRepository(database)
                assertTrue(progress.observeProgress(question.datasetVersion).first().isEmpty())
                progress.setFavorite(question.datasetVersion, question.id, true)
                val unrelatedFavorite = "DEMO-unanswered"
                progress.setFavorite(question.datasetVersion, unrelatedFavorite, true)
                assertEquals(
                    StudyQuestionProgress(0, 0, null, true),
                    progress.observeProgress(question.datasetVersion).first().getValue(unrelatedFavorite),
                )
                progress.recordAnswer(question, AnswerChoice.NO, "DEMO-study-first")
                progress.recordAnswer(question, AnswerChoice.NO, "DEMO-study-first")
                progress.recordAnswer(question, AnswerChoice.YES, "DEMO-study-second")
                val laterVersion = question.copy(datasetVersion = "DEMO-next")
                progress.recordAnswer(laterVersion, AnswerChoice.NO, "DEMO-study-later-version")
            }
            openDatabase(name).useDatabase { database ->
                val progress = RoomStudyProgressRepository(database)
                assertEquals(
                    StudyQuestionProgress(2, 1, true, true),
                    progress.observeProgress(question.datasetVersion).first().getValue(question.id),
                )
                assertEquals(
                    StudyQuestionProgress(1, 0, false, false),
                    progress.observeProgress("DEMO-next").first().getValue(question.id),
                )
                progress.setFavorite(question.datasetVersion, question.id, false)
                assertFalse(progress.observeProgress(question.datasetVersion).first().getValue(question.id).isFavorite)
            }
        } finally {
            appContext.deleteDatabase(name)
        }
    }

    @Test
    fun examTransitionsRestoreAndHistoryIsAtomicAndIdempotent() = runBlocking {
        val name = "prawko-exam-${UUID.randomUUID()}.db"
        try {
            val inProgress = openDatabase(name).useDatabase { database ->
                val exams = RoomExamRepository(database)
                val first = demoSession("DEMO-attempt-one")
                val second = demoSession("DEMO-attempt-two")
                val winners = listOf(
                    async { exams.start(first) },
                    async { exams.start(second) },
                ).awaitAll()
                assertEquals(1, winners.count { it })

                val initial = requireNotNull(exams.activeSession.first())
                val selected = initial.copy(selection = AnswerChoice.YES)
                assertTrue(exams.save(initial, selected))
                assertTrue(exams.save(initial, selected))
                assertFalse(exams.save(initial, initial.copy(selection = AnswerChoice.NO)))
                val committed = selected.copy(
                    answers = listOf(ExamAnswer(initial.questions.first().questionId, AnswerChoice.YES)),
                    selection = null,
                    questionStartedAtElapsedMs = 22_000,
                )
                assertTrue(exams.save(selected, committed))
                committed
            }

            openDatabase(name).useDatabase { database ->
                val exams = RoomExamRepository(database)
                assertEquals(inProgress, exams.activeSession.first())
                assertEquals(inProgress, exams.observeSession(inProgress.id).first())
                assertTrue(exams.observeResults().first().isEmpty())

                val ended = inProgress.copy(
                    answers = inProgress.answers + ExamAnswer(inProgress.questions.last().questionId, null),
                    completion = ExamCompletion.TIME_EXPIRED,
                    completedAtEpochMs = 200_000,
                )
                assertTrue(exams.save(inProgress, ended))
                assertTrue(exams.save(inProgress, ended))
                assertFalse(exams.save(inProgress, ended.copy(completion = ExamCompletion.ABANDONED)))
                assertNull(exams.activeSession.first())
                val result = exams.observeResults().first().single()
                assertEquals(ended, result.session)
                assertEquals(3, result.session.score)
                assertEquals(200_000L, result.completedAtEpochMillis)
                assertTrue(exams.start(demoSession("DEMO-attempt-three")))
            }
        } finally {
            appContext.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFromVersionTwoRetainsCatalogAndSupportsProgressTables() = runBlocking {
        val name = "prawko-migration-v2-${UUID.randomUUID()}.db"
        try {
            withContext(Dispatchers.IO) { createVersionTwoDatabase(name) }
            openDatabase(name).useDatabase { database ->
                val dao = database.questionDao()
                val dataset = requireNotNull(dao.dataset("DEMO-from-v2"))
                assertEquals(DatasetSource.DEMO, dataset.source)
                val question = dao.observeQuestions(dataset.version, "B", DatasetSource.DEMO).first().single()
                assertEquals("DEMO-existing", question.id)
                assertEquals("DEMO — zachowane pytanie", question.prompt)
                assertEquals("B", dao.categories(dataset.version).single().category)

                val study = RoomStudyProgressRepository(database)
                val key = demoQuestion().copy(id = question.id, datasetVersion = dataset.version)
                study.recordAnswer(key, AnswerChoice.YES, "DEMO-after-migration")
                assertEquals(
                    StudyQuestionProgress(1, 1, true, false),
                    study.observeProgress(dataset.version).first().getValue(question.id),
                )
                val exams = RoomExamRepository(database)
                assertTrue(exams.start(demoSession("DEMO-migrated-attempt")))
                assertEquals("DEMO-migrated-attempt", exams.activeSession.first()?.id)
            }
        } finally {
            appContext.deleteDatabase(name)
        }
    }

    private fun openDatabase(name: String) =
        Room.databaseBuilder(appContext, PrawkoDatabase::class.java, name)
            .addMigrations(MIGRATION_2_3)
            .build()

    private inline fun <T> PrawkoDatabase.useDatabase(block: (PrawkoDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    private fun createVersionTwoDatabase(name: String) {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("pl.prawko.app.data.local.PrawkoDatabase/2.json")
            .bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }
        val file = appContext.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (item in 0 until indices.length()) {
                    database.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            for (index in 0 until setupQueries.length()) database.execSQL(setupQueries.getString(index))
            database.execSQL(
                """
                INSERT INTO question_datasets (version, source, sourceReference, selectedCategory)
                VALUES ('DEMO-from-v2', 'DEMO', 'DEMO — test migracji', 'B')
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO questions (datasetVersion, id, type, prompt, points, correctAnswer)
                VALUES ('DEMO-from-v2', 'DEMO-existing', 'BASIC', 'DEMO — zachowane pytanie', 1, 'YES')
                """.trimIndent(),
            )
            database.execSQL("INSERT INTO question_categories VALUES ('DEMO-from-v2', 'DEMO-existing', 'B')")
            database.version = 2
        }
    }

    private fun demoQuestion() = Question(
        id = "DEMO-study-01",
        datasetVersion = "DEMO-stage-2",
        category = LicenseCategory.B,
        prompt = "DEMO — treść szkoleniowa",
        points = 3,
        answers = QuestionAnswers.YesNo(correctIsYes = true),
    )

    private fun demoSession(id: String) = ExamSession(
        id = id,
        datasetVersion = "DEMO-stage-2",
        category = LicenseCategory.B,
        bootId = "DEMO-boot",
        startedAtElapsedMs = 1_000,
        startedAtEpochMs = 100_000,
        questionStartedAtElapsedMs = 1_000,
        questions = listOf(
            ExamQuestion("DEMO-question-01", QuestionType.BASIC, 3, AnswerChoice.YES),
            ExamQuestion("DEMO-question-02", QuestionType.SPECIALIST, 2, AnswerChoice.A),
        ),
    )
}
