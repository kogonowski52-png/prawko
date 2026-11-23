package pl.prawko.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pl.prawko.app.domain.QuestionType
import pl.prawko.app.domain.exam.ExamCompletion
import pl.prawko.app.domain.exam.ExamSession
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoPrimaryButton
import pl.prawko.app.ui.components.PrawkoSymbol
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ProgressHeader(title: String, isDemo: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)) {
            PrawkoIcon(PrawkoSymbol.ARROW_LEFT, contentDescription = "Wróć")
        }
        if (isDemo) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = CircleShape,
            ) {
                Text("DEMO", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
}

@Composable
internal fun ProgressLoading() {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { stateDescription = "Wczytywanie danych" },
        )
    }
}

@Composable
internal fun ProgressMessage(
    title: String,
    details: String,
    icon: PrawkoSymbol,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressIconTile(icon)
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) PrawkoPrimaryButton(action, onAction)
        }
    }
}

@Composable
internal fun ProgressIconTile(icon: PrawkoSymbol) {
    Box(
        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        PrawkoIcon(icon, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

internal val QuestionType.progressLabel: String
    get() = when (this) {
        QuestionType.BASIC -> "Podstawowe"
        QuestionType.SPECIALIST -> "Specjalistyczne"
    }

internal fun ExamSession.outcomeLabel(): String = when (completion) {
    ExamCompletion.COMPLETED, ExamCompletion.TIME_EXPIRED -> if (passed) "Zdany" else "Niezdany"
    ExamCompletion.ABANDONED, ExamCompletion.DEVICE_RESTARTED, ExamCompletion.MEDIA_UNAVAILABLE -> "Przerwany"
    null -> "W toku"
}

internal fun formatAttemptDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("pl-PL"))
        .format(Date(epochMillis))
