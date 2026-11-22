package pl.prawko.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.domain.QuestionAvailability

@RunWith(AndroidJUnit4::class)
class RoomQuestionRepositoryTest {
    @Test
    fun emptyDatabaseAndDemoDatasetBothKeepOfficialQuestionsUnavailable() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PrawkoDatabase::class.java,
        ).build()

        try {
            val repository = RoomQuestionRepository(database.questionDao())
            assertEquals(QuestionAvailability.UNAVAILABLE, repository.availability.first())

            withContext(Dispatchers.IO) {
                val sqlite = database.openHelper.writableDatabase
                sqlite.execSQL(
                    "INSERT INTO question_datasets (version, source, sourceReference) VALUES (?, ?, ?)",
                    arrayOf("DEMO-test", "DEMO", "DEMO — test wewnętrzny"),
                )
                sqlite.execSQL(
                    """
                    INSERT INTO questions (datasetVersion, id, category, prompt, points, correctAnswer)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>("DEMO-test", "DEMO-01", "B", "DEMO — treść testowa", 1, "YES"),
                )
            }

            assertEquals(QuestionAvailability.UNAVAILABLE, repository.availability.first())
            assertTrue(repository.observeQuestions("DEMO-test").first().isEmpty())
        } finally {
            database.close()
        }
    }
}
