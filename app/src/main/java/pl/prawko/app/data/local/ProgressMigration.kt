package pl.prawko.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE study_answers (
                sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                attemptId TEXT NOT NULL,
                datasetVersion TEXT NOT NULL,
                questionId TEXT NOT NULL,
                choice TEXT NOT NULL,
                correct INTEGER NOT NULL,
                answeredAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX index_study_answers_attemptId ON study_answers(attemptId)")
        db.execSQL("CREATE INDEX index_study_answers_datasetVersion_questionId ON study_answers(datasetVersion, questionId)")
        db.execSQL(
            """
            CREATE TABLE study_favorites (
                datasetVersion TEXT NOT NULL,
                questionId TEXT NOT NULL,
                PRIMARY KEY (datasetVersion, questionId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE exam_attempts (
                id TEXT NOT NULL PRIMARY KEY,
                datasetVersion TEXT NOT NULL,
                category TEXT NOT NULL,
                bootId TEXT NOT NULL,
                startedAtElapsedMs INTEGER NOT NULL,
                startedAtEpochMs INTEGER NOT NULL,
                questionStartedAtElapsedMs INTEGER NOT NULL,
                selection TEXT,
                completion TEXT,
                completedAtEpochMs INTEGER,
                activeSlot INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX index_exam_attempts_activeSlot ON exam_attempts(activeSlot)")
        db.execSQL("CREATE INDEX index_exam_attempts_completedAtEpochMs ON exam_attempts(completedAtEpochMs)")
        db.execSQL(
            """
            CREATE TABLE exam_attempt_questions (
                attemptId TEXT NOT NULL,
                position INTEGER NOT NULL,
                questionId TEXT NOT NULL,
                type TEXT NOT NULL,
                points INTEGER NOT NULL,
                correctChoice TEXT NOT NULL,
                isAnswered INTEGER NOT NULL,
                answerChoice TEXT,
                PRIMARY KEY (attemptId, position),
                FOREIGN KEY (attemptId) REFERENCES exam_attempts(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX index_exam_attempt_questions_attemptId_questionId
            ON exam_attempt_questions(attemptId, questionId)
            """.trimIndent(),
        )
    }
}
