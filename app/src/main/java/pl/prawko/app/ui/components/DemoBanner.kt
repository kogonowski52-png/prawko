package pl.prawko.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DemoBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "DEMO · Dane syntetyczne",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
