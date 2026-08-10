package com.thirtysecondsago.thorreplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thirtysecondsago.thorreplay.capture.CapturePreset
import com.thirtysecondsago.thorreplay.display.DisplayOption
import com.thirtysecondsago.thorreplay.input.KeyCaptureEvent
import com.thirtysecondsago.thorreplay.input.KeyBindingRepository
import com.thirtysecondsago.thorreplay.settings.AppSettings
import com.thirtysecondsago.thorreplay.settings.CaptureState
import com.thirtysecondsago.thorreplay.settings.SettingsRepository
import com.thirtysecondsago.thorreplay.storage.SavedClip
import com.thirtysecondsago.thorreplay.storage.TrimClipResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class AppTab { Replay, Clips, Settings, Key, Info }
private enum class ClipSort(val label: String) {
    Newest("Newest"), Oldest("Oldest"), Largest("Largest")
}

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
    onShareClip: (SavedClip) -> Unit,
    onRenameClip: (SavedClip, String) -> SavedClip,
    onDeleteClip: (SavedClip) -> Unit,
    onTrimClip: (SavedClip, Long, Long, Boolean, String) -> TrimClipResult,
    onOpenAccessibilitySettings: () -> Unit,
    onKeyDetectionActive: (Boolean, ((KeyCaptureEvent) -> Unit)?) -> Unit,
) {
    MaterialTheme {
        val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
        var tab by remember { mutableStateOf(AppTab.Replay) }
        val scope = rememberCoroutineScope()
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!settings.onboardingComplete) {
                OnboardingScreen(
                    settings = settings,
                    onChooseOutputFolder = onChooseOutputFolder,
                    getDisplayOptions = getDisplayOptions,
                    onSelectDisplay = onSelectDisplay,
                    onSelectSavedPopupDisplay = onSelectSavedPopupDisplay,
                    onShowDisplayIndicator = onShowDisplayIndicator,
                    onHideDisplayIndicator = onHideDisplayIndicator,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onApplyPreset = { preset ->
                        scope.launch { settingsRepository.applyPreset(preset) }
                    },
                    onFinish = { scope.launch { settingsRepository.updateOnboardingComplete(true) } },
                )
            } else {
                AppScaffold(tab = tab, onTabSelected = { tab = it }) {
                    when (tab) {
                        AppTab.Replay -> ReplayScreen(
                            settingsRepository = settingsRepository,
                            onStartBuffer = onStartBuffer,
                            onStopBuffer = onStopBuffer,
                            onSaveReplay = onSaveReplay,
                        )
                        AppTab.Clips -> ClipsScreen(
                            settingsRepository,
                            onLoadSavedClips,
                            onOpenClip,
                            onShareClip,
                            onRenameClip,
                            onDeleteClip,
                            onTrimClip,
                        )
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
                        AppTab.Info -> InfoScreen(
                            onShowOnboarding = {
                                scope.launch { settingsRepository.updateOnboardingComplete(false) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    settings: AppSettings,
    onChooseOutputFolder: () -> Unit,
    getDisplayOptions: () -> List<DisplayOption>,
    onSelectDisplay: (DisplayOption) -> Unit,
    onSelectSavedPopupDisplay: (DisplayOption) -> Unit,
    onShowDisplayIndicator: (Int) -> Unit,
    onHideDisplayIndicator: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onApplyPreset: (CapturePreset) -> Unit,
    onFinish: () -> Unit,
) {
    var displays by remember { mutableStateOf(getDisplayOptions()) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    val selectedPreset = CapturePreset.entries.firstOrNull { preset ->
        settings.width == preset.width &&
            settings.height == preset.height &&
            settings.frameRate == preset.frameRate &&
            settings.bitrateMbps == preset.bitrateMbps
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("First-Time Setup")
        SectionCard {
            SectionHeader("Replay Buffer")
            Text("Choose how the replay buffer records your clips. For Dolphin, start with 720p30 Standard. Try 720p60 Smooth if the game still runs well.")
            Text("You can change this later in Settings.")
            Box(modifier = Modifier.fillMaxWidth()) {
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
                                onApplyPreset(preset)
                            },
                        )
                    }
                }
            }
        }
        SectionCard {
            SectionHeader("Saved Clips")
            Text(settings.outputFolderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = onChooseOutputFolder, modifier = Modifier.fillMaxWidth()) {
                ButtonText("Choose Save Folder")
            }
        }
        SectionCard {
            SectionHeader("Saved Clip Alert")
            Text("Choose which Thor screen shows the small saved-clip popup after a replay is captured.")
            Text("Tap Allow Popup Permission first. Then use Test Alert to make sure Android allows the popup to appear.")
            Text(settings.savedPopupDisplayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { displays = getDisplayOptions() }, modifier = Modifier.weight(1f)) {
                    ButtonText("Refresh")
                }
                OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f)) {
                    ButtonText("Allow Permission")
                }
            }
            displays.forEach { display ->
                SelectableDisplayButton(
                    label = display.alertPickerLabel(),
                    selected = settings.savedPopupDisplayId == display.displayId,
                    onClick = {
                        onSelectSavedPopupDisplay(display)
                        onSelectDisplay(display)
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onShowDisplayIndicator(settings.savedPopupDisplayId) }, modifier = Modifier.weight(1f)) {
                    ButtonText("Test Alert")
                }
                OutlinedButton(onClick = onHideDisplayIndicator, modifier = Modifier.weight(1f)) {
                    ButtonText("Hide Alert")
                }
            }
        }
        SectionCard {
            SectionHeader("Controller Hotkey")
            Text("You can set this up later after you know which buttons are free in your emulators or apps.")
            Text("The bottom screen will still have a pressable Capture Replay button.")
            OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                ButtonText("Accessibility Settings")
            }
        }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            ButtonText("Finish Setup")
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
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val context = LocalContext.current
    var freeStorageBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(settings.lastSavedUri, settings.captureState) {
        freeStorageBytes = withContext(Dispatchers.IO) {
            runCatching {
                val storagePath = context.getExternalFilesDir(null) ?: context.filesDir
                StatFs(storagePath.absolutePath).availableBytes
            }.getOrNull()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        val spacing = if (compact) 5.dp else 8.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            CaptureStateBanner(settings.captureState, settings.serviceStatus, settings.lastSavedClip)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
                DashboardMetric(
                    "Capture",
                    "${settings.width} × ${settings.height} • ${settings.frameRate} FPS",
                    Modifier.weight(1f),
                )
                DashboardMetric(
                    "Audio",
                    if (settings.audioEnabled) "Internal requested" else "Disabled",
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
                DashboardMetric("Replay buffer", "${settings.replayDurationSeconds} seconds", Modifier.weight(1f))
                DashboardMetric(
                    "Estimated memory",
                    formatBytes(estimatedBufferBytes(settings)),
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
                DashboardMetric(
                    "Device storage free",
                    freeStorageBytes?.let(::formatBytes) ?: "Checking…",
                    Modifier.weight(1f),
                )
                DashboardMetric("Source display", settings.selectedDisplayLabel, Modifier.weight(1f))
            }
            Button(
                onClick = onSaveReplay,
                enabled = settings.bufferActive &&
                    settings.captureState != CaptureState.Starting && settings.captureState != CaptureState.Saving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF166534),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 72.dp else 96.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(if (compact) 28.dp else 36.dp))
                Text(
                    if (settings.captureState == CaptureState.Saving) "Saving…" else "SAVE REPLAY",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReplayControlButton(
                    "Start Buffer",
                    Icons.Default.PlayArrow,
                    onStartBuffer,
                    compact,
                    Modifier.weight(1f),
                    enabled = !settings.bufferActive,
                )
                ReplayControlButton(
                    "Stop Buffer",
                    Icons.Default.Stop,
                    onStopBuffer,
                    compact,
                    Modifier.weight(1f),
                    enabled = settings.bufferActive,
                )
            }
        }
    }
}

@Composable
private fun CaptureStateBanner(state: CaptureState, detail: String, lastSavedClip: String) {
    val color = when (state) {
        CaptureState.Off -> Color(0xFF5F6368)
        CaptureState.Starting -> Color(0xFFF9A825)
        CaptureState.Ready -> Color(0xFF16803C)
        CaptureState.Saving -> Color(0xFF1565C0)
        CaptureState.Error -> Color(0xFFC62828)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(state.label.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                detail,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Last: $lastSavedClip",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun estimatedBufferBytes(settings: AppSettings): Long {
    val audioBitsPerSecond = if (settings.audioEnabled) 128_000L else 0L
    val totalBitsPerSecond = settings.bitrateMbps * 1_000_000L + audioBitsPerSecond
    return totalBitsPerSecond * settings.replayDurationSeconds / 8L
}

private fun formatBytes(bytes: Long): String {
    val gigabytes = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gigabytes >= 1.0) {
        "%.1f GB".format(gigabytes)
    } else {
        "%.0f MB".format(bytes / 1024.0 / 1024.0)
    }
}

@Composable
private fun ClipsScreen(
    settingsRepository: SettingsRepository,
    onLoadSavedClips: (String) -> List<SavedClip>,
    onOpenClip: (SavedClip) -> Unit,
    onShareClip: (SavedClip) -> Unit,
    onRenameClip: (SavedClip, String) -> SavedClip,
    onDeleteClip: (SavedClip) -> Unit,
    onTrimClip: (SavedClip, Long, Long, Boolean, String) -> TrimClipResult,
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var clips by remember { mutableStateOf(emptyList<SavedClip>()) }
    var selectedClip by remember { mutableStateOf<SavedClip?>(null) }
    var sort by remember { mutableStateOf(ClipSort.Newest) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var renameClip by remember { mutableStateOf<SavedClip?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteClip by remember { mutableStateOf<SavedClip?>(null) }
    var trimmingClip by remember { mutableStateOf<SavedClip?>(null) }
    var operationMessage by remember { mutableStateOf("") }

    suspend fun refreshClips() {
        clips = withContext(Dispatchers.IO) { onLoadSavedClips(settings.outputFolderUri) }
        selectedClip = selectedClip?.takeIf { selected -> clips.any { it.uri == selected.uri } }
    }

    LaunchedEffect(settings.outputFolderUri, settings.lastSavedUri) {
        refreshClips()
    }
    val sortedClips = when (sort) {
        ClipSort.Newest -> clips.sortedByDescending { it.modifiedMs }
        ClipSort.Oldest -> clips.sortedBy { it.modifiedMs }
        ClipSort.Largest -> clips.sortedByDescending { it.sizeBytes }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ScreenTitle("Clips")
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            OutlinedButton(onClick = { sortMenuOpen = true }) { ButtonText(sort.label) }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                ClipSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            sort = option
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = { scope.launch { refreshClips() } }) {
            ButtonText("Refresh")
        }
    }
    if (operationMessage.isNotBlank()) {
        Text(operationMessage, style = MaterialTheme.typography.bodySmall)
    }
    if (clips.isEmpty()) {
        SectionCard {
            SectionHeader("No Clips Found")
            Text("Folder: ${settings.outputFolderLabel}")
        }
    } else {
        sortedClips.forEach { clip ->
            ClipCard(
                clip = clip,
                favorite = clip.uri.toString() in settings.favoriteClipUris,
                selected = selectedClip?.uri == clip.uri,
                onPlay = {
                    if (selectedClip?.uri == clip.uri) {
                        selectedClip = null
                        if (trimmingClip?.uri == clip.uri) trimmingClip = null
                    } else {
                        selectedClip = clip
                    }
                },
                onOpen = { onOpenClip(clip) },
                onShare = { onShareClip(clip) },
                onRename = {
                    renameText = clip.name.removeSuffix(".mp4")
                    renameClip = clip
                },
                onDelete = { deleteClip = clip },
                trimming = trimmingClip?.uri == clip.uri,
                onTrim = {
                    selectedClip = clip
                    trimmingClip = clip
                },
                onCancelTrim = { trimmingClip = null },
                onSaveTrim = { startMs, endMs, replaceOriginal ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            onTrimClip(
                                clip,
                                startMs,
                                endMs,
                                replaceOriginal,
                                settings.outputFolderUri,
                            )
                        }
                    }.fold(
                        onSuccess = { result ->
                            if (result.replacedOriginal) {
                                val wasFavorite = clip.uri.toString() in settings.favoriteClipUris
                                settingsRepository.updateFavoriteClip(clip.uri.toString(), false)
                                if (wasFavorite) settingsRepository.updateFavoriteClip(result.clip.uri.toString(), true)
                                if (settings.lastSavedUri == clip.uri.toString()) {
                                    settingsRepository.updateLastSavedClip(result.clip.name, result.clip.uri.toString())
                                }
                            }
                            operationMessage = result.message
                            selectedClip = result.clip
                            trimmingClip = null
                            refreshClips()
                            true
                        },
                        onFailure = {
                            operationMessage = "Unable to trim: ${it.message ?: "invalid clip"}"
                            false
                        },
                    )
                },
                onFavorite = { favorite ->
                    scope.launch { settingsRepository.updateFavoriteClip(clip.uri.toString(), favorite) }
                },
            )
        }
    }

    renameClip?.let { clip ->
        AlertDialog(
            onDismissRequest = { renameClip = null },
            title = { Text("Rename clip") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Clip name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        renameClip = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { onRenameClip(clip, renameText) } }
                                .onSuccess { renamed ->
                                    if (settings.lastSavedUri == clip.uri.toString()) {
                                        settingsRepository.updateLastSavedClip(renamed.name, renamed.uri.toString())
                                    }
                                    if (selectedClip?.uri == clip.uri) selectedClip = renamed
                                    operationMessage = "Renamed to ${renamed.name}"
                                    refreshClips()
                                }
                                .onFailure { operationMessage = it.message ?: "Unable to rename clip" }
                        }
                    },
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameClip = null }) { Text("Cancel") } },
        )
    }

    deleteClip?.let { clip ->
        AlertDialog(
            onDismissRequest = { deleteClip = null },
            title = { Text("Delete clip?") },
            text = { Text("${clip.name} will be permanently deleted.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        deleteClip = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { onDeleteClip(clip) } }
                                .onSuccess {
                                    settingsRepository.updateFavoriteClip(clip.uri.toString(), false)
                                    if (settings.lastSavedUri == clip.uri.toString()) {
                                        settingsRepository.updateLastSavedClip("No clip saved yet", "")
                                    }
                                    if (selectedClip?.uri == clip.uri) selectedClip = null
                                    operationMessage = "Deleted ${clip.name}"
                                    refreshClips()
                                }
                                .onFailure {
                                    operationMessage = "Unable to delete: ${it.message ?: "clip unavailable"}"
                                    refreshClips()
                                }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteClip = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ClipPlayer(uri: Uri) {
    val context = LocalContext.current
    val videoView = remember(uri) {
        VideoView(context).apply {
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
        }
    }
    DisposableEffect(videoView) {
        onDispose {
            videoView.stopPlayback()
        }
    }
    AndroidView(
        factory = { videoView },
        update = { view ->
            if (view.tag != uri) {
                view.tag = uri
                view.setVideoURI(uri)
                view.start()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    )
}

@Composable
private fun ClipCard(
    clip: SavedClip,
    favorite: Boolean,
    selected: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    trimming: Boolean,
    onTrim: () -> Unit,
    onCancelTrim: () -> Unit,
    onSaveTrim: suspend (Long, Long, Boolean) -> Boolean,
    onFavorite: (Boolean) -> Unit,
) {
    var moreMenuOpen by remember { mutableStateOf(false) }
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ClipThumbnail(clip.uri)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    clip.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    clipMetadataLine(clip),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatClipDate(clip.modifiedMs),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onFavorite(!favorite) }) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (favorite) "Remove favorite" else "Favorite",
                    tint = if (favorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { moreMenuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More clip actions")
                }
                DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open externally") },
                        onClick = { moreMenuOpen = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = { moreMenuOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Trim") },
                        onClick = { moreMenuOpen = false; onTrim() },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { moreMenuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { moreMenuOpen = false; onDelete() },
                    )
                }
            }
        }
        if (selected) {
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Icon(Icons.Default.Stop, contentDescription = null)
                ButtonText("Stop")
            }
        } else {
            OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                ButtonText("Play")
            }
        }
        if (selected) {
            if (trimming) {
                ClipTrimEditor(clip, onCancelTrim, onSaveTrim)
            } else {
                ClipPlayer(clip.uri)
            }
        }
    }
}

@Composable
private fun ClipTrimEditor(
    clip: SavedClip,
    onCancel: () -> Unit,
    onSave: suspend (Long, Long, Boolean) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durationMs = clip.durationMs.coerceAtLeast(500L)
    var selectedRange by remember(clip.uri) { mutableStateOf(0f..durationMs.toFloat()) }
    var previewing by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    val videoView = remember(clip.uri) {
        VideoView(context).apply {
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
            setVideoURI(clip.uri)
        }
    }

    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
    }
    LaunchedEffect(previewing, selectedRange.endInclusive) {
        while (previewing) {
            if (videoView.currentPosition >= selectedRange.endInclusive.toInt()) {
                videoView.pause()
                videoView.seekTo(selectedRange.start.toInt())
                previewing = false
            }
            kotlinx.coroutines.delay(75L)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Trim clip", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        AndroidView(
            factory = { videoView },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Start ${formatTrimTime(selectedRange.start.toLong())}")
            Text("End ${formatTrimTime(selectedRange.endInclusive.toLong())}")
        }
        RangeSlider(
            value = selectedRange,
            onValueChange = { proposed ->
                if (proposed.endInclusive - proposed.start >= 500f) {
                    selectedRange = proposed
                    previewing = false
                    videoView.pause()
                    videoView.seekTo(proposed.start.toInt())
                }
            },
            valueRange = 0f..durationMs.toFloat(),
            enabled = !processing,
        )
        Text(
            "Lossless trimming may move the start slightly earlier to the nearest keyframe.",
            style = MaterialTheme.typography.labelSmall,
        )
        OutlinedButton(
            onClick = {
                if (previewing) {
                    videoView.pause()
                    previewing = false
                } else {
                    videoView.seekTo(selectedRange.start.toInt())
                    videoView.start()
                    previewing = true
                }
            },
            enabled = !processing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (previewing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
            ButtonText(if (previewing) "Stop preview" else "Preview selection")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                enabled = !processing,
                onClick = {
                    processing = true
                    scope.launch {
                        if (!onSave(selectedRange.start.toLong(), selectedRange.endInclusive.toLong(), false)) {
                            processing = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { ButtonText(if (processing) "Saving…" else "Save copy") }
            OutlinedButton(
                enabled = !processing,
                onClick = { confirmReplace = true },
                modifier = Modifier.weight(1f),
            ) { ButtonText("Replace") }
            TextButton(enabled = !processing, onClick = onCancel) { Text("Cancel") }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace original clip?") },
            text = { Text("A valid trimmed copy will be saved before the original is deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReplace = false
                        processing = true
                        scope.launch {
                            if (!onSave(selectedRange.start.toLong(), selectedRange.endInclusive.toLong(), true)) {
                                processing = false
                            }
                        }
                    },
                ) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

private fun formatTrimTime(milliseconds: Long): String {
    val totalTenths = milliseconds.coerceAtLeast(0L) / 100L
    val minutes = totalTenths / 600L
    val seconds = (totalTenths / 10L) % 60L
    val tenths = totalTenths % 10L
    return "%d:%02d.%d".format(minutes, seconds, tenths)
}

private fun clipMetadataLine(clip: SavedClip): String {
    val details = buildList {
        if (clip.durationMs > 0L) add(formatClipDuration(clip.durationMs))
        if (clip.width > 0 && clip.height > 0) add("${clip.width}×${clip.height}")
        add(formatClipSize(clip.sizeBytes))
    }
    return details.joinToString(" • ")
}

private fun formatClipDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatClipDate(modifiedMs: Long): String {
    if (modifiedMs <= 0L) return "Date unavailable"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(modifiedMs))
}

@Composable
private fun ClipThumbnail(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = loadVideoThumbnail(context, uri)
    }
    Box(
        modifier = Modifier
            .width(104.dp)
            .height(58.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnail = bitmap
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
        }
    }
}

private suspend fun loadVideoThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()
}

private fun formatClipSize(sizeBytes: Long): String {
    val mb = sizeBytes / 1024f / 1024f
    return if (mb >= 10f) {
        "${mb.toInt()} MB"
    } else {
        "%.1f MB".format(mb)
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
                        ButtonText("Allow Permission")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { displays = getDisplayOptions() }, modifier = Modifier.weight(1f)) {
                        ButtonText("Refresh Screens")
                    }
                    OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f)) {
                        ButtonText("Allow Permission")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            displays.forEach { display ->
                SelectableDisplayButton(
                    label = display.alertPickerLabel(),
                    selected = settings.savedPopupDisplayId == display.displayId,
                    onClick = {
                        onSelectSavedPopupDisplay(display)
                        onSelectDisplay(display)
                    },
                )
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

private fun DisplayOption.alertPickerLabel(): String = when (displayId) {
    0 -> "Top screen"
    4 -> "Bottom screen"
    else -> "Display $displayId"
}

@Composable
private fun SelectableDisplayButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            ButtonText(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            ButtonText(label)
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
fun InfoScreen(onShowOnboarding: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "Unknown"
    }
    ScreenTitle("Info")
    SectionCard {
        SectionHeader("About")
        Text("30 Seconds Ago keeps a short gameplay buffer in memory and saves the recent replay as an MP4 clip.")
        Text("Built for the AYN Thor dual-screen setup, with the dashboard intended for the bottom screen.")
        Text("Version $versionName")
        SectionHeader("Notes")
        Text("Some games may block internal audio recording.")
        Text("If a button does not work as the replay trigger, try choosing a different controller button.")
        SectionHeader("Credits")
        Text("Made by Ryan Arthur Walker using AI.")
        Text("Ryan actually has no idea how to code.")
    }
    OutlinedButton(onClick = onShowOnboarding, modifier = Modifier.fillMaxWidth()) {
        ButtonText("Run Setup Again")
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
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
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
private fun SectionCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 420.dp
            Column(
                modifier = contentModifier.padding(if (compact) 10.dp else 14.dp),
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
