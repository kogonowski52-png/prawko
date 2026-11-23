package pl.prawko.app.ui.settings

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSymbol

private val Sha256 = Regex("[a-fA-F0-9]{64}")

@Composable
internal fun MediaPackScreen(
    datasetVersion: String?,
    installMediaPack: suspend (String, InputStream, String) -> Int,
    onBack: () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var checksum by rememberSaveable(datasetVersion) { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val selectArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val version = datasetVersion
        val sha256 = checksum.trim()
        if (uri != null && version != null && Sha256.matches(sha256) && !installing) {
            installing = true
            feedback = null
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        val input = resolver.openInputStream(uri) ?: throw IOException("Archiwum jest niedostępne.")
                        input.use { installMediaPack(version, it, sha256) }
                    }
                    failed = false
                    feedback = "Paczka gotowa. Zapisane pliki: $count."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("PrawkoMedia", "Nie udało się zainstalować paczki.", error)
                    failed = true
                    feedback = "Sprawdź sumę SHA-256, wersję katalogu i zawartość paczki ZIP."
                } finally {
                    installing = false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    PrawkoIcon(PrawkoSymbol.ARROW_LEFT, contentDescription = "Wróć")
                }
                Text(
                    "Paczki multimediów",
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            if (datasetVersion == null) {
                Text("Najpierw wczytaj katalog pytań, do którego należy paczka.")
            } else {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Aktywny katalog", style = MaterialTheme.typography.labelLarge)
                        Text(datasetVersion, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Użyj sumy kontrolnej z pliku SHA256SUMS, a następnie wybierz odpowiadającą jej paczkę ZIP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = checksum,
                    onValueChange = { checksum = it.take(128) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing,
                    label = { Text("Suma SHA-256") },
                    supportingText = { Text("64 znaki z pliku SHA256SUMS") },
                    singleLine = true,
                )
                PrawkoPrimaryButton(
                    text = "Wybierz paczkę ZIP",
                    onClick = { selectArchive.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = Sha256.matches(checksum.trim()) && !installing,
                    symbol = PrawkoSymbol.ARCHIVE,
                )
                if (installing) LinearProgressIndicator(Modifier.fillMaxWidth())
                feedback?.let {
                    Text(
                        it,
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
