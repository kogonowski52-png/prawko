package pl.prawko.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pl.prawko.app.domain.AnswerChoice
import pl.prawko.app.domain.QuestionAnswers
import pl.prawko.app.domain.choices
import pl.prawko.app.domain.correctChoice
import pl.prawko.app.domain.textFor

@Composable
internal fun AnswerChoices(
    answers: QuestionAnswers,
    selection: AnswerChoice?,
    enabled: Boolean,
    onSelect: (AnswerChoice) -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
) {
    val shortAnswers = answers is QuestionAnswers.YesNo
    BoxWithConstraints(modifier.fillMaxWidth().selectableGroup()) {
        val option: @Composable (AnswerChoice, Modifier) -> Unit = { choice, size ->
            AnswerTile(
                text = answers.textFor(choice),
                letter = if (shortAnswers) null else choice.label,
                selected = choice == selection,
                correct = checked && choice == answers.correctChoice,
                incorrect = checked && choice == selection && choice != answers.correctChoice,
                enabled = enabled,
                onClick = { onSelect(choice) },
                modifier = size,
            )
        }
        if (shortAnswers && maxWidth >= 286.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                answers.choices.forEach { option(it, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                answers.choices.forEach { option(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun AnswerTile(
    text: String,
    letter: String?,
    selected: Boolean,
    correct: Boolean,
    incorrect: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val tint = when {
        incorrect -> colors.error
        correct || selected -> colors.primary
        else -> colors.outline
    }
    val background by animateColorAsState(
        targetValue = when {
            incorrect -> colors.errorContainer
            correct || selected -> colors.primaryContainer
            else -> colors.surface
        },
        animationSpec = tween(150),
        label = "Tło odpowiedzi",
    )
    val contentColor = when {
        incorrect -> colors.onErrorContainer
        correct || selected -> colors.onPrimaryContainer
        enabled -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics {
                if (correct) stateDescription = "Poprawna odpowiedź"
                if (incorrect) stateDescription = "Niepoprawna odpowiedź"
            },
        shape = shape,
        color = background,
        contentColor = contentColor,
        border = BorderStroke(if (selected || correct) 2.dp else 1.dp, if (selected || correct) tint else colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (letter != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colors.surfaceContainerHighest,
                    contentColor = colors.onSurface,
                ) {
                    Text(
                        letter,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = if (letter == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            )
            val marked = selected || correct
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, tint),
                color = if (marked) tint else Color.Transparent,
            ) {
                if (marked) {
                    Box(contentAlignment = Alignment.Center) {
                        PrawkoIcon(
                            symbol = if (incorrect) PrawkoSymbol.CLOSE else PrawkoSymbol.CHECK,
                            modifier = Modifier.size(14.dp),
                            tint = if (incorrect) colors.onError else colors.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
