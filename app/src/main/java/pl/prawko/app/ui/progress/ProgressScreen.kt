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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.prawko.app.domain.QuestionRepository
import pl.prawko.app.domain.StudyProgressRepository
import pl.prawko.app.domain.exam.ExamCompletion
import pl.prawko.app.domain.exam.ExamHistoryItem
import pl.prawko.app.domain.exam.ExamRepository
import pl.prawko.app.domain.exam.ExamRules
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSectionLabel
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.theme.PrawkoPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    studyProgressRepository: StudyProgressRepository,
    examRepository: ExamRepository,
    questionRepository: QuestionRepository,
    datasetVersion: String?,
    isDemo: Boolean,
    onBack: () -> Unit,
    onAttemptClick: (String) -> Unit,
    onStudy: () -> Unit,
    modifier: Modifier = Modifier,
    showHistoryInitially: Boolean = false,
) {
    val model: ProgressViewModel = viewModel(
        key = "progress:${datasetVersion.orEmpty()}",
        factory = viewModelFactory {
            initializer { ProgressViewModel(studyProgressRepository, examRepository, questionRepository, datasetVersion) }
        },
    )
    val study by model.study.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable(showHistoryInitially) { mutableStateOf(showHistoryInitially) }
    val studyScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            state = if (showHistory) historyScroll else studyScroll,
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressHeader("Twój postęp", isDemo, onBack)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = { showHistory = false },
                            selected = !showHistory,
                            label = { Text("Statystyki") },
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { showHistory = true },
                            selected = showHistory,
                            label = { Text("Historia") },
                        )
                    }
                }
            }

            if (showHistory) {
                when (val state = history) {
                    ProgressLoad.Loading -> item { ProgressLoading() }
                    ProgressLoad.Failed -> item {
                        ProgressMessage(
                            "Nie udało się wczytać historii",
                            "Spróbuj ponownie odczytać zapisane wyniki.",
                            PrawkoSymbol.ARCHIVE,
                            "Spróbuj ponownie",
                            model::reloadHistory,
                        )
                    }
                    is ProgressLoad.Ready -> if (state.data.isEmpty()) {
                        item {
                            ProgressMessage(
                                "Brak zapisanych egzaminów",
                                "Po zakończeniu pierwszej próby zobaczysz tutaj jej wynik.",
                                PrawkoSymbol.EXAM,
                            )
                        }
                    } else {
                        item { PrawkoSectionLabel("Ostatnie próby") }
                        items(state.data, key = { "attempt:${it.session.id}" }) { attempt ->
                            HistoryCard(attempt, onClick = { onAttemptClick(attempt.session.id) })
                        }
                    }
                }
            } else {
                when (val state = study) {
                    ProgressLoad.Loading -> item { ProgressLoading() }
                    ProgressLoad.Failed -> item {
                        ProgressMessage(
                            "Nie udało się wczytać postępu",
                            "Spróbuj ponownie odczytać dane nauki.",
                            PrawkoSymbol.PROGRESS,
                            "Spróbuj ponownie",
                            model::reloadStudy,
                        )
                    }
                    is ProgressLoad.Ready -> item {
                        val totals = state.data
                        when {
                            totals == null || totals.types.all { it.total == 0 } -> ProgressMessage(
                                "Brak aktywnego katalogu",
                                "Po dodaniu katalogu i rozwiązaniu pytań pojawią się tutaj statystyki nauki.",
                                PrawkoSymbol.ARCHIVE,
                            )
                            else -> StudyStatistics(totals, onStudy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyStatistics(totals: StudyTotals, onStudy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PrawkoPalette.Hero,
            contentColor = PrawkoPalette.Mint,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrawkoIcon(PrawkoSymbol.PROGRESS, tint = PrawkoPalette.Mint)
                    Text("Przerobione pytania", style = MaterialTheme.typography.titleMedium, color = PrawkoPalette.HeroMuted)
                }
                val available = totals.types.sumOf { it.total }
                Text("${totals.answeredQuestions} z $available", style = MaterialTheme.typography.headlineLarge)
                LinearProgressIndicator(
                    progress = { totals.answeredQuestions.toFloat() / available },
                    modifier = Modifier.fillMaxWidth(),
                    color = PrawkoPalette.Mint,
                    trackColor = PrawkoPalette.HeroRaised,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudyMetric(totals.accuracyPercentage?.let { "$it%" } ?: "—", "Skuteczność", Modifier.weight(1f))
            StudyMetric(totals.attempts.toString(), "Odpowiedzi", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudyMetric(totals.mistakes.toString(), "Do powtórki", Modifier.weight(1f))
            StudyMetric(totals.favorites.toString(), "Ulubione", Modifier.weight(1f))
        }
        PrawkoSectionLabel("Pytania według rodzaju")
        OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                totals.types.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(group.type.progressLabel, style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(
                            progress = { if (group.total == 0) 0f else group.answered.toFloat() / group.total },
                            modifier = Modifier.fillMaxWidth().semantics {
                                stateDescription = "${group.answered} z ${group.total} pytań"
                            },
                        )
                        Text(
                            "${group.answered} z ${group.total} pytań",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        pl.prawko.app.ui.components.PrawkoPrimaryButton(
            text = if (totals.attempts == 0L) "Rozpocznij naukę" else "Wróć do nauki",
            onClick = onStudy,
            modifier = Modifier.fillMaxWidth(),
            symbol = PrawkoSymbol.BOOK,
        )
    }
}

@Composable
private fun StudyMetric(value: String, label: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryCard(attempt: ExamHistoryItem, onClick: () -> Unit) {
    val session = attempt.session
    val isScored = session.completion == ExamCompletion.COMPLETED || session.completion == ExamCompletion.TIME_EXPIRED
    val date = remember(attempt.completedAtEpochMillis) { formatAttemptDate(attempt.completedAtEpochMillis) }
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressIconTile(PrawkoSymbol.EXAM)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isScored) "${session.score} / ${ExamRules.MAX_SCORE} pkt • ${session.outcomeLabel()}" else "Egzamin przerwany",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Kat. ${session.category.name} · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrawkoIcon(PrawkoSymbol.CHEVRON_RIGHT, modifier = Modifier.size(18.dp))
        }
    }
}
