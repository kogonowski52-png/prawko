package pl.prawko.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoMark
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSymbol
import pl.prawko.app.ui.theme.PrawkoPalette

@Composable
fun OnboardingScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrawkoMark(Modifier.size(40.dp))
                    Text("Prawko", style = MaterialTheme.typography.headlineSmall)
                }

                RouteIllustration()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "Kategoria B",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                    Text(
                        text = "Przygotuj się do prawa jazdy",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Wybierz naukę pytań lub egzamin próbny. Odpowiedzi i wyniki zapisują się na tym urządzeniu.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrawkoPrimaryButton(
                    text = "Przejdź do pulpitu",
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    PrawkoIcon(
                        symbol = PrawkoSymbol.SHIELD,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Niezależna aplikacja edukacyjna",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.85f)
            .clip(RoundedCornerShape(28.dp))
            .background(PrawkoPalette.Hero),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val origin = Offset(0.13f * width, 0.79f * height)
            val destination = Offset(0.82f * width, 0.29f * height)
            val trackWidth = height * 0.215f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PrawkoPalette.Mint.copy(alpha = 0.20f), PrawkoPalette.Hero),
                    center = destination,
                    radius = width * 0.58f,
                ),
                radius = width * 0.58f,
                center = destination,
            )

            val gridColor = PrawkoPalette.HeroMuted.copy(alpha = 0.13f)
            val gridSpacing = width / 12f
            for (column in 1..11) {
                val x = column * gridSpacing
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), 1.dp.toPx())
            }
            for (row in 1..6) {
                val y = row * gridSpacing
                drawLine(gridColor, Offset(0f, y), Offset(width, y), 1.dp.toPx())
            }

            val route = Path().apply {
                moveTo(origin.x, origin.y)
                cubicTo(width * 0.36f, height * 0.79f, width * 0.20f, height * 0.28f, width * 0.41f, height * 0.28f)
                cubicTo(width * 0.55f, height * 0.28f, width * 0.46f, height * 0.71f, width * 0.60f, height * 0.71f)
                cubicTo(width * 0.73f, height * 0.71f, width * 0.65f, height * 0.29f, destination.x, destination.y)
            }
            drawPath(
                path = route,
                color = PrawkoPalette.HeroMuted.copy(alpha = 0.36f),
                style = Stroke(width = trackWidth + 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path = route,
                color = PrawkoPalette.HeroRaised,
                style = Stroke(width = trackWidth, cap = StrokeCap.Round),
            )
            drawPath(
                path = route,
                color = PrawkoPalette.Mint,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 8.dp.toPx())),
                ),
            )
            drawCircle(PrawkoPalette.HeroMuted, radius = 6.dp.toPx(), center = origin)
            drawCircle(PrawkoPalette.Hero, radius = 3.dp.toPx(), center = origin)
            drawCircle(PrawkoPalette.Mint.copy(alpha = 0.20f), radius = trackWidth * 0.68f, center = destination)
            drawCircle(PrawkoPalette.Mint, radius = trackWidth * 0.33f, center = destination)

            val check = Path().apply {
                moveTo(destination.x - trackWidth * 0.13f, destination.y)
                lineTo(destination.x - trackWidth * 0.04f, destination.y + trackWidth * 0.09f)
                lineTo(destination.x + trackWidth * 0.14f, destination.y - trackWidth * 0.10f)
            }
            drawPath(check, PrawkoPalette.Hero, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
