package pl.prawko.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.prawko.app.data.importing.LocalCatalogImportResult
import pl.prawko.app.data.importing.LocalCatalogRepository
import pl.prawko.app.data.importing.LocalCatalogSummary
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSectionLabel
import pl.prawko.app.ui.components.PrawkoSymbol

@Composable
internal fun LocalCatalogSettings(repository: LocalCatalogRepository) {
    val factory = remember(repository) { viewModelFactory { initializer { LocalCatalogViewModel(repository) } } }
    val viewModel: LocalCatalogViewModel = viewModel(factory = factory)
    val activeCatalog by viewModel.activeCatalog.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importDocument)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrawkoSectionLabel("Katalog lokalny")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CatalogDetails(activeCatalog)
                PrawkoPrimaryButton(
                    text = "Wczytaj lokalny katalog (JSON)",
                    onClick = { picker.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importState.reading,
                    symbol = PrawkoSymbol.ARCHIVE,
                )
                if (importState.reading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "Odczytywanie i sprawdzanie katalogu…",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                importState.result?.let { result ->
                    Text(
                        result.message(),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result is LocalCatalogImportResult.Failure) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogDetails(catalog: LocalCatalogSummary?) {
    if (catalog == null) {
        Text("Brak wczytanego katalogu", style = MaterialTheme.typography.titleSmall)
        Text(
            "Wybierz plik questions.json kategorii B zapisany na telefonie. Katalog zostanie zapisany w tej aplikacji testowej.",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            catalog.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Wersja ${catalog.version} · kategoria B", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Pytania w katalogu · ${catalog.questionCount}",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            "Źródło DEMO · lokalny plik",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        if (catalog.mediaWithoutPath > 0) {
            "Odwołania bez ścieżki do zdjęcia lub filmu · ${catalog.mediaWithoutPath}. " +
                "Przy tych pytaniach sama treść może nie wystarczyć."
        } else {
            "Jeśli pytanie wymaga zdjęcia lub filmu, odpowiedni plik musi być dostępny w paczce multimediów."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun LocalCatalogImportResult.message(): String = when (this) {
    is LocalCatalogImportResult.Imported -> if (isNew) {
        "Wczytano $fileName. Aktywna wersja to $version."
    } else {
        "Katalog $fileName jest już zapisany. Aktywna wersja to $version."
    }
    LocalCatalogImportResult.Failure.UNREADABLE -> "Nie udało się odczytać pliku. Zapisz go na telefonie i wybierz ponownie."
    LocalCatalogImportResult.Failure.TOO_LARGE -> "Plik przekracza limit 32 MiB."
    LocalCatalogImportResult.Failure.INVALID_DATA -> "Plik nie jest poprawnym katalogiem questions.json z importera Prawko."
    LocalCatalogImportResult.Failure.WRONG_CATEGORY -> "Ta aplikacja testowa obsługuje katalogi kategorii B."
    LocalCatalogImportResult.Failure.WRONG_SOURCE -> "Wybierz katalog testowy zapisany przez importer ze źródłem DEMO."
    LocalCatalogImportResult.Failure.VERSION_CONFLICT ->
        "Katalog o tej wersji ma inną zawartość. Wygeneruj plik z nową wersją albo wyczyść dane Prawko Katalog TEST."
    LocalCatalogImportResult.Failure.DATABASE_ERROR -> "Nie udało się zapisać katalogu na telefonie."
}

private data class LocalCatalogImportState(
    val reading: Boolean = false,
    val result: LocalCatalogImportResult? = null,
)

private class LocalCatalogViewModel(private val repository: LocalCatalogRepository) : ViewModel() {
    val activeCatalog = repository.activeCatalog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableImportState = MutableStateFlow(LocalCatalogImportState())
    val importState = mutableImportState.asStateFlow()

    fun importDocument(uri: Uri) {
        if (mutableImportState.value.reading) return
        mutableImportState.value = LocalCatalogImportState(reading = true)
        viewModelScope.launch {
            mutableImportState.value = LocalCatalogImportState(result = repository.importDocument(uri))
        }
    }
}
