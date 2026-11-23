package pl.prawko.app.ui.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.prawko.app.domain.Appearance
import pl.prawko.app.ui.components.PrawkoIcon
import pl.prawko.app.ui.components.PrawkoSectionLabel
import pl.prawko.app.ui.components.PrawkoSymbol

@Composable
fun SettingsScreen(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    onMediaPacks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Text(
                text = "Ustawienia",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(24.dp))
            PrawkoSectionLabel("Motyw aplikacji")
            Spacer(Modifier.height(10.dp))
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.selectableGroup()) {
                    AppearanceOption(
                        text = "Zgodny z systemem",
                        icon = PrawkoSymbol.DISPLAY,
                        selected = appearance == Appearance.SYSTEM,
                        onSelect = { onAppearanceChange(Appearance.SYSTEM) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    AppearanceOption(
                        text = "Jasny",
                        icon = PrawkoSymbol.SUN,
                        selected = appearance == Appearance.LIGHT,
                        onSelect = { onAppearanceChange(Appearance.LIGHT) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    AppearanceOption(
                        text = "Ciemny",
                        icon = PrawkoSymbol.MOON,
                        selected = appearance == Appearance.DARK,
                        onSelect = { onAppearanceChange(Appearance.DARK) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            PrawkoSectionLabel("Multimedia")
            Spacer(Modifier.height(10.dp))
            OutlinedCard(onClick = onMediaPacks, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrawkoIcon(PrawkoSymbol.ARCHIVE)
                    Column(Modifier.weight(1f)) {
                        Text("Paczki multimediów", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Dodaj zdjęcia i filmy z lokalnej paczki ZIP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PrawkoIcon(PrawkoSymbol.CHEVRON_RIGHT)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Prawko to niezależna aplikacja do nauki teorii na prawo jazdy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearanceOption(
    text: String,
    icon: PrawkoSymbol,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrawkoIcon(symbol = icon, modifier = Modifier.size(22.dp))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = selected, onClick = null)
    }
}
