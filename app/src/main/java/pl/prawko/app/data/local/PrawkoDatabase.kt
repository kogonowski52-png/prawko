package pl.prawko.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionDatasetEntity::class,
        ActiveQuestionDatasetEntity::class,
        QuestionEntity::class,
        QuestionCategoryEntity::class,
        QuestionTranslationEntity::class,
        QuestionMediaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
internal abstract class PrawkoDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
}
