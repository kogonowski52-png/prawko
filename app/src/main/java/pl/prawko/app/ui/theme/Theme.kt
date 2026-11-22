package pl.prawko.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.prawko.app.R

object PrawkoPalette {
    val Ink = Color(0xFF132238)
    val Forest = Color(0xFF265537)
    val Hero = Color(0xFF122638)
    val HeroRaised = Color(0xFF1D3648)
    val HeroMuted = Color(0xFFB9CDD5)
    val Mint = Color(0xFFBBED70)
}

private val LightColors = lightColorScheme(
    primary = PrawkoPalette.Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EBC4),
    onPrimaryContainer = Color(0xFF182C19),
    inversePrimary = Color(0xFFADE1A5),
    secondary = Color(0xFF475D4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F2DA),
    onSecondaryContainer = Color(0xFF243623),
    tertiary = Color(0xFF385C75),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9ECFA),
    onTertiaryContainer = Color(0xFF17394F),
    background = Color(0xFFF5F7F4),
    onBackground = PrawkoPalette.Ink,
    surface = Color.White,
    onSurface = PrawkoPalette.Ink,
    surfaceVariant = Color(0xFFEBEFEA),
    onSurfaceVariant = Color(0xFF46564F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9F6),
    surfaceContainer = Color(0xFFF0F3EF),
    surfaceContainerHigh = Color(0xFFEAF0E9),
    surfaceContainerHighest = Color(0xFFE3EAE2),
    surfaceDim = Color(0xFFDCE5DA),
    surfaceBright = Color.White,
    inverseSurface = PrawkoPalette.Hero,
    inverseOnSurface = Color(0xFFEDF4F7),
    outline = Color(0xFF6B7D71),
    outlineVariant = Color(0xFFD5E1D6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADE1A5),
    onPrimary = Color(0xFF142D1B),
    primaryContainer = Color(0xFF274732),
    onPrimaryContainer = Color(0xFFDDF5D2),
    inversePrimary = PrawkoPalette.Forest,
    secondary = Color(0xFFC2D5BE),
    onSecondary = Color(0xFF223527),
    secondaryContainer = Color(0xFF253B31),
    onSecondaryContainer = Color(0xFFDFECDD),
    tertiary = Color(0xFFA6CFEA),
    onTertiary = Color(0xFF143247),
    tertiaryContainer = Color(0xFF263E51),
    onTertiaryContainer = Color(0xFFD6ECF9),
    background = Color(0xFF101C25),
    onBackground = Color(0xFFE9F1F6),
    surface = Color(0xFF182731),
    onSurface = Color(0xFFE9F1F6),
    surfaceVariant = Color(0xFF24343E),
    onSurfaceVariant = Color(0xFFBFCCD2),
    surfaceContainerLowest = Color(0xFF0F1920),
    surfaceContainerLow = Color(0xFF16242E),
    surfaceContainer = Color(0xFF1C2B35),
    surfaceContainerHigh = Color(0xFF23333D),
    surfaceContainerHighest = Color(0xFF2B3B44),
    surfaceDim = Color(0xFF101C25),
    surfaceBright = Color(0xFF303F49),
    inverseSurface = Color(0xFFE9F1F6),
    inverseOnSurface = PrawkoPalette.Ink,
    outline = Color(0xFF879A98),
    outlineVariant = Color(0xFF40524F),
)

private val DmSans = FontFamily(
    Font(R.font.dm_sans_400, FontWeight.Normal),
    Font(R.font.dm_sans_500, FontWeight.Medium),
    Font(R.font.dm_sans_600, FontWeight.SemiBold),
    Font(R.font.dm_sans_700, FontWeight.Bold),
)

private fun textStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = DmSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private val PrawkoTypography = Typography(
    displayLarge = textStyle(FontWeight.Bold, 54, 58, -2.4),
    displayMedium = textStyle(FontWeight.Bold, 46, 50, -1.9),
    displaySmall = textStyle(FontWeight.Bold, 38, 42, -1.45),
    headlineLarge = textStyle(FontWeight.Bold, 32, 36, -1.0),
    headlineMedium = textStyle(FontWeight.Bold, 28, 32, -0.75),
    headlineSmall = textStyle(FontWeight.Bold, 24, 28, -0.55),
    titleLarge = textStyle(FontWeight.Bold, 22, 27, -0.45),
    titleMedium = textStyle(FontWeight.Bold, 17, 22, -0.2),
    titleSmall = textStyle(FontWeight.SemiBold, 15, 20),
    bodyLarge = textStyle(FontWeight.Normal, 16, 24),
    bodyMedium = textStyle(FontWeight.Normal, 14, 21),
    bodySmall = textStyle(FontWeight.Normal, 12, 17, 0.1),
    labelLarge = textStyle(FontWeight.Bold, 14, 20, 0.1),
    labelMedium = textStyle(FontWeight.SemiBold, 12, 16, 0.15),
    labelSmall = textStyle(FontWeight.SemiBold, 11, 15, 0.3),
)

private val PrawkoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PrawkoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PrawkoTypography,
        shapes = PrawkoShapes,
        content = content,
    )
}
