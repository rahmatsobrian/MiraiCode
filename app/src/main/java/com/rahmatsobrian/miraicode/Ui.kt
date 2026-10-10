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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import rikka.shizuku.Shizuku

// ───────────── App shell + navigasi ─────────────
@Composable
fun MiraiApp(vm: MainVM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Chat" to Icons.Rounded.Forum, "Workspace" to Icons.Rounded.Folder,
        "Setelan" to Icons.Rounded.Settings)
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    // Keyboard terbuka -> sembunyikan nav bar, supaya tidak ada ruang kosong di atas keyboard
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // contentWindowInsets=0: TopAppBar & NavigationBar sudah mengurus inset-nya sendiri
    // (sebelumnya inset terhitung dobel -> jarak kosong besar di atas & di bawah)
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        if (!imeOpen) ShortNavigationBar {
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
            when (t) { 0 -> ChatScreen(vm); 1 -> WorkspaceScreen(vm); else -> SettingsScreen(vm) }
        }
    }
    vm.crash?.let { c ->
        val ctx = LocalContext.current
        AlertDialog(onDismissRequest = { vm.dismissCrash() },
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Aplikasi sempat crash") },
            text = { Text(c.take(3500), Modifier.verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
            confirmButton = { Button({
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", c)); vm.dismissCrash()
            }) { Text("Salin & tutup") } },
            dismissButton = { TextButton({ vm.dismissCrash() }) { Text("Tutup") } })
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
private val InputH = 56.dp   // tinggi minimum OutlinedTextField; tombol kirim & dropdown disamakan

@Composable
fun ChatScreen(vm: MainVM) {
    val ls = rememberLazyListState()
    val ctx = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(vm.msgs.size, vm.busy) {
        if (vm.msgs.isNotEmpty()) ls.animateScrollToItem(if (vm.busy) vm.msgs.size else vm.msgs.lastIndex)
    }
    Page("MiraiCode", actions = {
        AssistChip(onClick = {}, label = { Text(vm.mode.name) }, leadingIcon = { Icon(Icons.Rounded.Security, null, Modifier.size(18.dp)) })
    }) {
        Box(Modifier.weight(1f)) {
            if (vm.msgs.isEmpty()) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Workspace: ${vm.workspaces[vm.active].path}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("Minta AI membaca/menulis file atau menjalankan shell.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Tampilkan isi workspace", "Buat file hello.html sederhana", "Jelaskan struktur proyek ini").forEach {
                    SuggestionChip(onClick = { vm.send(it) }, label = { Text(it) })
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = ls, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.msgs) { i, m ->
                    Bubble(m, i, isLast = i == vm.msgs.lastIndex, busy = vm.busy,
                        onEdit = { idx -> vm.rollback(idx)?.let { input = it } }, onRetry = { vm.retry() })
                }
                if (vm.busy) item {
                    val tn = vm.toolName
                    if (tn != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContainedLoadingIndicator(Modifier.size(48.dp)); Text("Menjalankan $tn…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else LoadingIndicator(Modifier.size(48.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp).imePadding(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f).heightIn(min = InputH), placeholder = { Text("Pesan…") }, maxLines = 5, shape = RoundedCornerShape(28.dp))
            // Tombol kirim + dropdown: tinggi sama persis dengan kolom input (56dp), menyatu seperti split button
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Button(
                    onClick = { if (vm.busy) vm.stop() else if (input.isNotBlank()) { vm.send(input.trim()); input = "" } },
                    modifier = Modifier.size(width = 60.dp, height = InputH),
                    enabled = vm.busy || input.isNotBlank(),
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (vm.busy) Icon(Icons.Rounded.Stop, "Berhenti") else Icon(Icons.AutoMirrored.Rounded.Send, "Kirim")
                }
                Box {
                    Button(
                        onClick = { menu = !menu },
                        modifier = Modifier.size(width = 48.dp, height = InputH),
                        shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        val r by animateFloatAsState(if (menu) 180f else 0f, label = "arrow")
                        Icon(Icons.Rounded.KeyboardArrowDown, "Menu", Modifier.graphicsLayer { rotationZ = r })
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ulangi pesan terakhir") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, enabled = !vm.busy, onClick = { menu = false; vm.retry() })
                        DropdownMenuItem(text = { Text("Salin seluruh chat") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, enabled = vm.msgs.isNotEmpty(), onClick = { menu = false; copyToClipboard(ctx, vm.transcript()) })
                        DropdownMenuItem(text = { Text("Bersihkan chat") }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) }, enabled = !vm.busy, onClick = { menu = false; vm.clearChat() })
                    }
                }
            }
        }
    }
}

@Composable
fun Bubble(m: Msg, idx: Int, isLast: Boolean, busy: Boolean, onEdit: (Int) -> Unit, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val isUser = m.role == "user"
    val (bg, fg) = when (m.role) {
        "user" -> cs.primaryContainer to cs.onPrimaryContainer
        "tool" -> cs.surfaceContainerHigh to cs.onSurfaceVariant
        "error" -> cs.errorContainer to cs.onErrorContainer
        else -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = bg, contentColor = fg, shape = RoundedCornerShape(if (m.role == "tool") 14.dp else 20.dp),
            modifier = if (isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth()
        ) {
            when (m.role) {
                "tool" -> Column(Modifier.clickable { open = !open }.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▶ ${m.text}", Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                        if (m.extra.isNotBlank()) Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    }
                    if (open && m.extra.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(m.extra, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                "user", "error" -> SelectionContainer { Text(m.text, Modifier.padding(12.dp)) }
                else -> SelectionContainer { Markdown(m.text, Modifier.padding(12.dp)) }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val tint = cs.onSurfaceVariant
            IconButton({ copyToClipboard(ctx, if (m.role == "tool" && m.extra.isNotBlank()) m.text + "\n\n" + m.extra else m.text) }, Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ContentCopy, "Salin", Modifier.size(16.dp), tint = tint)
            }
            if (isUser && !busy) IconButton({ onEdit(idx) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Edit, "Edit", Modifier.size(16.dp), tint = tint) }
            if (m.role == "assistant" && isLast && !busy) IconButton(onRetry, Modifier.size(32.dp)) { Icon(Icons.Rounded.Refresh, "Ulangi", Modifier.size(16.dp), tint = tint) }
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
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = sel == i, onClick = { onSel(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {}, // tanpa ikon centang: hemat lebar, cegah teks terpotong
                label = { Text(o, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            )
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
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                if (vm.rootChecking) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularWavyProgressIndicator(Modifier.size(40.dp)); Text("Memeriksa akses root…")
                }
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
