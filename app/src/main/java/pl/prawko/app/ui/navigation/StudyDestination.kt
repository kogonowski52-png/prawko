package pl.prawko.app.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.catch
import pl.prawko.app.domain.Question
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.study.StudyScreen

private sealed interface StudyCatalog {
    data object Loading : StudyCatalog
    data object Failed : StudyCatalog
    data class Loaded(val questions: List<Question>) : StudyCatalog
}

@Composable
internal fun StudyDestination(
    datasetVersion: String?,
    features: PrawkoFeatures,
    onBack: () -> Unit,
    onStats: () -> Unit,
    initialMistakeIds: Set<String>? = null,
) {
    if (datasetVersion == null) {
        StudyMessage(
            title = "Brak bazy pytań",
            description = "Do rozpoczęcia nauki potrzebny jest zweryfikowany katalog kategorii B.",
            onBack = onBack,
        )
        return
    }

    var retry by remember(datasetVersion) { mutableIntStateOf(0) }
    val catalog by produceState<StudyCatalog>(StudyCatalog.Loading, datasetVersion, features.questionRepository, retry) {
        value = StudyCatalog.Loading
        features.questionRepository.observeQuestions(datasetVersion)
            .catch { error ->
                Log.e("PrawkoStudy", "Nie udało się wczytać katalogu do nauki.", error)
                value = StudyCatalog.Failed
            }
            .collect { value = StudyCatalog.Loaded(it) }
    }
    when (val loaded = catalog) {
        StudyCatalog.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { stateDescription = "Wczytywanie pytań" })
        }
        StudyCatalog.Failed -> StudyMessage(
            title = "Nie udało się wczytać pytań",
            description = "Spróbuj ponownie.",
            onBack = onBack,
            onRetry = { retry++ },
        )
        is StudyCatalog.Loaded -> {
            if (loaded.questions.isEmpty()) {
                StudyMessage(
                    title = "Brak bazy pytań",
                    description = "Wybrana wersja katalogu jest niedostępna.",
                    onBack = onBack,
                )
            } else {
                StudyScreen(
                    questions = loaded.questions,
                    progressRepository = features.studyProgressRepository,
                    mediaRepository = features.mediaRepository,
                    isDemo = features.isDemo,
                    onBack = onBack,
                    onStats = onStats,
                    initialMistakeIds = initialMistakeIds,
                )
            }
        }
    }
}

@Composable
private fun StudyMessage(title: String, description: String, onBack: () -> Unit, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Nauka", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                    Text(description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (onRetry != null) {
                PrawkoPrimaryButton("Spróbuj ponownie", onRetry, Modifier.fillMaxWidth())
            }
            PrawkoPrimaryButton("Wróć", onBack, Modifier.fillMaxWidth(), symbol = PrawkoSymbol.ARROW_LEFT)
        }
    }
}
