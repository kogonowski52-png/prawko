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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.LicenseCategory
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.choices
import pl.prawko.app.domain.exam.ExamPhase
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamRules
import pl.prawko.app.domain.textFor
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.media.QuestionMediaContent
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
                isDemo = isDemo,
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
        ExamToolbar("Wróć", isDemo, onBack)
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
            is ExamScreenState.Intro -> ExamPlan(state, isDemo, onRetry, onStart)
            is ExamScreenState.Result -> ExamNotice("Wynik zapisany") {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            is ExamScreenState.Running -> Unit
        }
    }
}

@Composable
private fun ExamPlan(state: ExamScreenState.Intro, isDemo: Boolean, onRetry: () -> Unit, onStart: () -> Unit) {
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
    if (isDemo) {
        Text(
            "DEMO zawiera wyłącznie syntetyczne pytania treningowe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.shortages.isEmpty()) {
        ExamNotice("Przed rozpoczęciem") {
            Text("Na pytanie podstawowe masz 20 sekund na zapoznanie się z treścią i 15 sekund na odpowiedź. Na specjalistyczne masz 50 sekund.")
            Text("Po zatwierdzeniu przejdziesz dalej. Do poprzednich pytań nie można wrócić.")
        }
    } else {
        ExamNotice("Za mało gotowych pytań") {
            Text("Egzamin uruchomi się po uzupełnieniu poniższych grup. Pytania z brakującymi mediami są pomijane.")
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
private fun ExamToolbar(backDescription: String, isDemo: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = onBack) {
                PrawkoIcon(PrawkoSymbol.ARROW_LEFT, contentDescription = backDescription)
            }
        }
        if (isDemo) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text("DEMO", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
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

@Composable
private fun RunningExam(
    state: ExamScreenState.Running,
    isDemo: Boolean,
    mediaRepository: MediaRepository,
    onLeave: () -> Unit,
    onChoose: (String, AnswerChoice) -> Unit,
    onSubmit: (String) -> Unit,
    onMediaUnavailable: (String) -> Unit,
) {
    val (session, question, clock) = state
    val phase = session.phase(clock)
    val answering = phase == ExamPhase.ANSWERING
    val specialist = question.type == QuestionType.SPECIALIST
    var confirmSkip by rememberSaveable(session.id, question.id) { mutableStateOf(false) }

    Column(
        Modifier.widthIn(max = 740.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExamToolbar("Opuść egzamin", isDemo, onLeave)
        ExamProgress(state)
        key(question.id) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    question.prompt,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
                if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
                    val elapsed = (clock.elapsedRealtimeMs - session.questionStartedAtElapsedMs).coerceAtLeast(0)
                    QuestionMediaContent(
                        question = question,
                        repository = mediaRepository,
                        modifier = Modifier.fillMaxWidth(),
                        controls = false,
                        playing = phase == ExamPhase.READING || specialist && answering,
                        videoStartPositionMs = if (specialist) elapsed else elapsed.coerceAtMost(ExamRules.BASIC_READING_TIME_MS),
                        onUnavailable = { onMediaUnavailable(question.id) },
                    )
                }
                if (answering) {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        question.answers.choices.forEach { choice ->
                            ExamAnswerOption(
                                choice = choice,
                                label = question.answers.textFor(choice),
                                selected = session.selection == choice,
                                onClick = { onChoose(question.id, choice) },
                            )
                        }
                    }
                } else {
                    Text(
                        "Odpowiedzi będą dostępne po zakończeniu tej części.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (answering) {
            val selected = session.selection != null
            Button(
                onClick = { if (selected) onSubmit(question.id) else confirmSkip = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) {
                Text(
                    when {
                        !selected -> "Dalej bez odpowiedzi"
                        session.currentIndex == session.questions.lastIndex -> "Zatwierdź i zakończ"
                        else -> "Zatwierdź i dalej"
                    },
                )
            }
        }
    }

    if (confirmSkip && answering && session.selection == null) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            title = { Text("Pominąć pytanie?") },
            text = { Text("Brak odpowiedzi oznacza 0 punktów za to pytanie. Po przejściu dalej nie będzie można do niego wrócić.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSkip = false
                        onSubmit(question.id)
                    },
                ) { Text("Pomiń pytanie") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) { Text("Wybierz odpowiedź") }
            },
        )
    }
}

@Composable
private fun ExamProgress(state: ExamScreenState.Running) {
    val (session, question, clock) = state
    val totalTime = clockText(session.remainingMs(clock))
    val phaseTime = clockText(session.phaseRemainingMs(clock))
    val phase = if (session.phase(clock) == ExamPhase.READING) "Zapoznaj się z pytaniem" else "Udziel odpowiedzi"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = PrawkoPalette.Hero,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pytanie ${session.currentIndex + 1} z ${session.questions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    "Łącznie $totalTime",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrawkoPalette.Mint,
                    modifier = Modifier.semantics { contentDescription = "Czas do końca egzaminu $totalTime" },
                )
            }
            LinearProgressIndicator(
                progress = { (session.currentIndex + 1f) / session.questions.size },
                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                color = PrawkoPalette.Mint,
                trackColor = PrawkoPalette.HeroRaised,
            )
            Text(
                "${if (question.type == QuestionType.BASIC) "Podstawowe" else "Specjalistyczne"} · $phase · $phaseTime",
                style = MaterialTheme.typography.bodyMedium,
                color = PrawkoPalette.HeroMuted,
            )
        }
    }
}

@Composable
private fun ExamAnswerOption(choice: AnswerChoice, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (choice.label != label) {
                Text(choice.label, style = MaterialTheme.typography.labelLarge)
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun clockText(remainingMs: Long): String {
    val seconds = (remainingMs + 999) / 1_000
    return "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}
