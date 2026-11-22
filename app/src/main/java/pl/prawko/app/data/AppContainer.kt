package pl.prawko.app.data

import android.content.Context
import androidx.room.Room
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.domain.PreferencesRepository
import pl.prawko.app.domain.QuestionRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy {
        Room.databaseBuilder(appContext, PrawkoDatabase::class.java, "prawko.db").build()
    }

    val questionRepository: QuestionRepository by lazy {
        RoomQuestionRepository(database.questionDao())
    }

    val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(appContext.prawkoPreferences)
    }
}
