package pl.prawko.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionDatasetEntity::class, QuestionEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class PrawkoDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
}
