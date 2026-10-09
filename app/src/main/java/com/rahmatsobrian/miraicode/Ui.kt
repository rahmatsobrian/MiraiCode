@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.rahmatsobrian.miraicode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import rikka.shizuku.Shizuku

// ───────────── App shell + navigasi ─────────────
@Composable
fun MiraiApp(vm: MainVM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Chat" to Icons.Rounded.Forum, "Workspace" to Icons.Rounded.Folder,
        "Showcase" to Icons.Rounded.AutoAwesome, "Setelan" to Icons.Rounded.Settings)
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    Scaffold(bottomBar = {
        ShortNavigationBar {
            tabs.forEachIndexed { i, (l, ic) ->
                ShortNavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, null) }, label = { Text(l) })
            }
        }
    }) { pad ->
        AnimatedContent(tab, modifier = Modifier.padding(pad), label = "nav", transitionSpec = {
            val d = if (targetState > initialState) 1 else -1
            (fadeIn(fade) + slideInHorizontally(slide) { it / 6 * d }) togetherWith
                (fadeOut(fade) + slideOutHorizontally(slide) { -it / 6 * d })
        }) { t ->
            when (t) { 0 -> ChatScreen(vm); 1 -> WorkspaceScreen(vm); 2 -> ShowcaseScreen(); else -> SettingsScreen(vm) }
        }
    }
    vm.pending?.let { p ->
        AlertDialog(onDismissRequest = { p.result.complete(false) },
            icon = { Icon(Icons.Rounded.Terminal, null) },
            title = { Text("Izinkan ${p.title}?") },
            text = { Text(p.detail, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            confirmButton = { Button({ p.result.complete(true) }) { Text("Izinkan") } },
            dismissButton = { TextButton({ p.result.complete(false) }) { Text("Tolak") } })
    }
    if (vm.rootWarn) AlertDialog(onDismissRequest = { vm.rootWarn = false },
        icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Peringatan: mode Root") },
        text = { Text("Mode Root memberi AI akses ke SELURUH sistem: /system, /data, partisi, data aplikasi lain. Perintah yang salah dapat menghapus data, membuat bootloop, atau merusak perangkat. Aktifkan hanya jika kamu paham risikonya, dan biarkan konfirmasi perintah tetap aktif.") },
        confirmButton = { Button({ vm.confirmRoot() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Saya paham, aktifkan") } },
        dismissButton = { TextButton({ vm.rootWarn = false }) { Text("Batal") } })
}

@Composable
fun Page(title: String, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize()) { TopAppBar(title = { Text(title) }, actions = actions); content() }

// ───────────── Chat ─────────────
@Composable
fun ChatScreen(vm: MainVM) {
    val ls = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(vm.msgs.size, vm.busy) { if (vm.msgs.isNotEmpty()) ls.animateScrollToItem(vm.msgs.lastIndex) }
    Page("MiraiCode", actions = {
        AssistChip(onClick = {}, label = { Text(vm.mode.name) }, leadingIcon = { Icon(Icons.Rounded.Security, null, Modifier.size(18.dp)) })
        IconButton({ vm.clearChat() }) { Icon(Icons.Rounded.DeleteSweep, "Bersihkan") }
    }) {
        LazyColumn(Modifier.weight(1f), state = ls, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.msgs.isEmpty()) item { Text("Workspace: ${vm.workspaces[vm.active].path}\nTanya apa saja atau minta AI membaca/menulis file & menjalankan shell.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(vm.msgs) { Bubble(it) }
            if (vm.busy) item { LoadingIndicator(Modifier.size(48.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp).imePadding(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Pesan…") }, maxLines = 5, shape = RoundedCornerShape(24.dp))
            FilledIconButton({ vm.send(input); input = "" }, enabled = !vm.busy && input.isNotBlank(), modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Kirim")
            }
        }
    }
}

@Composable
fun Bubble(m: Msg) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (m.role) {
        "user" -> cs.primaryContainer to cs.onPrimaryContainer
        "tool" -> cs.surfaceContainerHigh to cs.onSurfaceVariant
        "error" -> cs.errorContainer to cs.onErrorContainer
        else -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.role == "user") Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 340.dp)) {
            if (m.role == "tool") Text("▶ ${m.text}", Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            else Text(m.text, Modifier.padding(12.dp))
        }
    }
}

// ───────────── FAB menu (dipakai Workspace & Showcase) ─────────────
@Composable
fun FabMenu(modifier: Modifier, items: List<Pair<String, ImageVector>>, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FloatingActionButtonMenu(modifier = modifier, expanded = expanded, button = {
        ToggleFloatingActionButton(checked = expanded, onCheckedChange = { expanded = it }) {
            val img by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add } }
            Icon(img, null, Modifier.animateIcon({ checkedProgress }))
        }
    }) {
        items.forEachIndexed { i, (t, ic) ->
            FloatingActionButtonMenuItem(onClick = { expanded = false; onPick(i) }, text = { Text(t) }, icon = { Icon(ic, null) })
        }
    }
}

// ───────────── Workspace ─────────────
@Composable
fun WorkspaceScreen(vm: MainVM) {
    var dlg by remember { mutableStateOf(false) }
    Page("Workspace") {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                itemsIndexed(vm.workspaces) { i, w ->
                    SegmentedListItem(
                        onClick = { vm.active = i; vm.save() },
                        shapes = ListItemDefaults.segmentedShapes(i, vm.workspaces.size),
                        leadingContent = { Icon(Icons.Rounded.Folder, null) },
                        supportingContent = { Text(w.path) },
                        trailingContent = { if (i == vm.active) Icon(Icons.Rounded.Check, "Aktif") }
                    ) { Text(w.name) }
                }
            }
            FabMenu(Modifier.align(Alignment.BottomEnd).padding(16.dp),
                listOf("Workspace baru" to Icons.Rounded.CreateNewFolder, "Hapus yang aktif" to Icons.Rounded.Delete)) {
                if (it == 0) dlg = true else if (vm.workspaces.size > 1) { vm.workspaces.removeAt(vm.active); vm.active = 0; vm.save() }
            }
        }
    }
    if (dlg) {
        var n by remember { mutableStateOf("") }; var p by remember { mutableStateOf("/storage/emulated/0/") }
        AlertDialog(onDismissRequest = { dlg = false }, title = { Text("Workspace baru") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Nama") }, singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Path folder") }, singleLine = true) } },
            confirmButton = { Button({ if (n.isNotBlank() && p.startsWith("/")) { vm.workspaces.add(Workspace(n, p)); vm.save(); dlg = false } }) { Text("Tambah") } },
            dismissButton = { TextButton({ dlg = false }) { Text("Batal") } })
    }
}

// ───────────── Setelan ─────────────
@Composable
fun Choice(options: List<String>, sel: Int, onSel: (Int) -> Unit) =
    ButtonGroup(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            val src = remember { MutableInteractionSource() }
            ToggleButton(sel == i, { onSel(i) }, Modifier.weight(1f).animateWidth(src), interactionSource = src) { Text(o, maxLines = 1) }
        }
    }

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(sub) }, trailingContent = {
        Switch(checked, onChange, thumbContent = {
            Crossfade(checked, label = "thumb") { on ->
                Icon(if (on) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize))
            }
        })
    })

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) =
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); content()
        }
    }

@Composable
fun SettingsScreen(vm: MainVM) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val allFiles = remember(tick) {
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val shizukuUp = remember(tick) { runCatching { Shizuku.pingBinder() }.getOrDefault(false) }
    val shizukuOk = remember(tick) { runCatching { shizukuUp && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) }
    Page("Setelan") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Provider AI (OpenAI-compatible)") {
                OutlinedTextField(vm.baseUrl, { vm.baseUrl = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("Endpoint (base URL)") }, singleLine = true)
                OutlinedTextField(vm.apiKey, { vm.apiKey = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(vm.model, { vm.model = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                OutlinedTextField(vm.system, { vm.system = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("System prompt") }, minLines = 3)
            }
            Section("Mode akses") {
                Choice(listOf("Non-root", "Shizuku", "Root"), vm.mode.ordinal) { vm.pickMode(Mode.entries[it]) }
                Text(when (vm.mode) {
                    Mode.NONROOT -> "Terbatas: sandbox app + storage yang diizinkan (semua file lewat izin 'All files access')."
                    Mode.SHIZUKU -> "Menengah: shell uid 2000 (adb). Akses lebih luas dari app biasa, tidak penuh."
                    Mode.ROOT -> "Penuh: seluruh sistem. Berisiko tinggi." + (vm.rootOk?.let { if (it) " (root terdeteksi)" else " (root TIDAK tersedia)" } ?: "")
                }, style = MaterialTheme.typography.bodySmall)
                SwitchRow("Konfirmasi perintah", "Minta persetujuan sebelum shell/tulis file", vm.confirm) { vm.confirm = it; vm.save() }
            }
            Section("Izin") {
                ListItem(headlineContent = { Text("Akses semua file") }, supportingContent = { Text(if (allFiles) "Diberikan" else "Belum") },
                    leadingContent = { Icon(if (allFiles) Icons.Rounded.CheckCircle else Icons.Rounded.Folder, null) })
                Button({
                    if (Build.VERSION.SDK_INT >= 30) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    else perm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }) { Text("Beri izin file") }
                ListItem(headlineContent = { Text("Shizuku") },
                    supportingContent = { Text(if (!shizukuUp) "Tidak berjalan" else if (shizukuOk) "Berjalan, izin diberikan" else "Berjalan, izin belum diberikan") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ runCatching { if (shizukuUp) Shizuku.requestPermission(0) }; tick++ }) { Text("Minta izin Shizuku") }
                    TextButton({ tick++ }) { Text("Segarkan") }
                }
            }
            Section("Tampilan") {
                Choice(listOf("Sistem", "Terang", "Gelap", "AMOLED"), vm.theme) { vm.theme = it; vm.save() }
                SwitchRow("Dynamic color", if (Build.VERSION.SDK_INT >= 31) "Ikuti wallpaper (Android 12+)" else "Perlu Android 12+", vm.dynamic) { vm.dynamic = it; vm.save() }
            }
        }
    }
}

// ───────────── Showcase ─────────────
@Composable
fun ShowcaseScreen() {
    val t = rememberInfiniteTransition(label = "p")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "p")
    var s1 by remember { mutableStateOf(true) }; var s2 by remember { mutableStateOf(false) }
    var exp by remember { mutableStateOf(false) }
    Page("Showcase") {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section("Loading Indicator") {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(); ContainedLoadingIndicator()
                    }
                }
                Section("Progress (wavy)") {
                    LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        CircularWavyProgressIndicator(progress = { p }); CircularWavyProgressIndicator()
                    }
                }
                Section("Switch (✓ / ✕)") {
                    SwitchRow("Opsi A", "Aktif", s1) { s1 = it }
                    SwitchRow("Opsi B", "Nonaktif", s2) { s2 = it }
                }
                Section("List (dynamic corner)") {
                    val items = listOf("Kode" to Icons.Rounded.Code, "Terminal" to Icons.Rounded.Terminal, "File" to Icons.Rounded.Description)
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        items.forEachIndexed { i, (n, ic) ->
                            SegmentedListItem(onClick = {}, shapes = ListItemDefaults.segmentedShapes(i, items.size),
                                leadingContent = { Icon(ic, null) }, supportingContent = { Text("Supporting text $n") },
                                trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }) { Text(n) }
                        }
                    }
                }
                Section("Button") {
                    ButtonGroup(Modifier.fillMaxWidth()) {
                        listOf("Satu", "Dua", "Tiga").forEach { l ->
                            val src = remember { MutableInteractionSource() }
                            Button({}, Modifier.weight(1f).animateWidth(src), interactionSource = src) { Text(l) }
                        }
                    }
                    SplitButtonLayout(leadingButton = {
                        SplitButtonDefaults.LeadingButton({}) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(SplitButtonDefaults.LeadingIconSize))
                            Spacer(Modifier.width(8.dp)); Text("Jalankan")
                        }
                    }, trailingButton = {
                        SplitButtonDefaults.TrailingButton(checked = exp, onCheckedChange = { exp = it }) {
                            val r by animateFloatAsState(if (exp) 180f else 0f, label = "arrow")
                            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = r })
                        }
                    })
                }
                Section("Shape morphing") { MorphShowcase() }
            }
            FabMenu(Modifier.align(Alignment.BottomEnd).padding(16.dp),
                listOf("Edit" to Icons.Rounded.Edit, "Bagikan" to Icons.Rounded.Share)) {}
        }
    }
}

@Composable
fun MorphShowcase() {
    val shapes = listOf("Cookie9" to MaterialShapes.Cookie9Sided, "SoftBurst" to MaterialShapes.SoftBurst, "Pill" to MaterialShapes.Pill,
        "Clover" to MaterialShapes.Clover4Leaf, "Sunny" to MaterialShapes.Sunny, "Flower" to MaterialShapes.Flower)
    var from by remember { mutableIntStateOf(0) }; var to by remember { mutableIntStateOf(0) }
    val prog = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val morph = remember(from, to) { Morph(shapes[from].second, shapes[to].second) }
    LaunchedEffect(to) { prog.snapTo(0f); prog.animateTo(1f, spec) }
    val color = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Canvas(Modifier.size(160.dp)) {
        val b = morph.calculateBounds()
        val sc = size.minDimension / maxOf(b[2] - b[0], b[3] - b[1])
        val path = morph.toPath(prog.value).asComposePath()
        withTransform({ scale(sc, sc, Offset.Zero); translate(-b[0], -b[1]) }) { drawPath(path, color) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shapes.forEachIndexed { i, (n, _) -> FilterChip(selected = to == i, onClick = { from = to; to = i }, label = { Text(n) }) }
    }
    }
}
