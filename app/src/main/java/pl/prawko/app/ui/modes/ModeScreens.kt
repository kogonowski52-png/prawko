package pl.prawko.app.ui.modes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.theme.PrawkoPalette

@Composable
fun ModeSelectionScreen(
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onExam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModePage(onBack = onBack, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Kategoria B",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Wybierz tryb",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ModeCard(
                title = "Nauka",
                description = "Wybierz rodzaj pytań, ucz się po kolei lub losowo i powtarzaj błędy.",
                icon = PrawkoSymbol.BOOK,
                onClick = onStudy,
            )
            ModeCard(
                title = "Egzamin próbny",
                description = "Symulacja egzaminu teoretycznego z limitem czasu.",
                icon = PrawkoSymbol.EXAM,
                onClick = onExam,
                featured = true,
            )
        }
    }
}

@Composable
private fun ModePage(
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                PrawkoIcon(
                    symbol = PrawkoSymbol.ARROW_LEFT,
                    contentDescription = "Wróć",
                    modifier = Modifier.size(24.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: PrawkoSymbol,
    onClick: () -> Unit,
    featured: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (featured) PrawkoPalette.Hero else colors.surface
    val content = if (featured) Color.White else colors.onSurface
    val detail = if (featured) PrawkoPalette.HeroMuted else colors.onSurfaceVariant
    val accentBackground = if (featured) PrawkoPalette.HeroRaised else colors.secondaryContainer
    val accent = if (featured) PrawkoPalette.Mint else colors.onSecondaryContainer
    val restingBorder = if (featured) PrawkoPalette.HeroRaised else colors.outlineVariant
    val borderColor by animateColorAsState(
        targetValue = if (pressed) accent else restingBorder,
        label = "Obramowanie karty trybu",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = RoundedCornerShape(26.dp),
        color = background,
        contentColor = content,
        border = BorderStroke(1.dp, borderColor),
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(52.dp).background(accentBackground, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    PrawkoIcon(icon, modifier = Modifier.size(26.dp), tint = accent)
                }
                Box(
                    modifier = Modifier.size(36.dp).background(accentBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PrawkoIcon(PrawkoSymbol.ARROW_RIGHT, modifier = Modifier.size(18.dp), tint = accent)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = detail)
            }
        }
    }
}
