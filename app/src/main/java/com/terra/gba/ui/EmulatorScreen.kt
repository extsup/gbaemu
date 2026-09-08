package com.terra.gba.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.terra.gba.core.GpspCore
import com.terra.gba.library.RomEntry
import com.terra.gba.library.RomLibrary

private val Ink = Color(0xFF070A12)
private val Lime = Color(0xFFB9FF65)
private val Control = Color(0xFF26334A)

@Composable
fun EmulatorScreen(setLandscape: (Boolean) -> Unit, exitGame: () -> Unit) {
    val context = LocalContext.current
    val library = remember { RomLibrary(context) }
    var games by remember { mutableStateOf(library.games()) }
    var activeRom by remember { mutableStateOf<RomEntry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var linearFiltering by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var romFolder by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        library.addFolder(uri)
        games = library.games()
        romFolder = uri.lastPathSegment?.substringAfterLast(':') ?: "ROM folder selected"
    }
    if (activeRom == null) {
        BackHandler { showMenu = true }
        HomeScreen(games, { folderPicker.launch(null) }) { entry ->
            runCatching {
                val file = library.materialize(entry)
                if (GpspCore.available) { GpspCore.setSaveDirectory(library.saveDirectory().absolutePath); check(GpspCore.loadRom(file.absolutePath)) }
                activeRom = entry
            }.onFailure { error = it.message ?: "ROM tidak bisa dibuka" }
        }
        error?.let { AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }, title = { Text("Gagal membuka ROM") }, text = { Text(it) }) }
        if (showMenu) EmulatorMenu({ showMenu = false }, { showMenu = false; folderPicker.launch(null) }, { showMenu = false; showSettings = true }, exitGame)
        if (showSettings) SettingsDialog(linearFiltering, landscape, { linearFiltering = it }, { landscape = it; setLandscape(it) }, { showSettings = false })
        return
    }
    BackHandler { showMenu = true }

    Box(Modifier.fillMaxSize().background(Ink)) {
        val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
        AndroidView(
            factory = { GlesGameSurface(it) },
            update = { it.setLinearFiltering(linearFiltering) },
            modifier = Modifier.fillMaxWidth().then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.aspectRatio(1.5f)).align(Alignment.Center),
        )
        StatusPill(Modifier.align(Alignment.TopCenter).padding(top = 16.dp), romFolder)
        if (isLandscape) LandscapeControls() else PortraitControls()
        Row(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("SAVE") { if (GpspCore.available) GpspCore.saveState() }
            SmallAction("LOAD") { if (GpspCore.available) GpspCore.loadState() }
            SmallAction("☰") { showMenu = true }
        }
    }
    if (showMenu) EmulatorMenu(
        onDismiss = { showMenu = false },
        onChooseFolder = { showMenu = false; folderPicker.launch(null) },
        onSettings = { showMenu = false; showSettings = true },
        onExit = { if (GpspCore.available) GpspCore.stop(); activeRom = null; showMenu = false },
    )
    if (showSettings) SettingsDialog(
        linearFiltering = linearFiltering,
        landscape = landscape,
        onLinearChanged = { linearFiltering = it },
        onLandscapeChanged = { landscape = it; setLandscape(it) },
        onDismiss = { showSettings = false },
    )
}

@Composable private fun StatusPill(modifier: Modifier, folder: String?) = Surface(color = Color(0xCC101727), shape = RoundedCornerShape(18.dp), modifier = modifier) {
    Text(folder?.let { "ROM: $it" } ?: "TERRAGBA  •  PILIH FOLDER ROM", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable private fun PortraitControls() = Box(Modifier.fillMaxSize()) {
    DPad(Modifier.align(Alignment.BottomStart).padding(24.dp))
    Row(Modifier.align(Alignment.BottomEnd).padding(28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { GameButton("B", GpspCore.B, Color(0xFF7764FF)); GameButton("A", GpspCore.A, Lime) }
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { GameButton("SELECT", GpspCore.SELECT, Control, true); GameButton("START", GpspCore.START, Control, true) }
    Row(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) { GameButton("R", GpspCore.R, Control, true) }
    Row(Modifier.align(Alignment.CenterStart).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { GameButton("L", GpspCore.L, Control, true) }
}

@Composable private fun LandscapeControls() = Box(Modifier.fillMaxSize()) {
    DPad(Modifier.align(Alignment.BottomStart).padding(28.dp))
    Row(Modifier.align(Alignment.BottomEnd).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { GameButton("B", GpspCore.B, Color(0xFF7764FF)); GameButton("A", GpspCore.A, Lime) }
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { GameButton("SELECT", GpspCore.SELECT, Control, true); GameButton("START", GpspCore.START, Control, true) }
    GameButton("L", GpspCore.L, Control, true, Modifier.align(Alignment.TopStart).padding(24.dp))
    GameButton("R", GpspCore.R, Control, true, Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 24.dp))
}

@Composable private fun DPad(modifier: Modifier) = Box(modifier.size(160.dp)) {
    GameButton("↑", GpspCore.UP, Control, true, Modifier.align(Alignment.TopCenter)); GameButton("←", GpspCore.LEFT, Control, true, Modifier.align(Alignment.CenterStart)); GameButton("→", GpspCore.RIGHT, Control, true, Modifier.align(Alignment.CenterEnd)); GameButton("↓", GpspCore.DOWN, Control, true, Modifier.align(Alignment.BottomCenter))
}

@Composable private fun GameButton(label: String, code: Int, color: Color, small: Boolean = false, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val size = if (small) 46.dp else 66.dp
    Surface(color = color.copy(alpha = if (pressed) 1f else .92f), contentColor = if (color == Lime) Ink else Color.White, shape = CircleShape, shadowElevation = if (pressed) 2.dp else 8.dp, modifier = modifier.size(if (small && label.length > 2) 72.dp else size, size).pointerInput(code) { detectTapGestures(onPress = { pressed = true; if (GpspCore.available) GpspCore.setButton(code, true); tryAwaitRelease(); pressed = false; if (GpspCore.available) GpspCore.setButton(code, false) }) }) { Box(contentAlignment = Alignment.Center) { Text(label, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleLarge) } }
}

@Composable private fun SmallAction(label: String, action: () -> Unit) = TextButton(onClick = action, colors = ButtonDefaults.textButtonColors(contentColor = Lime)) { Text(label) }

@Composable private fun EmulatorMenu(onDismiss: () -> Unit, onChooseFolder: () -> Unit, onSettings: () -> Unit, onExit: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text("TerraGBA") }, text = { Text("Kelola permainan atau hentikan emulasi.") }, confirmButton = { TextButton(onClick = onSettings) { Text("Pengaturan") } }, dismissButton = { Row { TextButton(onClick = onChooseFolder) { Text("Folder ROM") }; TextButton(onClick = onExit, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("Exit") } } })

@Composable private fun SettingsDialog(linearFiltering: Boolean, landscape: Boolean, onLinearChanged: (Boolean) -> Unit, onLandscapeChanged: (Boolean) -> Unit, onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text("Pengaturan") }, text = { Column { SettingSwitch("Linear filtering", linearFiltering, onLinearChanged); SettingSwitch("Layar landscape", landscape, onLandscapeChanged) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai") } })

@Composable private fun SettingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Switch(checked = checked, onCheckedChange = onChanged) }

@Composable private fun HomeScreen(games: List<RomEntry>, addFolder: () -> Unit, openGame: (RomEntry) -> Unit) = Box(Modifier.fillMaxSize().background(Ink).padding(24.dp)) {
    Column(Modifier.fillMaxSize()) {
        Text("TerraGBA", color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Text("Perpustakaan Game Boy Advance", color = Color(0xFFB6C0D0), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        if (games.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Belum ada ROM. Tambahkan folder yang berisi .gba atau .zip.", color = Color.White) }
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { games.forEach { game -> ElevatedCard(onClick = { openGame(game) }, modifier = Modifier.fillMaxWidth()) { Text(game.name, modifier = Modifier.padding(18.dp), color = Color.White) } } }
    }
    ExtendedFloatingActionButton(onClick = addFolder, modifier = Modifier.align(Alignment.BottomEnd), containerColor = Lime, contentColor = Ink, text = { Text("Tambah folder ROM") }, icon = { Text("+") })
}
