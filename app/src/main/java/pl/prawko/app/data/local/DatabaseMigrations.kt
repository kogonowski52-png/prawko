package pl.prawko.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE question_datasets ADD COLUMN selectedCategory TEXT")
        db.execSQL("ALTER TABLE question_datasets ADD COLUMN sourceSha256 TEXT")
        db.execSQL("ALTER TABLE question_datasets ADD COLUMN payloadSha256 TEXT")
        // The v1 enum permitted only category B.
        db.execSQL("UPDATE question_datasets SET selectedCategory = 'B'")
        db.execSQL("ALTER TABLE questions RENAME TO questions_v1")

        db.execSQL(
            """
            CREATE TABLE questions (
                datasetVersion TEXT NOT NULL,
                id TEXT NOT NULL,
                type TEXT NOT NULL,
                prompt TEXT NOT NULL,
                points INTEGER NOT NULL,
                correctAnswer TEXT NOT NULL,
                optionA TEXT,
                optionB TEXT,
                optionC TEXT,
                PRIMARY KEY (datasetVersion, id),
                FOREIGN KEY (datasetVersion) REFERENCES question_datasets(version) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE question_categories (
                datasetVersion TEXT NOT NULL,
                questionId TEXT NOT NULL,
                category TEXT NOT NULL,
                PRIMARY KEY (datasetVersion, questionId, category),
                FOREIGN KEY (datasetVersion, questionId) REFERENCES questions(datasetVersion, id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE question_translations (
                datasetVersion TEXT NOT NULL,
                questionId TEXT NOT NULL,
                language TEXT NOT NULL,
                prompt TEXT,
                optionA TEXT,
                optionB TEXT,
                optionC TEXT,
                PRIMARY KEY (datasetVersion, questionId, language),
                FOREIGN KEY (datasetVersion, questionId) REFERENCES questions(datasetVersion, id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE question_media (
                datasetVersion TEXT NOT NULL,
                questionId TEXT NOT NULL,
                role TEXT NOT NULL,
                originalName TEXT NOT NULL,
                assetPath TEXT,
                type TEXT,
                PRIMARY KEY (datasetVersion, questionId, role),
                FOREIGN KEY (datasetVersion, questionId) REFERENCES questions(datasetVersion, id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE active_question_datasets (
                category TEXT NOT NULL PRIMARY KEY,
                datasetVersion TEXT NOT NULL,
                FOREIGN KEY (datasetVersion) REFERENCES question_datasets(version) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_active_question_datasets_datasetVersion ON active_question_datasets(datasetVersion)")

        db.execSQL(
            """
            INSERT INTO questions (datasetVersion, id, type, prompt, points, correctAnswer, optionA, optionB, optionC)
            SELECT datasetVersion, id,
                   CASE WHEN correctAnswer IN ('YES', 'NO') THEN 'BASIC' ELSE 'SPECIALIST' END,
                   prompt, points, correctAnswer, optionA, optionB, optionC
            FROM questions_v1
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO question_categories (datasetVersion, questionId, category)
            SELECT datasetVersion, id, category FROM questions_v1
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO question_media (datasetVersion, questionId, role, originalName, assetPath, type)
            SELECT datasetVersion, id, 'QUESTION', mediaPath, mediaPath, mediaType
            FROM questions_v1
            WHERE mediaPath IS NOT NULL AND TRIM(mediaPath) <> ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO active_question_datasets (category, datasetVersion)
            SELECT 'B', d.version
            FROM question_datasets AS d
            WHERE d.source = 'VERIFIED_OFFICIAL'
              AND EXISTS (
                  SELECT 1 FROM question_categories AS c
                  WHERE c.datasetVersion = d.version AND c.category = 'B'
              )
            ORDER BY d.version DESC
            LIMIT 1
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE questions_v1")
    }
}
