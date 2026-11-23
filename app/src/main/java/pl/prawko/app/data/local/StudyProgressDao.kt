package pl.prawko.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

internal data class StudyProgressRow(
    val questionId: String,
    val attempts: Int,
    val correctAttempts: Int,
    val lastCorrect: Boolean?,
    val isFavorite: Boolean,
)

@Dao
internal interface StudyProgressDao {
    @Query(
        """
        WITH touched AS (
            SELECT questionId FROM study_answers WHERE datasetVersion = :datasetVersion
            UNION
            SELECT questionId FROM study_favorites WHERE datasetVersion = :datasetVersion
        )
        SELECT
            touched.questionId,
            COUNT(a.sequence) AS attempts,
            SUM(CASE WHEN a.correct THEN 1 ELSE 0 END) AS correctAttempts,
            (
                SELECT latest.correct FROM study_answers AS latest
                WHERE latest.datasetVersion = :datasetVersion AND latest.questionId = touched.questionId
                ORDER BY latest.sequence DESC LIMIT 1
            ) AS lastCorrect,
            EXISTS (
                SELECT 1 FROM study_favorites AS favorite
                WHERE favorite.datasetVersion = :datasetVersion AND favorite.questionId = touched.questionId
            ) AS isFavorite
        FROM touched
        LEFT JOIN study_answers AS a ON a.datasetVersion = :datasetVersion AND a.questionId = touched.questionId
        GROUP BY touched.questionId
        """,
    )
    fun observeProgress(datasetVersion: String): Flow<List<StudyProgressRow>>

    @Query("SELECT * FROM study_answers WHERE attemptId = :attemptId")
    suspend fun recordedAnswer(attemptId: String): StudyAnswerEntity?

    @Insert
    suspend fun recordAnswer(answer: StudyAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: StudyFavoriteEntity)

    @Query("DELETE FROM study_favorites WHERE datasetVersion = :datasetVersion AND questionId = :questionId")
    suspend fun removeFavorite(datasetVersion: String, questionId: String)
}
