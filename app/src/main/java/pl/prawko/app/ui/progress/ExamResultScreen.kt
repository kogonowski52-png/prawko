package pl.prawko.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.exam.ExamCompletion
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamRules
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSectionLabel
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.theme.PrawkoPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultScreen(
    attemptId: String,
    examRepository: ExamRepository,
    questionRepository: QuestionRepository,
    isDemo: Boolean,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onPracticeMistakes: (String, Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: ExamResultViewModel = viewModel(
        key = "result:$attemptId",
        factory = viewModelFactory {
            initializer { ExamResultViewModel(attemptId, examRepository, questionRepository) }
        },
    )
    val review by model.review.collectAsStateWithLifecycle()
    var showAll by rememberSaveable(attemptId) { mutableStateOf(false) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressHeader("Wynik egzaminu", isDemo, onBack)
                }
            }
            when (val state = review) {
                ProgressLoad.Loading -> item { ProgressLoading() }
                ProgressLoad.Failed -> item {
                    ProgressMessage(
                        "Nie udało się wczytać wyniku",
                        "Spróbuj ponownie odczytać szczegóły próby.",
                        PrawkoSymbol.EXAM,
                        "Spróbuj ponownie",
                        model::reload,
                    )
                }
                is ProgressLoad.Ready -> {
                    val result = state.data
                    if (result == null) {
                        item {
                            ProgressMessage(
                                "Brak zapisanego wyniku",
                                "Zakończone próby znajdziesz w historii egzaminów.",
                                PrawkoSymbol.ARCHIVE,
                                "Zobacz historię",
                                onHistory,
                            )
                        }
                    } else {
                        item(key = "summary") {
                            ResultSummary(result)
                        }
                        item(key = "actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (result.isScored && result.reviewableMistakeIds.isNotEmpty()) {
                                    PrawkoPrimaryButton(
                                        text = "Przećwicz błędy",
                                        onClick = {
                                            onPracticeMistakes(result.session.datasetVersion, result.reviewableMistakeIds)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        symbol = PrawkoSymbol.BOOK,
                                    )
                                }
                                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                                    Text("Historia egzaminów")
                                }
                            }
                        }
                        item(key = "review-filter") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrawkoSectionLabel("Przegląd odpowiedzi")
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        selected = !showAll,
                                        onClick = { showAll = false },
                                        label = { Text("Do powtórki") },
                                    )
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        selected = showAll,
                                        onClick = { showAll = true },
                                        label = { Text("Wszystkie") },
                                    )
                                }
                            }
                        }
                        val answers = if (showAll) result.answers else result.mistakes
                        if (answers.isEmpty()) {
                            item(key = "all-correct") {
                                ProgressMessage(
                                    "Wszystkie odpowiedzi poprawne",
                                    "W tej próbie nie ma pytań do powtórki.",
                                    PrawkoSymbol.CHECK,
                                )
                            }
                        }
                        items(answers, key = { "question:${it.questionId}" }) { answer ->
                            AnswerReviewCard(answer, isScored = result.isScored)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSummary(review: ExamReview) {
    val session = review.session
    val whenCompleted = session.completedAtEpochMs ?: session.startedAtEpochMs
    val date = remember(whenCompleted) { formatAttemptDate(whenCompleted) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PrawkoPalette.Hero,
            contentColor = Color.White,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Kategoria ${session.category.name} · $date",
                    color = PrawkoPalette.HeroMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
                ResultBadge(
                    label = session.outcomeLabel(),
                    color = if (session.passed) PrawkoPalette.Mint else PrawkoPalette.HeroRaised,
                    textColor = if (session.passed) PrawkoPalette.Hero else Color.White,
                )
                if (review.isScored) {
                    Text("${session.score} / ${review.maxScore} pkt", style = MaterialTheme.typography.displaySmall)
                    val progress = session.score.toFloat() / review.maxScore.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().semantics {
                            stateDescription = "${session.score} z ${review.maxScore} punktów"
                        },
                        color = PrawkoPalette.Mint,
                        trackColor = PrawkoPalette.HeroRaised,
                    )
                    Text(
                        "Próg zaliczenia ${ExamRules.PASS_SCORE} pkt",
                        color = PrawkoPalette.HeroMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val reason = when (session.completion) {
                    ExamCompletion.TIME_EXPIRED -> "Upłynął czas egzaminu."
                    ExamCompletion.DEVICE_RESTARTED -> "Urządzenie uruchomiło się ponownie podczas egzaminu."
                    ExamCompletion.MEDIA_UNAVAILABLE -> "Materiał potrzebny do pytania nie był dostępny."
                    ExamCompletion.ABANDONED -> "Zakończono próbę przed ostatnim pytaniem."
                    ExamCompletion.COMPLETED, null -> null
                }
                if (reason != null) {
                    Text(reason, color = PrawkoPalette.HeroMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AnswerCount(review.correctCount, "Poprawne", Modifier.weight(1f))
                AnswerCount(review.incorrectCount, "Błędne", Modifier.weight(1f))
                AnswerCount(review.noAnswerCount, "Bez odpowiedzi", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnswerCount(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AnswerReviewCard(answer: ReviewedAnswer, isScored: Boolean) {
    val colors = MaterialTheme.colorScheme
    val isCorrect = answer.chosen == answer.correct
    val caption = when {
        isCorrect -> "Poprawna"
        answer.chosen == null -> "Pominięta"
        else -> "Błędna"
    }
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pytanie ${answer.number}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        answer.type.progressLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                ResultBadge(
                    label = caption,
                    color = when {
                        isCorrect -> colors.secondaryContainer
                        answer.chosen == null -> colors.surfaceVariant
                        else -> colors.errorContainer
                    },
                    textColor = when {
                        isCorrect -> colors.onSecondaryContainer
                        answer.chosen == null -> colors.onSurfaceVariant
                        else -> colors.onErrorContainer
                    },
                )
            }
            Text(
                answer.question?.prompt ?: "Treść z katalogu niedostępna · ID ${answer.questionId}",
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider(color = colors.outlineVariant)
            Text(
                text = "Twój wybór · ${answer.chosen?.let { choiceLabel(answer.question, it) } ?: "Brak"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Poprawna odpowiedź · ${choiceLabel(answer.question, answer.correct)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
            )
            if (isScored) {
                Text(
                    "${answer.earned} / ${answer.points} pkt",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultBadge(label: String, color: Color, textColor: Color) {
    Surface(color = color, contentColor = textColor, shape = CircleShape) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

private fun choiceLabel(question: Question?, choice: AnswerChoice): String {
    val options = question?.answers as? QuestionAnswers.MultipleChoice ?: return choice.label
    val text = when (choice) {
        AnswerChoice.A -> options.optionA
        AnswerChoice.B -> options.optionB
        AnswerChoice.C -> options.optionC
        AnswerChoice.YES, AnswerChoice.NO -> return choice.label
    }
    return "${choice.label} · $text"
}
