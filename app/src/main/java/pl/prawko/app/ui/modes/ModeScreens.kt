package pl.prawko.app.ui.modes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.navigation.StudyMode
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
                description = "Pytania uporządkowane według działów. Pracujesz we własnym tempie.",
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
fun QuestionAvailabilityScreen(
    mode: StudyMode,
    availability: QuestionAvailability,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modeTitle = when (mode) {
        StudyMode.STUDY -> "Nauka"
        StudyMode.EXAM -> "Egzamin próbny"
    }
    val unavailable = availability == QuestionAvailability.UNAVAILABLE
    val title = if (unavailable) "Brak bazy pytań" else "Baza pytań jest dostępna"
    val description = if (unavailable) {
        when (mode) {
            StudyMode.STUDY -> "Do rozpoczęcia nauki potrzebna jest zweryfikowana baza pytań dla kategorii B."
            StudyMode.EXAM -> "Do uruchomienia egzaminu próbnego potrzebna jest zweryfikowana baza pytań dla kategorii B."
        }
    } else {
        "Rozwiązywanie pytań będzie dostępne w kolejnej wersji aplikacji."
    }

    ModePage(onBack = onBack, modifier = modifier) {
        Text(
            text = modeTitle,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                QuestionIllustration(unavailable = unavailable)
                Column(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        PrawkoPrimaryButton(
            text = "Wróć do poprzedniego ekranu",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            symbol = PrawkoSymbol.ARROW_LEFT,
        )
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

@Composable
private fun QuestionIllustration(unavailable: Boolean) {
    val palette = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(palette.secondaryContainer, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 206.dp, height = 124.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val orbitColor = palette.onSecondaryContainer.copy(alpha = 0.30f)
            val ring = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
            drawCircle(orbitColor, radius = 52.dp.toPx(), center = center, style = ring)
            drawCircle(orbitColor, radius = 2.dp.toPx(), center = center + Offset(-68.dp.toPx(), -24.dp.toPx()))
            drawCircle(orbitColor, radius = 3.dp.toPx(), center = center + Offset(65.dp.toPx(), 27.dp.toPx()))
            drawRoundRect(
                color = palette.primary.copy(alpha = 0.16f),
                topLeft = center + Offset(-27.dp.toPx(), -30.dp.toPx()),
                size = Size(63.dp.toPx(), 67.dp.toPx()),
                cornerRadius = CornerRadius(17.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier.size(64.dp).background(palette.surface, RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PrawkoIcon(
                symbol = if (unavailable) PrawkoSymbol.ARCHIVE else PrawkoSymbol.CHECK,
                tint = palette.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
