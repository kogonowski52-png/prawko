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
        StudyAnswerEntity::class,
        StudyFavoriteEntity::class,
        ExamAttemptEntity::class,
        ExamAttemptQuestionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class PrawkoDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun studyProgressDao(): StudyProgressDao
    abstract fun examAttemptDao(): ExamAttemptDao
}
