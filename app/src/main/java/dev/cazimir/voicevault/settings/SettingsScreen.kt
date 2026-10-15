package dev.cazimir.voicevault.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cazimir.voicevault.Config

/** One switch on the settings screen. A new option is a [BooleanKey] in [SettingsStore] plus one row here. */
private data class ToggleRow(val key: BooleanKey, val title: String, val description: String)

private val toggleRows = listOf(
    ToggleRow(
        SettingsStore.SAVE_AS_TASK,
        title = "Save notes as tasks",
        description = "Write each note as a single \"- [ ]\" checklist line, so the Obsidian Tasks " +
            "and Dataview plugins list it as a task. Line breaks are joined into one line.",
    ),
    ToggleRow(
        SettingsStore.KEEP_AUDIO,
        title = "Keep audio in the vault",
        description = "Copy each voice note's recording into a \"${Config.VAULT_AUDIO_FOLDER}\" folder " +
            "inside your vault folder and link it from the note. About 2 MB per minute. If a copy " +
            "fails, the note is still saved, without the recording.",
    ),
)

/**
 * A plain one-level list of switches in the setup screen's visual language. Everything here is
 * off by default, which is upstream's behaviour.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Changes apply to the next note saved, including captures still waiting to be transcribed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        toggleRows.forEach { row -> ToggleSetting(row) }
    }
}

@Composable
private fun ToggleSetting(row: ToggleRow) {
    val context = LocalContext.current
    // SharedPreferences is not observable by Compose, so the switch keeps its own state and
    // writes through on every change.
    var checked by remember(row.key) { mutableStateOf(SettingsStore.getBoolean(context, row.key)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch) { value ->
                checked = value
                SettingsStore.setBoolean(context, row.key, value)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(row.title, style = MaterialTheme.typography.titleMedium)
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The whole row is the toggle (bigger touch target, one accessibility node), so the
        // switch itself only displays the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}
