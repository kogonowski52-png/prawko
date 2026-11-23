package pl.prawko.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Matching line icons on a 24 × 24 grid. */
enum class PrawkoSymbol(pathData: String) {
    DASHBOARD("M3 11 L12 3.5 L21 11 M5.5 9 V20.5 H18.5 V9 M10 20.5 V15 H14 V20.5"),
    BOOK("M12 6 C9 4 6 3.5 3 4.5 V18.5 C6 17.5 9 18 12 20 C15 18 18 17.5 21 18.5 V4.5 C18 3.5 15 4 12 6 V20"),
    EXAM("M15 4 H18 C19 4 20 5 20 6 V20 C20 21 19 22 18 22 H6 C5 22 4 21 4 20 V6 C4 5 5 4 6 4 H9 M9 3 H15 V7 H9 Z M8 12 L9 13 L11 11 M14 12 H16 M8 17 L9 18 L11 16 M14 17 H16"),
    PROGRESS("M4 4 V20 H21 M8 15 L12 11 L15 14 L20 8 M16.5 8 H20 V11.5"),
    SETTINGS("M4 6 H9 M13 6 H20 M4 12 H14 M18 12 H20 M4 18 H6 M10 18 H20 M13 6 A2 2 0 1 1 9 6 A2 2 0 0 1 13 6 Z M18 12 A2 2 0 1 1 14 12 A2 2 0 0 1 18 12 Z M10 18 A2 2 0 1 1 6 18 A2 2 0 0 1 10 18 Z"),
    ARROW_RIGHT("M4 12 H20 M14 6 L20 12 L14 18"),
    ARROW_LEFT("M20 12 H4 M10 6 L4 12 L10 18"),
    CLOSE("M6 6 L18 18 M18 6 L6 18"),
    CHECK("M5 12 L10 17 L19 7"),
    LAYERS("M12 3 L21 8 L12 13 L3 8 Z M3 12.5 L12 17.5 L21 12.5 M3 17 L12 22 L21 17"),
    SHIELD("M12 3 L20 6 V11 C20 16 17 19 12 22 C7 19 4 16 4 11 V6 Z M8 12 L11 15 L16 10"),
    MOON("M20.5 14.7 A8.7 8.7 0 1 1 9.3 3.5 A7 7 0 0 0 20.5 14.7 Z"),
    SUN("M16 12 A4 4 0 1 1 8 12 A4 4 0 0 1 16 12 Z M12 2 V4 M12 20 V22 M2 12 H4 M20 12 H22 M5 5 L6.5 6.5 M17.5 17.5 L19 19 M5 19 L6.5 17.5 M17.5 6.5 L19 5"),
    DISPLAY("M5 4 H19 C20 4 21 5 21 6 V16 C21 17 20 18 19 18 H5 C4 18 3 17 3 16 V6 C3 5 4 4 5 4 Z M12 18 V21 M8 21 H16"),
    ARCHIVE("M4 8 H20 V21 H4 Z M3 3 H21 V8 H3 Z M9 12 H15"),
    CHEVRON_RIGHT("M9 6 L15 12 L9 18"),
    ;

    internal val path = PathParser().parsePathString(pathData).toPath()
}

@Composable
fun PrawkoIcon(
    symbol: PrawkoSymbol,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val accessibility = if (contentDescription.isNullOrBlank()) {
        Modifier
    } else {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }

    Canvas(modifier.size(24.dp).then(accessibility)) {
        val scale = size.minDimension / 24f
        withTransform({
            translate((size.width - 24 * scale) / 2, (size.height - 24 * scale) / 2)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(
                path = symbol.path,
                color = tint,
                style = Stroke(width = 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
