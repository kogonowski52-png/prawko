package pl.prawko.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import pl.prawko.app.data.importing.installBundledCatalog
import pl.prawko.app.data.local.MIGRATION_1_2
import pl.prawko.app.data.local.MIGRATION_2_3
import pl.prawko.app.data.local.RoomExamRepository
import pl.prawko.app.data.local.RoomStudyProgressRepository
import pl.prawko.app.data.media.LocalMediaRepository
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.domain.PreferencesRepository
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.StudyProgressRepository
import pl.prawko.app.domain.exam.ExamRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy {
        Room.databaseBuilder(appContext, PrawkoDatabase::class.java, "prawko.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
    private val catalogInitialization by lazy {
        applicationScope.async { installBundledCatalog(appContext.assets, database) }
    }

    val questionRepository: QuestionRepository by lazy {
        val local = RoomQuestionRepository(database.questionDao())
        selectBuildQuestionRepository(object : QuestionRepository {
            override val availability = afterCatalogInitialization(local.availability)

            override val activeDatasetVersion = afterCatalogInitialization(local.activeDatasetVersion)

            override fun observeQuestions(datasetVersion: String) =
                afterCatalogInitialization(local.observeQuestions(datasetVersion))
        })
    }

    val studyProgressRepository: StudyProgressRepository by lazy { RoomStudyProgressRepository(database) }
    val examRepository: ExamRepository by lazy { RoomExamRepository(database) }
    internal val mediaRepository by lazy { LocalMediaRepository(appContext) }

    val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(appContext.prawkoPreferences)
    }

    private fun <T> afterCatalogInitialization(query: Flow<T>): Flow<T> = flow {
        catalogInitialization.await()
        emitAll(query)
    }
}
