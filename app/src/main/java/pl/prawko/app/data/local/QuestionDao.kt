package pl.prawko.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface QuestionDao {
    @Query(
        """
        SELECT d.version
        FROM active_question_datasets AS active
        JOIN question_datasets AS d ON d.version = active.datasetVersion
        WHERE active.category = :category
          AND d.source = :source AND d.selectedCategory = :category
          AND EXISTS (
              SELECT 1
              FROM question_categories AS c
              WHERE c.datasetVersion = d.version AND c.category = :category
          )
        """,
    )
    fun observeActiveVersion(
        category: String,
        source: DatasetSource = DatasetSource.VERIFIED_OFFICIAL,
    ): Flow<String?>

    @Query(
        """
        SELECT q.*
        FROM questions AS q
        JOIN question_datasets AS d ON d.version = q.datasetVersion
        JOIN question_categories AS c ON c.datasetVersion = q.datasetVersion AND c.questionId = q.id
        WHERE q.datasetVersion = :datasetVersion AND c.category = :category
          AND d.source = :source AND d.selectedCategory = :category
        ORDER BY q.id
        """,
    )
    fun observeQuestions(
        datasetVersion: String,
        category: String,
        source: DatasetSource,
    ): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM question_categories WHERE datasetVersion = :version ORDER BY category")
    suspend fun categories(version: String): List<QuestionCategoryEntity>

    @Query("SELECT * FROM question_translations WHERE datasetVersion = :version ORDER BY language")
    suspend fun translations(version: String): List<QuestionTranslationEntity>

    @Query("SELECT * FROM question_media WHERE datasetVersion = :version ORDER BY role")
    suspend fun media(version: String): List<QuestionMediaEntity>

    @Query("SELECT * FROM question_datasets WHERE version = :version")
    suspend fun dataset(version: String): QuestionDatasetEntity?

    @Query("SELECT COUNT(*) FROM questions WHERE datasetVersion = :version")
    suspend fun questionCount(version: String): Int

    @Query("SELECT COUNT(*) FROM question_media WHERE datasetVersion = :version AND assetPath IS NULL")
    suspend fun mediaWithoutPathCount(version: String): Int

    @Insert
    suspend fun insertDataset(dataset: QuestionDatasetEntity)

    @Insert
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert
    suspend fun insertCategories(categories: List<QuestionCategoryEntity>)

    @Insert
    suspend fun insertTranslations(translations: List<QuestionTranslationEntity>)

    @Insert
    suspend fun insertMedia(media: List<QuestionMediaEntity>)

    @Upsert
    suspend fun activateDataset(active: ActiveQuestionDatasetEntity)
}
