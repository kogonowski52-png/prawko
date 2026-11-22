package pl.prawko.app.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.prawko.app.domain.LicenseCategory

@Dao
internal interface QuestionDao {
    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM questions AS q
            INNER JOIN question_datasets AS d ON d.version = q.datasetVersion
            WHERE q.category = :category AND d.source = :source
        )
        """,
    )
    fun observeAvailability(category: LicenseCategory, source: DatasetSource): Flow<Boolean>

    @Query(
        """
        SELECT q.*
        FROM questions AS q
        INNER JOIN question_datasets AS d ON d.version = q.datasetVersion
        WHERE q.datasetVersion = :datasetVersion AND q.category = :category AND d.source = :source
        ORDER BY q.id
        """,
    )
    fun observeQuestions(
        datasetVersion: String,
        category: LicenseCategory,
        source: DatasetSource,
    ): Flow<List<QuestionEntity>>
}
