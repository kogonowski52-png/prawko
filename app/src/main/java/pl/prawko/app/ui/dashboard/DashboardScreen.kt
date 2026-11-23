package pl.prawko.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
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
    onProgress: () -> Unit,
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
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Przygotowanie do egzaminu",
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ucz się teorii w swoim tempie.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            ExamCard(availability = availability, onClick = onExam)
            Spacer(Modifier.height(24.dp))
            PrawkoSectionLabel("Nauka i postęp")
            Spacer(Modifier.height(10.dp))
            OutlinedCard(onClick = onStudy, modifier = Modifier.fillMaxWidth()) {
                DashboardRow(
                    icon = PrawkoSymbol.BOOK,
                    title = "Nauka pytań",
                    description = "Wybierz rodzaj pytań i wracaj do błędów.",
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedCard(onClick = onProgress, modifier = Modifier.fillMaxWidth()) {
                DashboardRow(
                    icon = PrawkoSymbol.PROGRESS,
                    title = "Twój postęp",
                    description = "Statystyki odpowiedzi i historia egzaminów.",
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = PrawkoPalette.Hero,
        contentColor = Color.White,
        border = BorderStroke(1.dp, PrawkoPalette.HeroRaised),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .background(PrawkoPalette.HeroRaised, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    PrawkoIcon(PrawkoSymbol.EXAM, Modifier.size(22.dp), tint = PrawkoPalette.Mint)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Egzamin próbny",
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        when (availability) {
                            QuestionAvailability.UNAVAILABLE -> "Baza pytań jest jeszcze niedostępna."
                            QuestionAvailability.AVAILABLE -> "32 pytania · do 25 minut"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrawkoPalette.HeroMuted,
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrawkoPalette.Mint,
                    contentColor = PrawkoPalette.Hero,
                ),
            ) {
                Text("Zobacz egzamin", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                PrawkoIcon(PrawkoSymbol.ARROW_RIGHT, Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun DashboardRow(
    icon: PrawkoSymbol,
    title: String,
    description: String,
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
                    color = colors.secondaryContainer,
                    shape = RoundedCornerShape(13.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            PrawkoIcon(
                symbol = icon,
                modifier = Modifier.size(21.dp),
                tint = colors.onSecondaryContainer,
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
        PrawkoIcon(symbol = PrawkoSymbol.CHEVRON_RIGHT, modifier = Modifier.size(18.dp))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DashboardLightPreview() {
    PrawkoTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardScreen(QuestionAvailability.UNAVAILABLE, onExam = {}, onStudy = {}, onProgress = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun DashboardDarkPreview() {
    PrawkoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardScreen(QuestionAvailability.UNAVAILABLE, onExam = {}, onStudy = {}, onProgress = {})
        }
    }
}
