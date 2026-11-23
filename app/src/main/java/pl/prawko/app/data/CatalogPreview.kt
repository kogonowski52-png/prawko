package pl.prawko.app.data

import androidx.compose.runtime.Composable
import pl.prawko.app.domain.QuestionRepository

internal class CatalogPreview(
    val questions: QuestionRepository,
    val settings: @Composable () -> Unit,
)
