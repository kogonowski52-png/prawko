package pl.prawko.app.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.StudyProgressRepository
import pl.prawko.app.domain.choices
import pl.prawko.app.domain.correctChoice
import pl.prawko.app.domain.textFor
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSectionLabel
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.media.QuestionMediaContent
import pl.prawko.app.ui.theme.PrawkoPalette

@Composable
fun StudyScreen(
    questions: List<Question>,
    progressRepository: StudyProgressRepository,
    mediaRepository: MediaRepository,
    isDemo: Boolean,
    onBack: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
    initialMistakeIds: Set<String>? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val content = Modifier.widthIn(max = 680.dp).fillMaxWidth()
        if (questions.isEmpty()) {
            StudyMessage(
                title = "Brak bazy pytań",
                detail = "Do nauki potrzebny jest katalog pytań.",
                action = "Wróć",
                onAction = onBack,
                modifier = content,
            )
        } else {
            val factory = remember(questions, initialMistakeIds, progressRepository) {
                viewModelFactory {
                    initializer {
                        StudyViewModel(questions, initialMistakeIds, progressRepository, createSavedStateHandle())
                    }
                }
            }
            val model: StudyViewModel = viewModel(
                key = "study-${questions.first().datasetVersion}",
                factory = factory,
            )
            StudyContent(model, mediaRepository, isDemo, initialMistakeIds != null, onBack, onStats, content)
        }
    }
}

@Composable
private fun StudyContent(
    model: StudyViewModel,
    mediaRepository: MediaRepository,
    isDemo: Boolean,
    reviewExam: Boolean,
    onBack: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val series = state.series
    var askToLeave by rememberSaveable { mutableStateOf(false) }

    val closeSeries = {
        if (state.saving) {
            Unit
        } else if (series != null && !series.completed) {
            askToLeave = true
        } else {
            model.clearSeries()
        }
    }
    BackHandler(enabled = series != null) { closeSeries() }

    if (askToLeave) {
        AlertDialog(
            onDismissRequest = { askToLeave = false },
            title = { Text("Zakończyć tę serię?") },
            text = { Text("Sprawdzone odpowiedzi i ulubione pytania pozostaną zapisane.") },
            confirmButton = {
                TextButton(onClick = {
                    model.clearSeries()
                    askToLeave = false
                }) { Text("Zakończ serię") }
            },
            dismissButton = { TextButton(onClick = { askToLeave = false }) { Text("Kontynuuj naukę") } },
        )
    }

    when {
        series != null && series.completed -> StudyComplete(
            series = series,
            isDemo = isDemo,
            onBack = model::clearSeries,
            onStats = onStats,
            modifier = modifier,
        )
        series != null -> model.currentQuestion(series)?.let { question ->
            key(question.id, series.index) {
                StudyQuestion(
                    question = question,
                    series = series,
                    state = state,
                    mediaRepository = mediaRepository,
                    isDemo = isDemo,
                    onBack = closeSeries,
                    onSelect = model::select,
                    onCheck = model::checkAnswer,
                    onNext = model::next,
                    onFavorite = { model.toggleFavorite(question) },
                    modifier = modifier,
                )
            }
        }
        state.progressFailed -> StudyMessage(
            title = "Postęp chwilowo niedostępny",
            detail = "Spróbuj ponownie wczytać postęp nauki.",
            action = "Spróbuj ponownie",
            onAction = model::observeProgress,
            modifier = modifier,
            onBack = onBack,
        )
        !state.progressReady -> Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StudyHeader("Nauka", onBack)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text("Wczytuję postęp nauki")
            }
        }
        else -> StudySetup(
            pool = model.pool,
            state = state,
            isDemo = isDemo,
            reviewExam = reviewExam,
            onFilters = model::updateFilters,
            onStart = model::start,
            onBack = onBack,
            onStats = onStats,
            modifier = modifier,
        )
    }
}

@Composable
private fun StudySetup(
    pool: List<Question>,
    state: StudyState,
    isDemo: Boolean,
    reviewExam: Boolean,
    onFilters: (StudyFilters) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier,
) {
    val filters = state.filters
    val done = remember(pool, state.progress) { pool.count { (state.progress[it.id]?.attempts ?: 0) > 0 } }
    val remaining = remember(pool, filters, state.progress) {
        studyQuestionIndices(pool, state.progress, filters).size
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StudyHeader(if (reviewExam) "Powtórka błędów" else "Nauka pytań", onBack)
        Surface(
            color = PrawkoPalette.Hero,
            contentColor = Color.White,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isDemo) DemoStudyChip()
                Text(
                    text = "Przerobiono $done z ${pool.size} pytań",
                    style = MaterialTheme.typography.headlineSmall,
                )
                val progress = if (pool.isEmpty()) 0f else done.toFloat() / pool.size
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = PrawkoPalette.Mint,
                    trackColor = PrawkoPalette.HeroRaised,
                )
                TextButton(onClick = onStats, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                    Text("Zobacz statystyki", color = PrawkoPalette.Mint)
                    PrawkoIcon(
                        PrawkoSymbol.ARROW_RIGHT,
                        Modifier.padding(start = 8.dp).size(18.dp),
                        tint = PrawkoPalette.Mint,
                    )
                }
            }
        }
        StudyFiltersGroup(
            label = "Zestaw",
            values = listOf(
                StudyCollection.ALL to "Wszystkie pytania",
                StudyCollection.MISTAKES to "Błędy do powtórki",
                StudyCollection.FAVORITES to "Ulubione",
            ),
            selected = filters.collection,
            onSelect = { onFilters(filters.copy(collection = it)) },
        )
        StudyFiltersGroup(
            label = "Typ pytania",
            values = listOf(
                null to "Wszystkie typy",
                QuestionType.BASIC to "Podstawowe",
                QuestionType.SPECIALIST to "Specjalistyczne",
            ),
            selected = filters.type,
            onSelect = { onFilters(filters.copy(type = it)) },
        )
        StudyFiltersGroup(
            label = "Punktacja",
            values = listOf(null to "Każda", 1 to "1 pkt", 2 to "2 pkt", 3 to "3 pkt"),
            selected = filters.points,
            onSelect = { onFilters(filters.copy(points = it)) },
        )
        StudyFiltersGroup(
            label = "Kolejność",
            values = listOf(StudyOrder.SEQUENTIAL to "Po kolei", StudyOrder.RANDOM to "Losowo"),
            selected = filters.order,
            onSelect = { onFilters(filters.copy(order = it)) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (remaining == 0) {
            Text(
                if (pool.isEmpty()) "W tej wersji katalogu nie ma pytań do powtórki."
                else "Brak pytań spełniających wybrane filtry.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (pool.isNotEmpty()) TextButton(onClick = { onFilters(StudyFilters()) }) {
                Text("Wyczyść filtry")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = questionCount(remaining),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                StudyAction("Rozpocznij naukę", onStart)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> StudyFiltersGroup(label: String, values: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PrawkoSectionLabel(label)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { (value, text) ->
                val matches = value == selected
                FilterChip(
                    selected = matches,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                    leadingIcon = if (matches) {
                        { PrawkoIcon(PrawkoSymbol.CHECK, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun StudyQuestion(
    question: Question,
    series: StudySeries,
    state: StudyState,
    mediaRepository: MediaRepository,
    isDemo: Boolean,
    onBack: () -> Unit,
    onSelect: (AnswerChoice) -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier,
) {
    val scroll = rememberScrollState()
    val feedback = remember { BringIntoViewRequester() }
    val correct = question.answers.correctChoice
    val pickedCorrect = correct == series.selected

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudyHeader(
                title = "Pytanie ${series.index + 1} z ${series.questionIndices.size}",
                onBack = onBack,
                backEnabled = !state.saving,
                trailing = {
                    FavoriteQuestionButton(
                        checked = state.progress[question.id]?.isFavorite == true,
                        enabled = state.progressReady && question.id !in state.favoriteChanges,
                        onToggle = onFavorite,
                    )
                },
            )
            val answered = series.index + if (series.checked) 1 else 0
            LinearProgressIndicator(
                progress = { answered.toFloat() / series.questionIndices.size },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StudyQuestionTags(question, isDemo)
            Text(
                text = question.prompt,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            QuestionMediaContent(question = question, repository = mediaRepository, controls = true)
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                question.answers.choices.forEach { choice ->
                    StudyAnswerOption(
                        choice = choice,
                        text = question.answers.textFor(choice),
                        showLabel = question.type == QuestionType.SPECIALIST,
                        selected = series.selected == choice,
                        enabled = series.attemptId == null,
                        checked = series.checked,
                        isCorrect = choice == correct,
                        onSelect = { onSelect(choice) },
                    )
                }
            }
            if (series.checked) {
                StudyFeedback(
                    correct = pickedCorrect,
                    correctAnswer = correct.label,
                    modifier = Modifier.bringIntoViewRequester(feedback),
                )
                LaunchedEffect(Unit) { feedback.bringIntoView() }
            }
            if (state.favoriteError == question.id) {
                Text(
                    "Nie udało się zmienić ulubionych. Spróbuj ponownie.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.answerSaveFailed) {
                        Text(
                            "Nie zapisano odpowiedzi. Spróbuj ponownie.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    val label = when {
                        state.saving -> "Zapisywanie odpowiedzi…"
                        state.answerSaveFailed -> "Zapisz odpowiedź ponownie"
                        !series.checked -> "Sprawdź odpowiedź"
                        series.index == series.questionIndices.lastIndex -> "Zakończ serię"
                        else -> "Następne pytanie"
                    }
                    StudyAction(
                        label = label,
                        onClick = if (series.checked) onNext else onCheck,
                        enabled = series.selected != null && !state.saving,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyQuestionTags(question: Question, isDemo: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isDemo) DemoStudyChip()
        StudyTag(if (question.type == QuestionType.BASIC) "Podstawowe" else "Specjalistyczne")
        StudyTag("${question.points} pkt")
    }
}

@Composable
private fun StudyTag(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CircleShape,
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DemoStudyChip() {
    Surface(color = PrawkoPalette.Mint, contentColor = PrawkoPalette.Hero, shape = CircleShape) {
        Text(
            "DEMO",
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp).semantics {
                contentDescription = "DEMO, pytania testowe"
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StudyAnswerOption(
    choice: AnswerChoice,
    text: String,
    showLabel: Boolean,
    selected: Boolean,
    enabled: Boolean,
    checked: Boolean,
    isCorrect: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val correctAnswer = checked && isCorrect
    val wrongSelected = checked && selected && !isCorrect
    val (container, foreground, border) = when {
        correctAnswer -> Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
        wrongSelected -> Triple(colors.errorContainer, colors.onErrorContainer, colors.error)
        selected -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, colors.primary)
        else -> Triple(colors.surface, colors.onSurface, colors.outlineVariant)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = foreground,
        border = BorderStroke(if (selected || correctAnswer) 2.dp else 1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .semantics {
                    if (correctAnswer) stateDescription = "Poprawna odpowiedź"
                    if (wrongSelected) stateDescription = "Wybrana niepoprawna odpowiedź"
                }
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLabel) {
                Box(
                    modifier = Modifier.size(32.dp).background(colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(choice.label, color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (correctAnswer || (selected && !checked)) {
                PrawkoIcon(PrawkoSymbol.CHECK, Modifier.size(20.dp))
            }
        }
    }
}

private val FavoritePath = PathParser().parsePathString(
    "M12 2.8 L14.9 8.7 L21.4 9.6 L16.7 14.2 L17.8 20.7 L12 17.6 L6.2 20.7 L7.3 14.2 L2.6 9.6 L9.1 8.7 Z",
).toPath()

@Composable
private fun FavoriteQuestionButton(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        modifier = Modifier.semantics {
            contentDescription = if (checked) "Usuń z ulubionych" else "Dodaj do ulubionych"
        },
    ) {
        Canvas(Modifier.size(24.dp)) {
            with(drawContext.canvas) {
                save()
                scale(size.width / 24f, size.height / 24f)
            }
            if (checked) drawPath(FavoritePath, tint)
            else drawPath(FavoritePath, tint, style = Stroke(width = 1.65f))
            drawContext.canvas.restore()
        }
    }
}

@Composable
private fun StudyFeedback(correct: Boolean, correctAnswer: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val color = if (correct) colors.primaryContainer else colors.errorContainer
    val contentColor = if (correct) colors.onPrimaryContainer else colors.onErrorContainer
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.medium,
        color = color,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (correct) "Dobra odpowiedź" else "Niepoprawna odpowiedź",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!correct) Text("Poprawna odpowiedź to $correctAnswer.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StudyComplete(
    series: StudySeries,
    isDemo: Boolean,
    onBack: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StudyHeader("Ukończono serię", onBack)
        Surface(
            color = PrawkoPalette.Hero,
            contentColor = Color.White,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isDemo) DemoStudyChip()
                Text(
                    "${series.correctCount} z ${series.questionIndices.size}",
                    style = MaterialTheme.typography.displaySmall,
                    color = PrawkoPalette.Mint,
                )
                Text("poprawnych odpowiedzi", style = MaterialTheme.typography.titleMedium)
            }
        }
        StudyAction("Wybierz kolejną serię", onBack)
        TextButton(onClick = onStats) { Text("Zobacz statystyki") }
    }
}

@Composable
private fun StudyHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            enabled = backEnabled,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape),
        ) {
            PrawkoIcon(PrawkoSymbol.ARROW_LEFT, contentDescription = "Wróć")
        }
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        trailing()
    }
}

@Composable
private fun StudyMessage(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            StudyHeader(title, onBack)
        } else {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        }
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StudyAction(action, onAction)
    }
}

@Composable
private fun StudyAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        enabled = enabled,
    ) {
        Text(label)
        Spacer(Modifier.size(8.dp))
        PrawkoIcon(PrawkoSymbol.ARROW_RIGHT, Modifier.size(18.dp))
    }
}

private fun questionCount(count: Int): String = when {
    count == 1 -> "1 pytanie"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count pytania"
    else -> "$count pytań"
}
