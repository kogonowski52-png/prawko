package pl.prawko.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.prawko.app.domain.QuestionAvailability
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoMark
import pl.prawko.app.ui.components.PrawkoSectionLabel
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.theme.PrawkoPalette
import pl.prawko.app.ui.theme.PrawkoTheme

@Composable
fun DashboardScreen(
    availability: QuestionAvailability,
    onExam: () -> Unit,
    onStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            DashboardHeader()
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Przygotowanie do egzaminu",
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ucz się teorii w swoim tempie.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            ExamCard(availability = availability, onClick = onExam)
            Spacer(Modifier.height(24.dp))
            PrawkoSectionLabel("Nauka i postęp")
            Spacer(Modifier.height(10.dp))
            OutlinedCard(onClick = onStudy, modifier = Modifier.fillMaxWidth()) {
                DashboardRow(
                    icon = PrawkoSymbol.BOOK,
                    title = "Nauka działami",
                    description = "Ćwicz pytania według tematów.",
                    hasAction = true,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                DashboardRow(
                    icon = PrawkoSymbol.PROGRESS,
                    title = "Twój postęp",
                    description = "Tu zobaczysz postęp po pierwszej sesji nauki.",
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrawkoMark(modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Prawko",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = "Kategoria B",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ExamCard(availability: QuestionAvailability, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = PrawkoPalette.Hero,
        border = BorderStroke(1.dp, PrawkoPalette.HeroRaised),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
            val showArt = maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.25f
            if (showArt) {
                RouteArtwork(
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(136.dp)
                        .aspectRatio(0.8f),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(PrawkoPalette.HeroRaised, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PrawkoIcon(
                        symbol = PrawkoSymbol.EXAM,
                        modifier = Modifier.size(21.dp),
                        tint = PrawkoPalette.Mint,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val textModifier = Modifier.padding(end = if (showArt) 94.dp else 0.dp)
                Text(
                    text = "Egzamin próbny",
                    modifier = textModifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = when (availability) {
                        QuestionAvailability.UNAVAILABLE -> "Baza pytań jest jeszcze niedostępna."
                        QuestionAvailability.AVAILABLE -> "Sprawdź dostępność egzaminu na kategorię B."
                    },
                    modifier = textModifier,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrawkoPalette.HeroMuted,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrawkoPalette.Mint,
                        contentColor = PrawkoPalette.Hero,
                    ),
                ) {
                    Text("Zobacz egzamin")
                    Spacer(Modifier.width(8.dp))
                    PrawkoIcon(symbol = PrawkoSymbol.ARROW_RIGHT, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun RouteArtwork(modifier: Modifier) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val point = Offset(width * 0.53f, height * 0.54f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrawkoPalette.Mint.copy(alpha = 0.11f), Color.Transparent),
                center = point,
                radius = width * 0.58f,
            ),
            radius = width * 0.58f,
            center = point,
        )
        repeat(5) { column ->
            repeat(7) { row ->
                drawCircle(
                    color = PrawkoPalette.HeroMuted.copy(alpha = 0.19f),
                    radius = 1.dp.toPx(),
                    center = Offset(width * (0.24f + column * 0.15f), height * (0.08f + row * 0.13f)),
                )
            }
        }
        val road = Path().apply {
            moveTo(width * 1.1f, height * 0.08f)
            cubicTo(width * 0.42f, height * 0.03f, width * 0.24f, height * 0.29f, width * 0.57f, height * 0.42f)
            cubicTo(width * 1.01f, height * 0.59f, width * 0.99f, height * 0.72f, width * 0.63f, height * 0.81f)
            cubicTo(width * 0.44f, height * 0.86f, width * 0.31f, height * 0.92f, width * 0.33f, height)
        }
        drawPath(
            path = road,
            color = PrawkoPalette.HeroMuted.copy(alpha = 0.22f),
            style = Stroke(23.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = road,
            color = PrawkoPalette.HeroRaised,
            style = Stroke(21.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = road,
            color = PrawkoPalette.HeroMuted.copy(alpha = 0.55f),
            style = Stroke(
                width = 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 6.dp.toPx())),
            ),
        )
        val marker = Offset(width * 0.69f, height * 0.79f)
        drawCircle(PrawkoPalette.Hero, radius = 8.dp.toPx(), center = marker)
        drawCircle(PrawkoPalette.Mint, radius = 5.dp.toPx(), center = marker)
    }
}

@Composable
private fun DashboardRow(
    icon: PrawkoSymbol,
    title: String,
    description: String,
    hasAction: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (hasAction) colors.secondaryContainer else colors.surfaceVariant,
                    shape = RoundedCornerShape(13.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            PrawkoIcon(
                symbol = icon,
                modifier = Modifier.size(21.dp),
                tint = if (hasAction) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (hasAction) PrawkoIcon(symbol = PrawkoSymbol.CHEVRON_RIGHT, modifier = Modifier.size(18.dp))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DashboardLightPreview() {
    PrawkoTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardScreen(QuestionAvailability.UNAVAILABLE, onExam = {}, onStudy = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DashboardDarkPreview() {
    PrawkoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardScreen(QuestionAvailability.UNAVAILABLE, onExam = {}, onStudy = {})
        }
    }
}
