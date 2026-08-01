package com.thirtysecondsago.thorreplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thirtysecondsago.thorreplay.capture.CapturePreset
import com.thirtysecondsago.thorreplay.display.DisplayOption
import com.thirtysecondsago.thorreplay.input.KeyCaptureEvent
import com.thirtysecondsago.thorreplay.input.KeyBindingRepository
import com.thirtysecondsago.thorreplay.settings.AppSettings
import com.thirtysecondsago.thorreplay.settings.SettingsRepository
import com.thirtysecondsago.thorreplay.storage.SavedClip
import kotlinx.coroutines.launch

private enum class AppTab { Replay, Clips, Settings, Key, Info }

@Composable
fun ThorReplayApp(
    settingsRepository: SettingsRepository,
    keyBindingRepository: KeyBindingRepository,
    onStartBuffer: () -> Unit,
    onStopBuffer: () -> Unit,
    onSaveReplay: () -> Unit,
    onChooseOutputFolder: () -> Unit,
    getDisplayOptions: () -> List<DisplayOption>,
    onSelectDisplay: (DisplayOption) -> Unit,
    onSelectSavedPopupDisplay: (DisplayOption) -> Unit,
    onShowDisplayIndicator: (Int) -> Unit,
    onHideDisplayIndicator: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onLoadSavedClips: (String) -> List<SavedClip>,
    onOpenClip: (SavedClip) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onKeyDetectionActive: (Boolean, ((KeyCaptureEvent) -> Unit)?) -> Unit,
) {
    MaterialTheme {
        var tab by remember { mutableStateOf(AppTab.Replay) }
        Surface(modifier = Modifier.fillMaxSize()) {
            AppScaffold(tab = tab, onTabSelected = { tab = it }) {
                when (tab) {
                    AppTab.Replay -> ReplayScreen(
                        settingsRepository = settingsRepository,
                        onStartBuffer = onStartBuffer,
                        onStopBuffer = onStopBuffer,
                        onSaveReplay = onSaveReplay,
                        onLoadSavedClips = onLoadSavedClips,
                        onOpenClip = onOpenClip,
                        onOpenClips = { tab = AppTab.Clips },
                    )
                    AppTab.Clips -> ClipsScreen(settingsRepository, onLoadSavedClips, onOpenClip)
                    AppTab.Settings -> SettingsScreen(
                        settingsRepository = settingsRepository,
                        onChooseOutputFolder = onChooseOutputFolder,
                        getDisplayOptions = getDisplayOptions,
                        onSelectDisplay = onSelectDisplay,
                        onSelectSavedPopupDisplay = onSelectSavedPopupDisplay,
                        onShowDisplayIndicator = onShowDisplayIndicator,
                        onHideDisplayIndicator = onHideDisplayIndicator,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                    )
                    AppTab.Key -> KeyDetectionScreen(
                        keyBindingRepository = keyBindingRepository,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onKeyDetectionActive = onKeyDetectionActive,
                    )
                    AppTab.Info -> InfoScreen()
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(
    tab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavItem(tab, AppTab.Replay, Icons.Default.Save, "Replay", onTabSelected)
                NavItem(tab, AppTab.Clips, Icons.Default.Movie, "Clips", onTabSelected)
                NavItem(tab, AppTab.Settings, Icons.Default.Settings, "Settings", onTabSelected)
                NavItem(tab, AppTab.Key, Icons.Default.VideogameAsset, "Key", onTabSelected)
                NavItem(tab, AppTab.Info, Icons.Default.Info, "Info", onTabSelected)
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val compact = maxHeight < 420.dp || maxWidth < 520.dp
            val pagePadding = when {
                tab == AppTab.Replay -> if (compact) 6.dp else 8.dp
                compact -> 10.dp
                else -> 16.dp
            }
            val baseModifier = Modifier
                .padding(pagePadding)
                .fillMaxSize()
            if (tab == AppTab.Replay) {
                Column(
                    modifier = baseModifier,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
                ) {
                    content()
                }
            } else {
                Column(
                    modifier = baseModifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selectedTab: AppTab,
    tab: AppTab,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onTabSelected: (AppTab) -> Unit,
) {
    val modifier = Modifier
        .weight(1f)
        .padding(2.dp)
    val content: @Composable RowScope.() -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            if (selectedTab == tab) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .fillMaxWidth(0.52f)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
    TextButton(onClick = { onTabSelected(tab) }, modifier = modifier, content = content)
}

@Composable
private fun ReplayScreen(
    settingsRepository: SettingsRepository,
    onStartBuffer: () -> Unit,
    onStopBuffer: () -> Unit,
    onSaveReplay: () -> Unit,
    onLoadSavedClips: (String) -> List<SavedClip>,
    onOpenClip: (SavedClip) -> Unit,
    onOpenClips: () -> Unit,
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    var clips by remember { mutableStateOf(emptyList<SavedClip>()) }
    LaunchedEffect(settings.outputFolderUri, settings.lastSavedUri) {
        clips = onLoadSavedClips(settings.outputFolderUri)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        val recentLimit = if (compact) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            SectionCard {
                if (!compact) {
                    Text("Replay Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onSaveReplay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 58.dp else 68.dp),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    ButtonText("Capture Replay")
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackControls = maxWidth < 360.dp
                    if (stackControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            ReplayControlButton("Start", Icons.Default.PlayArrow, onStartBuffer, compact, Modifier.fillMaxWidth())
                            ReplayControlButton("Stop", Icons.Default.Stop, onStopBuffer, compact, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ReplayControlButton("Start", Icons.Default.PlayArrow, onStartBuffer, compact, Modifier.weight(1f))
                            ReplayControlButton("Stop", Icons.Default.Stop, onStopBuffer, compact, Modifier.weight(1f))
                        }
                    }
                }
                if (!compact) {
                    Text(
                        "${settings.replayDurationSeconds}s - ${settings.width} x ${settings.height} - ${settings.frameRate} FPS",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(settings.serviceStatus, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = onOpenClips, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    ButtonText("Clips")
                }
            }
            SectionCard {
                Text("Recent Clips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (clips.isEmpty()) {
                    Text("No clips yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    clips.take(recentLimit).forEach { clip ->
                        OutlinedButton(onClick = { onOpenClip(clip) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Movie, contentDescription = null)
                            Text(clip.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!compact) {
                        OutlinedButton(onClick = onOpenClips, modifier = Modifier.fillMaxWidth()) {
                            ButtonText("Open All Clips")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipsScreen(
    settingsRepository: SettingsRepository,
    onLoadSavedClips: (String) -> List<SavedClip>,
    onOpenClip: (SavedClip) -> Unit,
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    var clips by remember { mutableStateOf(emptyList<SavedClip>()) }
    LaunchedEffect(settings.outputFolderUri, settings.lastSavedUri) {
        clips = onLoadSavedClips(settings.outputFolderUri)
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        ScreenTitle("Clips")
        OutlinedButton(onClick = { clips = onLoadSavedClips(settings.outputFolderUri) }) {
            ButtonText("Refresh")
        }
    }
    if (clips.isEmpty()) {
        SectionCard {
            SectionHeader("No Clips Found")
            Text("Folder: ${settings.outputFolderLabel}")
        }
    } else {
        clips.forEach { clip ->
            SectionCard {
                Text(clip.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${clip.sizeBytes / 1024 / 1024} MB", modifier = Modifier.weight(1f))
                    Text(clip.source, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { onOpenClip(clip) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    ButtonText("Open")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onChooseOutputFolder: () -> Unit,
    getDisplayOptions: () -> List<DisplayOption>,
    onSelectDisplay: (DisplayOption) -> Unit,
    onSelectSavedPopupDisplay: (DisplayOption) -> Unit,
    onShowDisplayIndicator: (Int) -> Unit,
    onHideDisplayIndicator: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var displays by remember { mutableStateOf(getDisplayOptions()) }
    ScreenTitle("Settings")
    SectionCard {
        SectionHeader("Replay Length")
        SettingSlider("Clip length", settings.replayDurationSeconds, 10..60, "seconds") {
            scope.launch { settingsRepository.updateReplayDuration(it) }
        }
    }
    SectionCard {
        SectionHeader("Video Quality")
        Text("${settings.width} x ${settings.height} at ${settings.frameRate} FPS")
        var qualityMenuOpen by remember { mutableStateOf(false) }
        val selectedPreset = CapturePreset.entries.firstOrNull { preset ->
            settings.width == preset.width &&
                settings.height == preset.height &&
                settings.frameRate == preset.frameRate &&
                settings.bitrateMbps == preset.bitrateMbps
        }
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { qualityMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                ButtonText(selectedPreset?.label ?: "Custom quality")
            }
            DropdownMenu(
                expanded = qualityMenuOpen,
                onDismissRequest = { qualityMenuOpen = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                CapturePreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            qualityMenuOpen = false
                            scope.launch { settingsRepository.applyPreset(preset) }
                        },
                    )
                }
            }
        }
        SettingSlider("Quality", settings.bitrateMbps, 4..16, "Mbps") {
            scope.launch { settingsRepository.updateBitrate(it) }
        }
    }
    SectionCard {
        SectionHeader("Audio")
        ToggleRow("Internal audio", settings.audioEnabled) {
            scope.launch { settingsRepository.updateAudioEnabled(it) }
        }
        Text("Records game audio when Android allows it.", style = MaterialTheme.typography.bodyMedium)
    }
    SectionCard {
        SectionHeader("Saved Clips")
        Text(settings.outputFolderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        OutlinedButton(onClick = onChooseOutputFolder, modifier = Modifier.fillMaxWidth()) {
            ButtonText("Choose Save Folder")
        }
    }
    SectionCard {
        SectionHeader("File Names")
        Text("Example: ${settings.filenameTemplate}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        OutlinedTextField(
            value = settings.filenameTemplate,
            onValueChange = { scope.launch { settingsRepository.updateFilenameTemplate(it) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name format") },
            singleLine = true,
        )
        Text("Use: {datetime}, {date}, {time}, {duration}, {resolution}, {fps}", style = MaterialTheme.typography.bodyMedium)
    }
    SectionCard {
        SectionHeader("Saved Clip Alert")
        Text(settings.savedPopupDisplayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackButtons = maxWidth < 380.dp
            if (stackButtons) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { displays = getDisplayOptions() }, modifier = Modifier.fillMaxWidth()) {
                        ButtonText("Refresh Screens")
                    }
                    OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
                        ButtonText("Allow Popup")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { displays = getDisplayOptions() }, modifier = Modifier.weight(1f)) {
                        ButtonText("Refresh Screens")
                    }
                    OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f)) {
                        ButtonText("Allow Popup")
                    }
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 420.dp) 1 else 2
            displays.chunked(columns).forEach { rowDisplays ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowDisplays.forEach { display ->
                        FilterChip(
                            selected = settings.savedPopupDisplayId == display.displayId,
                            onClick = {
                                onSelectSavedPopupDisplay(display)
                                onSelectDisplay(display)
                            },
                            label = { Text(display.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowDisplays.size) {
                        Row(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onShowDisplayIndicator(settings.savedPopupDisplayId) }, modifier = Modifier.weight(1f)) {
                ButtonText("Preview Alert")
            }
            OutlinedButton(onClick = onHideDisplayIndicator, modifier = Modifier.weight(1f)) {
                ButtonText("Hide Alert")
            }
        }
    }
}

@Composable
fun KeyDetectionScreen(
    keyBindingRepository: KeyBindingRepository,
    onOpenAccessibilitySettings: () -> Unit,
    onKeyDetectionActive: (Boolean, ((KeyCaptureEvent) -> Unit)?) -> Unit,
) {
    val binding by keyBindingRepository.binding.collectAsState(initial = com.thirtysecondsago.thorreplay.input.KeyBinding())
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var lastDetected by remember { mutableStateOf("") }

    DisposableEffect(listening) {
        if (listening) {
            onKeyDetectionActive(true) { event ->
                lastDetected = "Replay trigger updated"
                listening = false
            }
        } else {
            onKeyDetectionActive(false, null)
        }
        onDispose { onKeyDetectionActive(false, null) }
    }

    LaunchedEffect(listening) {
        if (!listening) onKeyDetectionActive(false, null)
    }

    ScreenTitle("Capture Key")
    SectionCard {
        SectionHeader("Replay Trigger")
        Text(friendlyKeyName(binding.keyLabel), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (lastDetected.isNotBlank()) {
            Text(lastDetected, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { listening = true }, modifier = Modifier.weight(1f)) {
                ButtonText(if (listening) "Press replay button" else "Change Trigger")
            }
            OutlinedButton(
                onClick = {
                    listening = false
                    lastDetected = "Replay trigger cleared"
                    scope.launch { keyBindingRepository.clearKey() }
                },
                enabled = binding.keyLabel != "Not selected",
                modifier = Modifier.weight(1f),
            ) {
                ButtonText("Clear Trigger")
            }
        }
    }
    OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
        ButtonText("Accessibility Settings")
    }
}

@Composable
fun InfoScreen() {
    ScreenTitle("Info")
    SectionCard {
        SectionHeader("About")
        Text("30 Seconds Ago keeps a short gameplay buffer in memory and saves the recent replay as an MP4 clip.")
        Text("Built for the AYN Thor dual-screen setup, with the dashboard intended for the bottom screen.")
        SectionHeader("Notes")
        Text("Some games may block internal audio recording.")
        Text("If a button does not work as the replay trigger, try choosing a different controller button.")
        SectionHeader("Credits")
        Text("Made by Ryan Arthur Walker using AI.")
        Text("Ryan actually has no idea how to code.")
    }
}

@Composable
private fun ScreenTitle(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ButtonText(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
}

@Composable
private fun ReplayControlButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(if (compact) 48.dp else 56.dp),
    ) {
        Icon(icon, contentDescription = null)
        ButtonText(label)
    }
}

private fun friendlyKeyName(rawName: String): String {
    if (rawName == "Not selected") return "No trigger selected"
    return rawName
        .removePrefix("KEYCODE_BUTTON_")
        .removePrefix("KEYCODE_")
        .replace('_', ' ')
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 420.dp
            Column(
                modifier = Modifier.padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = modifier) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onChanged: (Int) -> Unit,
) {
    Text("$label: $value $suffix")
    Slider(
        value = value.toFloat(),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        onValueChange = { onChanged(it.toInt()) },
    )
}
