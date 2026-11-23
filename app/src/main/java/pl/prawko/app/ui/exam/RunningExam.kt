package pl.prawko.app.ui.exam

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.exam.ExamPhase
import pl.prawko.app.domain.exam.ExamRules
import pl.prawko.app.ui.components.AnswerChoices
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.media.QuestionMediaContent
import pl.prawko.app.ui.theme.PrawkoPalette

@Composable
internal fun RunningExam(
    state: ExamScreenState.Running,
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
    var pendingChoice by remember(session.id, question.id) { mutableStateOf<AnswerChoice?>(null) }
    var submitting by remember(session.id, question.id) { mutableStateOf(false) }
    val choice = pendingChoice ?: session.selection
    val savingChoice = pendingChoice != null && pendingChoice != session.selection
    LaunchedEffect(session.selection, pendingChoice) {
        if (pendingChoice == session.selection) pendingChoice = null
    }

    val submit = {
        if (!submitting && !savingChoice) {
            submitting = true
            onSubmit(question.id)
        }
    }

    Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExamProgressHeader(
                position = session.currentIndex + 1,
                count = session.questions.size,
                time = examClockText(session.remainingMs(clock)),
                onLeave = onLeave,
            )
            ExamPhaseStatus(phase, examClockText(session.phaseRemainingMs(clock)))
        }

        key(session.id, question.id) {
            Column(
                modifier = Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (specialist) "Pytanie specjalistyczne" else "Pytanie podstawowe",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    var entered by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { entered = true }
                    val opacity by animateFloatAsState(
                        targetValue = if (entered) 1f else 0.86f,
                        animationSpec = tween(140),
                        label = "Treść pytania",
                    )
                    Text(
                        question.prompt,
                        modifier = Modifier.alpha(opacity).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
                if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
                    val elapsed = (clock.elapsedRealtimeMs - session.questionStartedAtElapsedMs).coerceAtLeast(0)
                    QuestionMediaContent(
                        question = question,
                        repository = mediaRepository,
                        controls = false,
                        playing = phase == ExamPhase.READING || specialist && answering,
                        videoStartPositionMs = if (specialist) elapsed else elapsed.coerceAtMost(ExamRules.BASIC_READING_TIME_MS),
                        onUnavailable = { onMediaUnavailable(question.id) },
                    )
                }
                AnimatedVisibility(
                    visible = answering,
                    enter = fadeIn(tween(150)),
                    exit = ExitTransition.None,
                ) {
                    AnswerChoices(
                        answers = question.answers,
                        selection = choice,
                        enabled = answering && !submitting,
                        onSelect = { selected ->
                            if (!submitting && choice != selected) {
                                pendingChoice = selected
                                onChoose(question.id, selected)
                            }
                        },
                    )
                }
            }
        }

        if (answering) {
            ExamActionBar(
                hasChoice = choice != null,
                lastQuestion = session.currentIndex == session.questions.lastIndex,
                enabled = !submitting && !savingChoice,
                onAction = { if (choice == null) confirmSkip = true else submit() },
            )
        }
    }

    if (confirmSkip && answering && choice == null && !submitting) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            title = { Text("Pominąć pytanie?") },
            text = { Text("Za pominięte pytanie otrzymasz 0 punktów.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSkip = false
                    submit()
                }) { Text("Pomiń pytanie") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) { Text("Wybierz odpowiedź") }
            },
        )
    }
}

@Composable
private fun ExamProgressHeader(position: Int, count: Int, time: String, onLeave: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = PrawkoPalette.Hero,
        contentColor = Color.White,
        border = BorderStroke(1.dp, PrawkoPalette.HeroRaised),
    ) {
        Column(Modifier.padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onLeave,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    PrawkoIcon(PrawkoSymbol.ARROW_LEFT, Modifier.size(21.dp), contentDescription = "Opuść egzamin")
                }
                Text(
                    "Pytanie $position z $count",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Czas egzaminu $time" },
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("Czas egzaminu", style = MaterialTheme.typography.labelSmall, color = PrawkoPalette.HeroMuted)
                    Text(
                        time,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        color = PrawkoPalette.Mint,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { position.toFloat() / count },
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp).height(3.dp).clip(CircleShape)
                    .semantics { stateDescription = "Pytanie $position z $count" },
                color = PrawkoPalette.Mint,
                trackColor = PrawkoPalette.HeroRaised,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun ExamPhaseStatus(phase: ExamPhase, time: String) {
    val colors = MaterialTheme.colorScheme
    val title = when (phase) {
        ExamPhase.READING -> "Zapoznaj się z pytaniem"
        ExamPhase.ANSWERING -> "Udziel odpowiedzi"
        ExamPhase.FINISHED -> "Czas upłynął"
    }
    val timerLabel = if (phase == ExamPhase.READING) "Odpowiedzi za" else "Na odpowiedź"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (phase == ExamPhase.ANSWERING) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (phase == ExamPhase.ANSWERING) colors.onSecondaryContainer else colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (phase != ExamPhase.FINISHED) {
                Column(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "$timerLabel $time" },
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(timerLabel, style = MaterialTheme.typography.labelSmall)
                    Text(time, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

@Composable
private fun ExamActionBar(
    hasChoice: Boolean,
    lastQuestion: Boolean,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (hasChoice) {
                    PrawkoPrimaryButton(
                        text = if (lastQuestion) "Zatwierdź i zakończ" else "Zatwierdź i dalej",
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                    )
                } else {
                    OutlinedButton(
                        onClick = onAction,
                        enabled = enabled,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) {
                        Text("Dalej bez odpowiedzi")
                    }
                }
            }
        }
    }
}

private fun examClockText(remainingMs: Long): String {
    val seconds = (remainingMs + 999) / 1_000
    return "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}
