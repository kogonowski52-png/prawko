package pl.prawko.app.ui.exam

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamRules
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.theme.PrawkoPalette

@Composable
fun ExamScreen(
    questionRepository: QuestionRepository,
    examRepository: ExamRepository,
    mediaRepository: MediaRepository,
    isDemo: Boolean,
    onBack: () -> Unit,
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    category: LicenseCategory = LicenseCategory.B,
) {
    val application = LocalContext.current.applicationContext
    val factory = remember(questionRepository, examRepository, mediaRepository, category, application) {
        viewModelFactory {
            initializer {
                ExamViewModel(
                    questionRepository,
                    examRepository,
                    mediaRepository,
                    category,
                    AndroidExamTimeSource(application),
                    createSavedStateHandle(),
                )
            }
        }
    }
    val viewModel: ExamViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val completed = state as? ExamScreenState.Result
    LaunchedEffect(completed?.sessionId) {
        completed?.let { onResult(it.sessionId) }
    }

    ExamContent(
        state = state,
        mediaRepository = mediaRepository,
        category = category,
        isDemo = isDemo,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onStart = viewModel::start,
        onChoose = viewModel::choose,
        onSubmit = viewModel::submit,
        onAbandon = viewModel::abandon,
        onMediaUnavailable = viewModel::mediaUnavailable,
        modifier = modifier,
    )
}

@Composable
internal fun ExamContent(
    state: ExamScreenState,
    mediaRepository: MediaRepository,
    category: LicenseCategory,
    isDemo: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit,
    onChoose: (String, AnswerChoice) -> Unit,
    onSubmit: (String) -> Unit,
    onAbandon: () -> Unit,
    onMediaUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val running = state as? ExamScreenState.Running
    BackHandler(enabled = running != null) { confirmLeave = true }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (state) {
            is ExamScreenState.Running -> RunningExam(
                state = state,
                mediaRepository = mediaRepository,
                onLeave = { confirmLeave = true },
                onChoose = onChoose,
                onSubmit = onSubmit,
                onMediaUnavailable = onMediaUnavailable,
            )
            else -> ExamOverview(
                state = state,
                category = category,
                isDemo = isDemo,
                onBack = onBack,
                onRetry = onRetry,
                onStart = onStart,
            )
        }
    }

    if (confirmLeave && running != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Opuścić egzamin?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Po wyjściu czas będzie płynął dalej. Tę samą próbę możesz wznowić z ekranu egzaminu.")
                    TextButton(
                        onClick = {
                            confirmLeave = false
                            onAbandon()
                        },
                    ) {
                        Text("Przerwij próbę", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onBack()
                    },
                ) { Text("Opuść ekran") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Kontynuuj") }
            },
        )
    }
}

@Composable
private fun ExamOverview(
    state: ExamScreenState,
    category: LicenseCategory,
    isDemo: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ExamToolbar(onBack)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ExamHeading(if (isDemo) "Egzamin DEMO" else "Egzamin próbny")
            Text(
                text = when (state) {
                    is ExamScreenState.Intro -> "Kategoria ${category.name} · Katalog ${state.datasetVersion}"
                    else -> "Kategoria ${category.name}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state) {
            ExamScreenState.Loading -> ExamNotice("Przygotowywanie egzaminu") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Sprawdzamy dostępność pytań i mediów.")
                }
            }
            ExamScreenState.MissingCatalog -> ExamNotice("Brak bazy pytań") {
                Text("Aby rozpocząć egzamin, potrzebujesz aktywnego katalogu pytań dla tej kategorii.")
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.Start)) { Text("Sprawdź ponownie") }
            }
            ExamScreenState.Failed -> ExamNotice("Nie udało się otworzyć egzaminu") {
                Text("Spróbuj ponownie. Zatwierdzone odpowiedzi są zapisywane w trakcie próby.")
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.Start)) { Text("Spróbuj ponownie") }
            }
            is ExamScreenState.Intro -> ExamPlan(state, onRetry, onStart)
            is ExamScreenState.Result -> ExamNotice("Wynik zapisany") {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            is ExamScreenState.Running -> Unit
        }
    }
}

@Composable
private fun ExamPlan(state: ExamScreenState.Intro, onRetry: () -> Unit, onStart: () -> Unit) {
    Surface(
        color = PrawkoPalette.Hero,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrawkoIcon(PrawkoSymbol.EXAM, tint = PrawkoPalette.Mint)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExamMetric(ExamRules.QUESTION_COUNT.toString(), "pytania", Modifier.weight(1f))
                ExamMetric((ExamRules.TOTAL_TIME_MS / 60_000).toString(), "minut", Modifier.weight(1f))
                ExamMetric("${ExamRules.PASS_SCORE}/${ExamRules.MAX_SCORE}", "próg punktów", Modifier.weight(1.25f))
            }
        }
    }
    if (state.shortages.isEmpty()) {
        ExamNotice("Przed rozpoczęciem") {
            Text("Na pytanie podstawowe masz 20 sekund na zapoznanie się z treścią i 15 sekund na odpowiedź. Na specjalistyczne masz 50 sekund.")
            Text("Po zatwierdzeniu przejdziesz dalej. Do poprzednich pytań nie można wrócić.")
        }
    } else {
        ExamNotice("Za mało gotowych pytań") {
            if (state.shortages.any { it.unavailableMedia > 0 }) {
                Text("Brakuje zdjęć lub filmów do skompletowania pytań. Sprawdź paczki multimediów w Ustawieniach.")
            } else {
                Text("Egzamin uruchomi się, gdy katalog będzie zawierał wystarczającą liczbę pytań w poniższych grupach.")
            }
            state.shortages.forEach { shortage ->
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val type = if (shortage.type == QuestionType.BASIC) "Podstawowe" else "Specjalistyczne"
                    Text("$type · ${shortage.points} pkt", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Gotowych ${shortage.available} z ${shortage.required}. Brakuje ${shortage.missing}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (shortage.unavailableMedia > 0) {
                        Text(
                            "Z niedostępnymi mediami: ${shortage.unavailableMedia}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Button(
        onClick = onStart,
        enabled = state.shortages.isEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text("Rozpocznij egzamin")
    }
    if (state.shortages.isNotEmpty()) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Sprawdź ponownie")
        }
    }
}

@Composable
private fun ExamMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = PrawkoPalette.Mint)
        Text(label, style = MaterialTheme.typography.bodySmall, color = PrawkoPalette.HeroMuted)
    }
}

@Composable
private fun ExamToolbar(onBack: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
        IconButton(onClick = onBack) {
            PrawkoIcon(PrawkoSymbol.ARROW_LEFT, contentDescription = "Wróć")
        }
    }
}

@Composable
private fun ExamHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ExamNotice(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
