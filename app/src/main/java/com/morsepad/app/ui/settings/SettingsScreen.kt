package com.morsepad.app.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.morsepad.app.R
import com.morsepad.app.diagnostics.LatencyReport
import com.morsepad.app.diagnostics.LatencyTracker
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.codec.ChordTable
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.settings.AppSettings
import com.morsepad.core.settings.SettingsRepository
import com.morsepad.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: SettingsRepository,
    val latencyTracker: LatencyTracker,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    suspend fun exportLatency(context: Context): String = withContext(Dispatchers.IO) {
        LatencyReport.write(context.applicationContext, latencyTracker).absolutePath
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val colors = LocalPadColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.text)
            }
            Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
        }
        HorizontalDivider(color = colors.keyBorder)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Section(stringResource(R.string.settings_input))
            ToggleRow(stringResource(R.string.settings_vibration), settings.vibrationEnabled) { on -> viewModel.update { it.copy(vibrationEnabled = on) } }
            ToggleRow(stringResource(R.string.settings_chord_hint), settings.showChordHint) { on -> viewModel.update { it.copy(showChordHint = on) } }
            ToggleRow(stringResource(R.string.settings_key_labels), settings.showKeyLabels) { on -> viewModel.update { it.copy(showKeyLabels = on) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_text_size), color = colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.update { it.copy(textSizeSp = (it.textSizeSp - 2).coerceIn(AppSettings.TEXT_SIZE_RANGE)) } }) { Text("−") }
                Text("${settings.textSizeSp} sp", color = colors.text, modifier = Modifier.padding(horizontal = 12.dp))
                OutlinedButton(onClick = { viewModel.update { it.copy(textSizeSp = (it.textSizeSp + 2).coerceIn(AppSettings.TEXT_SIZE_RANGE)) } }) { Text("+") }
            }

            Section(stringResource(R.string.settings_appearance))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.theme == ThemeMode.DARK,
                    onClick = { viewModel.update { it.copy(theme = ThemeMode.DARK) } },
                    label = { Text(stringResource(R.string.settings_theme_dark)) },
                )
                FilterChip(
                    selected = settings.theme == ThemeMode.LIGHT,
                    onClick = { viewModel.update { it.copy(theme = ThemeMode.LIGHT) } },
                    label = { Text(stringResource(R.string.settings_theme_light)) },
                )
            }

            Section(stringResource(R.string.settings_encoding))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_table_version), color = colors.text, fontSize = 16.sp)
                ChordTables.all.forEach { table ->
                    FilterChip(
                        selected = settings.codeTableVersion == table.version,
                        onClick = { viewModel.update { it.copy(codeTableVersion = table.version) } },
                        label = { Text("v${table.version}") },
                    )
                }
            }
            CodeTableReference(ChordTables.byVersion(settings.codeTableVersion) ?: ChordTables.default)

            Section(stringResource(R.string.settings_diagnostics))
            LatencyPanel(viewModel)
            Spacer(Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = LocalPadColors.current.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = LocalPadColors.current.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Static reference card of the active table: letter, dots (● pressed) and the code. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodeTableReference(table: ChordTable) {
    val colors = LocalPadColors.current
    val cells = remember(table) {
        table.letters.entries.sortedBy { it.value }.map { (mask, letter) -> Triple(letter.toString(), mask, null as ChordMask?) } +
            table.extensions.entries.sortedBy { it.value }.map { (mask, letter) -> Triple(letter.toString(), table.prefix, mask) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEach { (letter, first, second) ->
            Row(
                Modifier
                    .border(1.dp, colors.keyBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(letter, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(22.dp))
                Text(
                    listOfNotNull(first, second).joinToString(" → ") { dots(it) },
                    color = colors.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
            }
        }
    }
    Text(stringResource(R.string.settings_table_note), color = colors.textSecondary, fontSize = 13.sp)
}

private fun dots(mask: ChordMask) = mask.toCode().map { if (it == '1') '●' else '○' }.joinToString("")

@Composable
private fun LatencyPanel(viewModel: SettingsViewModel) {
    val colors = LocalPadColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var exported by remember { mutableStateOf<String?>(null) }
    val lines = remember(refresh) {
        LatencyReport.metrics(viewModel.latencyTracker).map { (name, stats) -> LatencyReport.summaryLine(name, stats.summary()) }
    }
    Text(stringResource(R.string.settings_latency_intro), color = colors.textSecondary, fontSize = 13.sp)
    lines.forEach { Text(it, color = colors.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { refresh++ }) { Text(stringResource(R.string.settings_latency_refresh)) }
        OutlinedButton(onClick = {
            viewModel.latencyTracker.clear()
            refresh++
        }) { Text(stringResource(R.string.settings_latency_reset)) }
        OutlinedButton(onClick = { scope.launch { exported = viewModel.exportLatency(context) } }) {
            Text(stringResource(R.string.settings_latency_export))
        }
    }
    exported?.let { Text(it, color = colors.textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
}
