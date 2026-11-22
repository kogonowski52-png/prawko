package pl.prawko.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.prawko.app.ui.theme.PrawkoPalette

private val RoadEdges = PathParser().parsePathString(
    "M12 30 C11 25 15 21 21 20 C29 18 33 12 30 6 M18 30 C17 27 20 25 24 24 C34 21 39 13 36 6",
).toPath()

private val RoadCenter = PathParser().parsePathString(
    "M15 29 L15 27 M18 24 L19.5 23 M24 22 L26 21 M30 18 L31.3 16.5 M33.5 12.6 L33.7 10.5",
).toPath()

@Composable
fun PrawkoMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .background(PrawkoPalette.Hero, RoundedCornerShape(percent = 28)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.minDimension / 44f
            withTransform({
                translate((size.width - 44 * scale) / 2, (size.height - 44 * scale) / 2)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                translate(-2f, 4f)
            }) {
                drawPath(RoadEdges, PrawkoPalette.Mint, style = Stroke(2.2f, cap = StrokeCap.Round))
                drawPath(RoadCenter, Color.White, style = Stroke(1.1f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun PrawkoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: PrawkoSymbol? = PrawkoSymbol.ARROW_RIGHT,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (symbol == null) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        } else {
            Text(
                text = text,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(12.dp))
            PrawkoIcon(symbol, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PrawkoSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
