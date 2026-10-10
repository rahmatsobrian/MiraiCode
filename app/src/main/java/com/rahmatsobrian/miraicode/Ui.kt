@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.rahmatsobrian.miraicode

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlin.math.roundToInt
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import rikka.shizuku.Shizuku

// ───────────── App shell + navigasi ─────────────
@Composable
fun MiraiApp(vm: MainVM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Chat" to Icons.Rounded.Forum, "Workspace" to Icons.Rounded.Folder,
        "Setelan" to Icons.Rounded.Settings)
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Kembali: Workspace/Setelan -> Chat. Di halaman Chat tidak dicegat, jadi keluar dari aplikasi.
    BackHandler(enabled = tab != 0 && drawer.currentValue == DrawerValue.Closed) { tab = 0 }
    // Navigasi pindah dari bottom bar ke sidebar (drawer) -> area chat lebih luas
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {   // memberi warna latar + warna teks/ikon default
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.width(320.dp)) {
            DrawerContent(vm, tab, onTab = { tab = it }, close = { scope.launch { drawer.close() } })
        }
    }) {
        CompositionLocalProvider(LocalOpenDrawer provides { scope.launch { drawer.open() } }, LocalGoTab provides { tab = it }) {
            AnimatedContent(tab, modifier = Modifier.fillMaxSize(), label = "nav", transitionSpec = {
                val d = if (targetState > initialState) 1 else -1
                (fadeIn(fade) + slideInHorizontally(slide) { it / 6 * d }) togetherWith
                    (fadeOut(fade) + slideOutHorizontally(slide) { -it / 6 * d })
            }) { t ->
                when (t) { 0 -> ChatScreen(vm); 1 -> WorkspaceScreen(vm); else -> SettingsScreen(vm) }
            }
        }
    }
    }
    vm.renameAsk?.let { m ->
        var t by remember(m.id) { mutableStateOf(m.title) }
        AlertDialog(onDismissRequest = { vm.renameAsk = null }, title = { Text("Ganti nama chat") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button({ vm.renameChat(m.id, t); vm.renameAsk = null }) { Text("Simpan") } },
            dismissButton = { TextButton({ vm.renameAsk = null }) { Text("Batal") } })
    }
    vm.deleteAsk?.let { id ->
        val m = vm.chats.firstOrNull { it.id == id }
        AlertDialog(onDismissRequest = { vm.deleteAsk = null },
            icon = { Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus chat?") }, text = { Text("\"${m?.title ?: "Chat ini"}\" akan dihapus permanen dari riwayat.") },
            confirmButton = { Button({ vm.deleteChat(id); vm.deleteAsk = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Hapus") } },
            dismissButton = { TextButton({ vm.deleteAsk = null }) { Text("Batal") } })
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

// ───────────── Sidebar: riwayat + cari ─────────────
private data class Hit(val meta: ChatMeta, val snippet: AnnotatedString?)

private fun highlight(t: String, q: String, hl: Color): AnnotatedString = buildAnnotatedString {
    append(t)
    var i = t.indexOf(q, 0, ignoreCase = true)
    while (i >= 0 && q.isNotEmpty()) {
        addStyle(SpanStyle(background = hl, fontWeight = FontWeight.Bold), i, i + q.length)
        i = t.indexOf(q, i + q.length, ignoreCase = true)
    }
}

private fun hitOf(m: ChatMeta, q: String, hl: Color): Hit? {
    val inTitle = m.title.contains(q, ignoreCase = true)
    val i = m.text.indexOf(q, ignoreCase = true)
    if (!inTitle && i < 0) return null
    if (i < 0) return Hit(m, null)
    val st = (i - 30).coerceAtLeast(0); val en = (i + q.length + 70).coerceAtMost(m.text.length)
    val pre = if (st > 0) "…" else ""; val post = if (en < m.text.length) "…" else ""
    val raw = m.text.substring(st, en).replace('\n', ' ')
    val off = pre.length + (i - st)
    return Hit(m, buildAnnotatedString { append(pre + raw + post); addStyle(SpanStyle(background = hl, fontWeight = FontWeight.Bold), off, off + q.length) })
}

private fun bucket(t: Long): String {
    val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
    val today = cal.timeInMillis; val day = 86_400_000L
    return when { t >= today -> "Hari ini"; t >= today - day -> "Kemarin"; t >= today - 7 * day -> "7 hari terakhir"; t >= today - 30 * day -> "30 hari terakhir"; else -> "Lebih lama" }
}

@Composable
private fun ColumnScope.DrawerContent(vm: MainVM, tab: Int, onTab: (Int) -> Unit, close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searching) { if (searching) focus.requestFocus() else focusManager.clearFocus() }
    val q = query.trim()
    val hl = cs.primary.copy(alpha = 0.3f)
    val snapshot = vm.chats.toList()
    val rows: List<Any> = remember(q, snapshot, hl) {
        if (q.isEmpty()) buildList {
            val pins = snapshot.filter { it.pinned }
            if (pins.isNotEmpty()) { add("Disematkan"); pins.forEach { add(it) } }
            var last = ""
            snapshot.filter { !it.pinned }.forEach { m -> val b = bucket(m.updated); if (b != last) { add(b); last = b }; add(m) }
        }
        else snapshot.mapNotNull { hitOf(it, q, hl) }
    }
    // Header: judul + tombol cari. Kolom input baru muncul setelah tombol diketuk, jadi keyboard tidak
    // otomatis terbuka tiap sidebar dibuka dan tidak makan ruang.
    if (!searching) Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 28.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("MiraiCode", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        IconButton({ searching = true }) { Icon(Icons.Rounded.Search, "Cari chat") }
    } else {
        OutlinedTextField(
            query, { query = it },
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp).focusRequester(focus),
            placeholder = { Text("Cari chat") }, singleLine = true, shape = RoundedCornerShape(28.dp),
            leadingIcon = { IconButton({ searching = false; query = "" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Tutup pencarian") } },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Hapus teks") } }
        )
    }
    NavigationDrawerItem(
        label = { Text("Chat baru") }, icon = { Icon(Icons.Rounded.Add, null) }, selected = false,
        onClick = { if (!vm.busy) { vm.newChat(); onTab(0) }; searching = false; query = ""; close() },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (rows.isEmpty()) item {
            Text(if (q.isEmpty()) "Belum ada riwayat chat." else "Tidak ada chat yang cocok dengan \"$q\".",
                Modifier.padding(28.dp, 16.dp), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        items(rows) { r ->
            when (r) {
                is String -> Text(r, Modifier.padding(28.dp, 14.dp, 16.dp, 4.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                is ChatMeta -> ChatRow(vm, r, null, q, hl, tab == 0 && r.id == vm.currentId) { onTab(0); vm.openChat(r.id); searching = false; query = ""; close() }
                is Hit -> ChatRow(vm, r.meta, r.snippet, q, hl, tab == 0 && r.meta.id == vm.currentId) { onTab(0); vm.openChat(r.meta.id); searching = false; query = ""; close() }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
    listOf(1 to ("Workspace" to Icons.Rounded.Folder), 2 to ("Setelan" to Icons.Rounded.Settings)).forEach { (i, li) ->
        NavigationDrawerItem(
            label = { Text(li.first) }, icon = { Icon(li.second, null) }, selected = tab == i,
            onClick = { onTab(i); close() }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChatRow(vm: MainVM, m: ChatMeta, snippet: AnnotatedString?, q: String, hl: Color, selected: Boolean, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(28.dp),
        color = if (selected) cs.secondaryContainer else Color.Transparent,
        contentColor = if (selected) cs.onSecondaryContainer else cs.onSurface,
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (m.pinned) Icon(Icons.Rounded.PushPin, "Disematkan", Modifier.size(14.dp), tint = cs.primary)
                    Text(if (q.isEmpty()) AnnotatedString(m.title) else highlight(m.title, q, hl), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                }
                if (snippet != null) Text(snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Box {
                IconButton({ menu = true }, Modifier.size(36.dp)) { Icon(Icons.Rounded.MoreVert, "Opsi chat", Modifier.size(18.dp)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(if (m.pinned) "Lepas sematan" else "Sematkan") }, leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                        onClick = { menu = false; vm.pinChat(m.id) })
                    DropdownMenuItem(text = { Text("Ganti nama") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; vm.renameAsk = m })
                    DropdownMenuItem(text = { Text("Hapus", color = cs.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = cs.error) },
                        enabled = !(vm.busy && m.id == vm.currentId), onClick = { menu = false; vm.deleteAsk = m.id })
                }
            }
        }
    }
}

val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }
val LocalGoTab = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** Header ringkas 48dp (sebelumnya TopAppBar ~64dp + inset ganda) dengan tombol sidebar. */
@Composable
fun Page(title: String, actions: @Composable RowScope.() -> Unit = {}, bottomInset: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val open = LocalOpenDrawer.current
    val focusMgr = LocalFocusManager.current
    Column(Modifier.fillMaxSize().statusBarsPadding().then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
        .pointerInput(Unit) { detectTapGestures { focusMgr.clearFocus() } }) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(open) { Icon(Icons.Rounded.Menu, "Menu") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
}

// ───────────── Chat ─────────────
// ───────────── Badge mode akses (ketuk untuk ganti mode) ─────────────
class ModeStyle(val label: String, val sub: String, val icon: ImageVector, val bg: Color, val fg: Color)

fun modeStyle(m: Mode, cs: ColorScheme) = when (m) {
    Mode.NONROOT -> ModeStyle("Non-root", "Sandbox app + storage izin", Icons.Rounded.Security, cs.secondaryContainer, cs.onSecondaryContainer)
    Mode.SHIZUKU -> ModeStyle("Shizuku", "Shell uid 2000 (adb)", Icons.Rounded.Adb, cs.tertiaryContainer, cs.onTertiaryContainer)
    Mode.ROOT -> ModeStyle("Root", "Seluruh sistem · berisiko", Icons.Rounded.AdminPanelSettings, cs.errorContainer, cs.onErrorContainer)
}

@Composable
fun ModeBadge(vm: MainVM) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val st = modeStyle(vm.mode, cs)
    val bg by animateColorAsState(st.bg, label = "badgeBg")
    val fg by animateColorAsState(st.fg, label = "badgeFg")
    Box {
        Surface(
            onClick = { open = true }, enabled = !vm.busy, shape = CircleShape, color = bg, contentColor = fg,
            modifier = Modifier.alpha(if (vm.busy) 0.6f else 1f)
        ) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(st.icon, null, Modifier.size(18.dp))
                Text(st.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp))
            }
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(28.dp),
            containerColor = cs.surfaceContainerHighest, tonalElevation = 6.dp, shadowElevation = 8.dp) {
            Text("Mode akses", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Mode.entries.forEach { m ->
                val ms = modeStyle(m, cs); val picked = m == vm.mode
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp).widthIn(min = 240.dp)
                        .clip(RoundedCornerShape(if (picked) 22.dp else 16.dp)).background(if (picked) ms.bg else Color.Transparent)
                        .clickable { open = false; if (!picked) vm.pickMode(m) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(ms.icon, null, tint = if (picked) ms.fg else cs.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(ms.label, style = MaterialTheme.typography.titleMedium, color = if (picked) ms.fg else cs.onSurface)
                        Text(ms.sub, style = MaterialTheme.typography.bodySmall, color = if (picked) ms.fg.copy(alpha = 0.8f) else cs.onSurfaceVariant)
                    }
                    if (picked) Icon(Icons.Rounded.Check, null, tint = ms.fg)
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: MainVM) {
    val ls = rememberLazyListState()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val chatScope = rememberCoroutineScope()
    val focusMgr = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Jangan fokus otomatis ke kolom pesan (dan jangan munculkan keyboard) saat masuk ke halaman chat
    LaunchedEffect(Unit) { delay(150); focusMgr.clearFocus(); keyboard?.hide() }
    val showDown by remember { derivedStateOf { ls.canScrollForward } }
    fun toTop() { chatScope.launch { ls.animateScrollToItem(0) } }
    fun toBottom() { chatScope.launch { ls.animateScrollToItem((ls.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), 100000) } }
    var input by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    var inputH by remember { mutableStateOf(76.dp) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::addImage) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::addFile) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let(vm::addImage) }
    LaunchedEffect(vm.msgs.size, vm.busy) {
        if (vm.msgs.isNotEmpty()) ls.animateScrollToItem(if (vm.busy) vm.msgs.size else vm.msgs.lastIndex)
    }
    // Streaming: ikuti teks yang bertambah, tapi hanya bila pengguna memang sedang di dasar daftar
    val streamLen = vm.msgs.lastOrNull()?.takeIf { it.role == "assistant" }?.text?.length ?: 0
    LaunchedEffect(streamLen) {
        if (vm.busy && streamLen > 0 && (ls.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= vm.msgs.size - 1) ls.scrollToItem(vm.msgs.size)
    }
    Page(vm.chats.firstOrNull { it.id == vm.currentId }?.title ?: "MiraiCode", actions = {
        ModeBadge(vm)
    }, bottomInset = false) {
        // Box mengikuti keyboard/nav bar. Daftar chat memenuhi seluruh area dan kartu input melayang di atasnya,
        // jadi area di sekeliling kartu transparan (pesan terlihat lewat belakang, memudar halus ke latar).
        Box(Modifier.weight(1f).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            if (vm.msgs.isEmpty()) Column(Modifier.align(Alignment.Center).padding(start = 32.dp, end = 32.dp, bottom = inputH), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Workspace: ${vm.workspaces[vm.active].path}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("Minta AI membaca/menulis file atau menjalankan shell.", color = cs.onSurfaceVariant)
                listOf("Tampilkan isi workspace", "Buat file hello.html sederhana", "Jelaskan struktur proyek ini").forEach {
                    SuggestionChip(onClick = { vm.send(it) }, label = { Text(it) })
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = ls, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = inputH + 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.msgs) { i, m ->
                    Bubble(m, i, isLast = i == vm.msgs.lastIndex, busy = vm.busy,
                        onEdit = { idx -> vm.rollback(idx)?.let { input = it } }, onRetry = { vm.retry() })
                }
                if (vm.busy) item {
                    val tn = vm.toolName
                    if (tn != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContainedLoadingIndicator(Modifier.size(48.dp)); Text("Menjalankan $tn…", color = cs.onSurfaceVariant)
                    } else LoadingIndicator(Modifier.size(48.dp))
                }
            }
            // Pudar halus di atas kartu input
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(inputH + 12.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to cs.background.copy(alpha = 0.85f), 1f to cs.background)))

            // Tombol "ke akhir chat" muncul saat tidak di dasar daftar
            AnimatedVisibility(
                showDown && vm.msgs.isNotEmpty(), Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = inputH + 12.dp),
                enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()
            ) {
                Surface(onClick = { toBottom() }, shape = CircleShape, color = cs.surfaceContainerHighest, contentColor = cs.onSurface,
                    tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.KeyboardArrowDown, "Ke akhir chat") }
                }
            }
            // ── Kartu input ala Gemini/Claude ──
            val canSend = vm.busy || input.isNotBlank() || vm.attachments.isNotEmpty()
            var expanded by remember { mutableStateOf(false) }
            LaunchedEffect(input) { if (input.isEmpty()) expanded = false }
            val sidePad by animateDpAsState(if (expanded) 16.dp else 52.dp, label = "sidePad")
            val botPad by animateDpAsState(if (expanded) 52.dp else 14.dp, label = "botPad")
            Surface(
                color = cs.surfaceContainerHigh, shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                    .onSizeChanged { inputH = with(density) { it.height.toDp() } + 16.dp }
            ) {
                Column {
                    if (vm.attachments.isNotEmpty()) Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) { vm.attachments.forEachIndexed { i, a -> AttachmentChip(a) { vm.removeAttachment(i) } } }
                    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).animateContentSize()) {
                        BasicTextField(
                            value = input, onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth().padding(start = sidePad, end = sidePad, top = 14.dp, bottom = botPad),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                            cursorBrush = SolidColor(cs.primary), maxLines = 8,
                            onTextLayout = { if (it.lineCount > 1) expanded = true },
                            decorationBox = { inner ->
                                Box { if (input.isEmpty()) Text("Pesan…", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge); inner() }
                            }
                        )
                        FilledTonalIconButton(
                            onClick = { sheet = true },
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurface)
                        ) { Icon(Icons.Rounded.Add, "Tambahkan ke chat") }
                        FilledIconButton(
                            onClick = { if (vm.busy) vm.stop() else if (canSend) { vm.send(input.trim()); input = "" } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(40.dp), enabled = canSend,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (vm.busy) cs.secondaryContainer else cs.primaryContainer,
                                contentColor = if (vm.busy) cs.onSecondaryContainer else cs.onPrimaryContainer,
                                disabledContainerColor = cs.surfaceContainerHighest, disabledContentColor = cs.onSurfaceVariant.copy(alpha = 0.5f))
                        ) { if (vm.busy) Icon(Icons.Rounded.Stop, "Berhenti") else Icon(Icons.Rounded.ArrowUpward, "Kirim") }
                    }
                }
            }
        }
    }
    if (sheet) AddSheet(vm, onDismiss = { sheet = false }, onTop = { toTop() }, onBottom = { toBottom() },
        onCamera = {
            runCatching {
                val f = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "cam_${System.currentTimeMillis()}.jpg")
                val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f); camUri = u; takePhoto.launch(u)
            }
        },
        onPhoto = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onFile = { pickFile.launch(arrayOf("*/*")) })
}

@Composable
private fun AttachmentChip(a: Attachment, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.padding(top = 8.dp, end = 8.dp)) {
        if (a.thumb != null) Image(a.thumb.asImageBitmap(), a.name, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        else Surface(color = cs.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(64.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Description, null, Modifier.size(22.dp))
                Text(a.name, Modifier.widthIn(max = 140.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(onClick = onRemove, shape = CircleShape, color = cs.onSurface, contentColor = cs.surface,
            modifier = Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp).size(22.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Hapus", Modifier.size(14.dp)) }
        }
    }
}

// ───────────── Sheet "Tambahkan ke chat" ─────────────
@Composable
private fun AddSheet(vm: MainVM, onDismiss: () -> Unit, onTop: () -> Unit, onBottom: () -> Unit, onCamera: () -> Unit, onPhoto: () -> Unit, onFile: () -> Unit) {
    val ctx = LocalContext.current
    val goTab = LocalGoTab.current
    val cs = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit = {}) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); after() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = cs.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                IconButton({ close() }, Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.Close, "Tutup") }
                Text("Tambahkan ke chat", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddTile("Kamera", Icons.Rounded.PhotoCamera, Modifier.weight(1f)) { close(onCamera) }
                AddTile("Foto", Icons.Rounded.Image, Modifier.weight(1f)) { close(onPhoto) }
                AddTile("File", Icons.Rounded.AttachFile, Modifier.weight(1f)) { close(onFile) }
            }
            AddRow("Workspace", vm.workspaces.getOrNull(vm.active)?.let { it.name + " · " + it.path }, Icons.Rounded.Folder,
                onClick = { close { goTab(1) } }) { Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant) }
            SwitchRow("Konfirmasi perintah", "Tanya dulu sebelum shell / tulis file", vm.confirm, Icons.Rounded.VerifiedUser) { vm.confirm = it; vm.save() }
            AddRow("Ulangi pesan terakhir", null, Icons.Rounded.Refresh, enabled = !vm.busy && vm.msgs.any { it.role == "user" }, onClick = { close { vm.retry() } })
            AddRow("Ke awal chat", null, Icons.Rounded.VerticalAlignTop, enabled = vm.msgs.isNotEmpty(), onClick = { close(onTop) })
            AddRow("Ke akhir chat", null, Icons.Rounded.VerticalAlignBottom, enabled = vm.msgs.isNotEmpty(), onClick = { close(onBottom) })
            AddRow("Salin seluruh chat", null, Icons.Rounded.ContentCopy, enabled = vm.msgs.isNotEmpty(), onClick = { close { copyToClipboard(ctx, vm.transcript()) } })
            AddRow("Hapus chat ini", null, Icons.Rounded.DeleteSweep, enabled = !vm.busy && vm.currentId != null, danger = true, onClick = { close { vm.deleteAsk = vm.currentId } })
            Spacer(Modifier.height(8.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun AddTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick, modifier, shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = cs.surfaceContainerHighest, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(26.dp)) }
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AddRow(title: String, sub: String?, icon: ImageVector, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val tint = if (danger) cs.error else cs.onSurface
    Surface(onClick, Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), enabled = enabled, shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = cs.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

@Composable
fun Bubble(m: Msg, idx: Int, isLast: Boolean, busy: Boolean, onEdit: (Int) -> Unit, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val isUser = m.role == "user"
    val (bg, fg) = when (m.role) {
        "user" -> cs.surfaceContainerHigh to cs.onSurface          // bubble abu netral (gaya ChatGPT/Claude)
        "tool" -> cs.surfaceContainerLow to cs.onSurfaceVariant
        "error" -> cs.errorContainer to cs.onErrorContainer
        else -> Color.Transparent to cs.onSurface                  // balasan AI tanpa bubble, langsung di latar
    }
    var open by remember { mutableStateOf(false) }
    if (m.role == "assistant" && m.text.isBlank()) return   // placeholder streaming yang belum berisi teks
    val streamingNow = m.role == "assistant" && isLast && busy
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = bg, contentColor = fg, shape = RoundedCornerShape(if (m.role == "tool") 14.dp else 20.dp),
            border = if (m.role == "tool") BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)) else null,
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
                "user" -> SelectionContainer {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (m.text.isNotBlank()) Text(m.text)
                        if (m.extra.isNotBlank()) Text(m.extra, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
                "error" -> SelectionContainer { Text(m.text, Modifier.padding(12.dp)) }
                else -> SelectionContainer { Markdown(m.text, Modifier.padding(horizontal = 4.dp, vertical = 2.dp), streaming = isLast && busy) }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val tint = cs.onSurfaceVariant
            if (!streamingNow) IconButton({ copyToClipboard(ctx, if (m.role == "tool" && m.extra.isNotBlank()) m.text + "\n\n" + m.extra else m.text) }, Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ContentCopy, "Salin", Modifier.size(16.dp), tint = tint)
            }
            if (m.role == "error" && isLast && !busy) FilledTonalButton(onRetry, Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ulangi", style = MaterialTheme.typography.labelLarge)
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
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf(false) }
    Page("Workspace") {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                itemsIndexed(vm.workspaces) { i, w ->
                    SettingRow(i, vm.workspaces.size, w.name, w.path, if (w.path == "/") Icons.Rounded.AdminPanelSettings else Icons.Rounded.Folder,
                        { vm.active = i; vm.save() }) { if (i == vm.active) Icon(Icons.Rounded.CheckCircle, "Aktif", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            FabMenu(Modifier.align(Alignment.BottomEnd).padding(16.dp),
                listOf("Workspace baru" to Icons.Rounded.CreateNewFolder, "Hapus yang aktif" to Icons.Rounded.Delete)) {
                if (it == 0) dlg = true else if (vm.workspaces.size > 1) { vm.workspaces.removeAt(vm.active); vm.active = 0; vm.save() }
            }
        }
    }
    if (dlg) {
        val rules = remember(vm.mode) { wsRules(vm.mode, ctx) }
        var n by remember { mutableStateOf("") }; var p by remember { mutableStateOf("/storage/emulated/0/") }
        var picker by remember { mutableStateOf(false) }
        val path = normPath(if (vm.mode == Mode.ROOT && p.trim().lowercase() == "root") "/root" else p)
        val valid = p.trim().let { it.startsWith("/") || (vm.mode == Mode.ROOT && it.lowercase() == "root") } && rules.canPick(path)
        AlertDialog(onDismissRequest = { dlg = false }, title = { Text("Workspace baru") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Nama") }, singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Path folder") }, singleLine = true, isError = p.isNotBlank() && !valid,
                    trailingIcon = { IconButton({ picker = true }) { Icon(Icons.Rounded.FolderOpen, "Pilih folder") } })
                Text(rules.hint, style = MaterialTheme.typography.bodySmall,
                    color = if (vm.mode == Mode.ROOT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.isNotBlank() && !valid) Text("Path ini tidak diizinkan pada mode ${rules.label}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { Button({
                if (valid) {
                    val name = n.ifBlank { path.substringAfterLast('/').ifEmpty { "Root (/)" } }
                    vm.workspaces.add(Workspace(name, path)); vm.save(); dlg = false
                }
            }, enabled = valid) { Text("Tambah") } },
            dismissButton = { TextButton({ dlg = false }) { Text("Batal") } })
        if (picker) FolderPickerSheet(vm.mode, path, { picker = false }) { p = it; if (n.isBlank()) n = it.substringAfterLast('/').ifEmpty { "Root (/)" } }
    }
}

// ───────────── Aturan & picker folder workspace per mode ─────────────
class WsQuick(val label: String, val path: String)
class WsRules(
    val label: String, val hint: String, val start: String, val quick: List<WsQuick>,
    val canBrowse: (String) -> Boolean, val canPick: (String) -> Boolean
)

fun normPath(p: String): String {
    val parts = mutableListOf<String>()
    for (seg in p.trim().split("/")) when (seg) {
        "", "." -> {}
        ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
        else -> parts.add(seg)
    }
    return "/" + parts.joinToString("/")
}

fun wsRules(mode: Mode, ctx: Context): WsRules {
    val ext = "/storage/emulated/0"
    return when (mode) {
        // Non-root: dikurung di penyimpanan internal + folder privat app sendiri.
        Mode.NONROOT -> {
            val allowed = listOf(ext, "/sdcard", ctx.dataDir.absolutePath)
            val ok = { p: String -> allowed.any { p == it || p.startsWith("$it/") } }
            WsRules("Non-root", "Non-root: hanya penyimpanan internal & folder app sendiri (butuh izin 'All files access').", ext,
                listOf(WsQuick("Penyimpanan", ext), WsQuick("Folder app", ctx.filesDir.absolutePath)), ok, ok)
        }
        // Shizuku: shell uid 2000 -> bisa menjelajah hampir seluruh sistem + Android/data, tapi tidak bisa menulis di '/' atau area sistem.
        Mode.SHIZUKU -> WsRules("Shizuku",
            "Shizuku (shell/adb): bisa menjelajah luas termasuk Android/data & /data/local/tmp. Folder '/' dan area sistem hanya untuk mode Root.", ext,
            listOf(WsQuick("Penyimpanan", ext), WsQuick("Android/data", "$ext/Android/data"), WsQuick("/data/local/tmp", "/data/local/tmp")),
            { true }, { it != "/" && !it.startsWith("/system") && !it.startsWith("/vendor") && !it.startsWith("/proc") })
        // Root: bebas, termasuk '/'.
        Mode.ROOT -> WsRules("Root",
            "Root: bebas memilih folder mana pun, termasuk '/'. Hati-hati, perintah AI bisa mengubah seluruh sistem.", "/",
            listOf(WsQuick("/", "/"), WsQuick("/root", "/root"), WsQuick("/data", "/data"), WsQuick("/sdcard", ext), WsQuick("/data/local/tmp", "/data/local/tmp")),
            { true }, { true })
    }
}

@Composable
fun FolderPickerSheet(mode: Mode, initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val rules = remember(mode) { wsRules(mode, ctx) }
    var path by remember { mutableStateOf(if (initial.isNotBlank() && rules.canBrowse(initial)) initial else rules.start) }
    var dirs by remember { mutableStateOf<List<String>?>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(path, mode) { loading = true; dirs = Exec.listDirs(mode, path); loading = false }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val parent = if (path == "/") null else path.substringBeforeLast('/').ifEmpty { "/" }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = cs.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pilih folder workspace", style = MaterialTheme.typography.titleLarge)
            Text(rules.hint, style = MaterialTheme.typography.bodySmall, color = if (mode == Mode.ROOT) cs.error else cs.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rules.quick.forEach { qk -> FilterChip(path == qk.path, { path = qk.path }, label = { Text(qk.label) }) }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ parent?.let { path = it } }, enabled = parent != null && rules.canBrowse(parent)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Naik satu level") }
                    Text(path, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp), contentAlignment = Alignment.Center) {
                val list = dirs
                when {
                    loading -> LoadingIndicator(Modifier.size(40.dp))
                    list == null -> Text("Folder tidak bisa dibaca (izin ditolak / tidak ada)." + if (mode == Mode.ROOT) " Pastikan akses root sudah diberikan." else "",
                        color = cs.error, style = MaterialTheme.typography.bodyMedium)
                    list.isEmpty() -> Text("Tidak ada subfolder.", color = cs.onSurfaceVariant)
                    else -> LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        itemsIndexed(list) { i, d ->
                            val child = if (path == "/") "/$d" else "$path/$d"
                            SettingRow(i, list.size, d, null, Icons.Rounded.Folder, { path = child }, enabled = rules.canBrowse(child)) {
                                Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (!rules.canPick(path)) Text("Folder ini tidak dapat dipilih pada mode ${rules.label}.", style = MaterialTheme.typography.bodySmall, color = cs.error)
            Button({
                scope.launch { state.hide() }.invokeOnCompletion { onPick(path); onDismiss() }
            }, Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp), enabled = rules.canPick(path)) {
                Icon(Icons.Rounded.Check, null, Modifier.padding(end = 8.dp)); Text("Pilih folder ini")
            }
            Spacer(Modifier.height(8.dp).navigationBarsPadding())
        }
    }
}

// ───────────── Setelan ─────────────
@Composable
fun Choice(options: List<String>, sel: Int, minHeight: Dp = 52.dp, onSel: (Int) -> Unit) =
    // Connected button group M3 Expressive: sudut dalam kecil, sudut luar pill,
    // item terpilih berubah jadi pill penuh (morph) + animasi spring.
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { i, o ->
            ToggleButton(
                checked = sel == i,
                onCheckedChange = { if (it) onSel(i) },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = minHeight)
                    .semantics { role = androidx.compose.ui.semantics.Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                AnimatedVisibility(sel == i, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(18.dp))
                }
                Text(o, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }

/** Kartu setelan: badge ikon bulat + judul/subjudul + slot trailing. Warna ikut state (aktif -> container tonal). */
@Composable
fun SettingCard(
    title: String, sub: String?, icon: ImageVector, modifier: Modifier = Modifier,
    active: Boolean = false, tint: Color? = null, onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (active) cs.primaryContainer else cs.surfaceContainerHighest, label = "cardBg")
    val badge by animateColorAsState(if (active) cs.primary else cs.surfaceContainerHigh, label = "badgeBg")
    val badgeFg by animateColorAsState(if (active) cs.onPrimary else (tint ?: cs.onSurfaceVariant), label = "badgeFg")
    val fg = if (active) cs.onPrimaryContainer else cs.onSurface
    val body: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = CircleShape, color = badge, contentColor = badgeFg, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = if (active) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
            }
            trailing()
        }
    }
    if (onClick != null) Surface(onClick, modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = bg, content = body)
    else Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = bg, content = body)
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, icon: ImageVector = Icons.Rounded.Tune, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (checked) cs.primaryContainer else cs.surfaceContainerHighest, label = "rowBg")
    val badge by animateColorAsState(if (checked) cs.primary else cs.surfaceContainerHigh, label = "rowBadge")
    val badgeFg by animateColorAsState(if (checked) cs.onPrimary else cs.onSurfaceVariant, label = "rowBadgeFg")
    Surface(
        checked = checked, onCheckedChange = onChange, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = bg
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = CircleShape, color = badge, contentColor = badgeFg, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (checked) cs.onPrimaryContainer else cs.onSurface)
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = if (checked) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
            }
            Switch(checked, null, thumbContent = {
                Crossfade(checked, label = "thumb") { on ->
                    Icon(if (on) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize))
                }
            })
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) =
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.padding(start = 4.dp, bottom = 2.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }

// ───────────── Connected list (shape morphing) ─────────────
/** Grup item yang menyatu: sudut luar besar, sudut dalam kecil, morph jadi bulat penuh saat ditekan. */
@Composable
fun SettingGroup(title: String, vararg rows: @Composable (Int, Int) -> Unit) = SettingGroup(title, rows.toList())

@Composable
fun SettingGroup(title: String, rows: List<@Composable (Int, Int) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Text(title, Modifier.padding(start = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        rows.forEachIndexed { i, r -> r(i, rows.size) }
    }
}

/** Slider ukuran sedang (track 12dp, handle pill 5x32dp). */
@Composable
fun CompactSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val active = cs.primary; val inactive = cs.secondaryContainer
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    var w by remember { mutableIntStateOf(1) }
    val cur by rememberUpdatedState(onChange)
    fun at(x: Float) = range.start + (x / w).coerceIn(0f, 1f) * span
    Canvas(
        modifier.fillMaxWidth().height(38.dp).onSizeChanged { w = it.width.coerceAtLeast(1) }
            .pointerInput(range) { detectTapGestures { cur(at(it.x)) } }
            .pointerInput(range) { detectHorizontalDragGestures { change, _ -> change.consume(); cur(at(change.position.x)) } }
    ) {
        val th = 12.dp.toPx(); val hw = 5.dp.toPx(); val hh = 32.dp.toPx(); val gap = 6.dp.toPx(); val cy = size.height / 2
        val tx = (frac * size.width).coerceIn(hw / 2, size.width - hw / 2)
        drawRoundRect(inactive, Offset((tx + gap).coerceAtMost(size.width), cy - th / 2), Size((size.width - tx - gap).coerceAtLeast(0f), th), CornerRadius(th / 2))
        drawRoundRect(active, Offset(0f, cy - th / 2), Size((tx - gap).coerceAtLeast(0f), th), CornerRadius(th / 2))
        drawRoundRect(active, Offset(tx - hw / 2, cy - hh / 2), Size(hw, hh), CornerRadius(hw / 2))
    }
}

/** Spesifikasi satu parameter: rentang slider, rentang "disarankan" untuk custom, contoh & petunjuk. */
class ParamSpec(
    val min: Float, val max: Float, val step: Float, val decimals: Int, val start: Float,
    val softMin: Float, val softMax: Float, val example: String, val hint: String
)

/**
 * Baris parameter 3 mode: [Default] [Slider] [Custom].
 * Slider = cepat & aman dalam rentang umum; Custom = ketik angka sendiri (boleh di luar rentang slider, ada petunjuk & peringatan).
 */
@Composable
fun ParamRow(
    i: Int, n: Int, title: String, icon: ImageVector, desc: String, knob: Knob, spec: ParamSpec,
    defLabel: String = "Default", onChange: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    fun fmt(v: Float) = if (spec.decimals == 0) v.roundToInt().toString() else "%.${spec.decimals}f".format(v)
    val parsed = knob.text.trim().replace(',', '.').toFloatOrNull()
    val sliderVal = (if (knob.value.isNaN()) spec.start else knob.value).coerceIn(spec.min, spec.max)
    val label = when (knob.mode) {
        0 -> defLabel
        1 -> fmt(sliderVal)
        else -> knob.text.trim().ifEmpty { "—" }
    }
    SegmentedListItem(
        onClick = {}, shapes = ListItemDefaults.segmentedShapes(i, n),
        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh)
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(icon, null, Modifier.padding(top = 2.dp), tint = cs.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text(label, Modifier.padding(top = 2.dp).widthIn(min = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelLarge, color = if (knob.mode == 0) cs.onSurfaceVariant else cs.primary)
            }
            var menu by remember { mutableStateOf(false) }
            val modeNames = listOf("Default", "Slider", "Custom")
            val modeSubs = listOf("Pakai nilai bawaan provider/aplikasi", "Pilih cepat dalam rentang umum", "Ketik nilai sendiri, bebas rentang")
            Box {
                Surface(onClick = { menu = true }, shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mode", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(modeNames[knob.mode.coerceIn(0, 2)], style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = cs.onSurface)
                        Icon(Icons.Rounded.UnfoldMore, null, Modifier.padding(start = 4.dp), tint = cs.onSurfaceVariant)
                    }
                }
                DropdownMenu(menu, { menu = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(24.dp),
                    containerColor = cs.surfaceContainerHighest, tonalElevation = 6.dp, shadowElevation = 8.dp) {
                    modeNames.forEachIndexed { m, name ->
                        val picked = m == knob.mode
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp).widthIn(min = 240.dp)
                                .clip(RoundedCornerShape(if (picked) 20.dp else 14.dp))
                                .background(if (picked) cs.primaryContainer else Color.Transparent)
                                .clickable {
                                    menu = false
                                    if (m == 1 && knob.value.isNaN()) knob.value = (parsed ?: spec.start).coerceIn(spec.min, spec.max)
                                    if (m == 2 && knob.text.isBlank() && !knob.value.isNaN()) knob.text = fmt(knob.value)
                                    knob.mode = m; onChange()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium, color = if (picked) cs.onPrimaryContainer else cs.onSurface)
                                Text(modeSubs[m], style = MaterialTheme.typography.bodySmall,
                                    color = if (picked) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
                            }
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (picked) Icon(Icons.Rounded.Check, null, tint = cs.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
            if (knob.mode == 1) Column {
                CompactSlider(sliderVal, { x ->
                    val snapped = ((x / spec.step).roundToInt() * spec.step).coerceIn(spec.min, spec.max)
                    knob.value = Math.round(snapped * 1000f) / 1000f; onChange()
                }, spec.min..spec.max)
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt(spec.min), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(fmt(spec.max), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            if (knob.mode == 2) {
                val bad = knob.text.isNotBlank() && parsed == null
                val outside = parsed != null && (parsed < spec.softMin || parsed > spec.softMax)
                OutlinedTextField(
                    knob.text, { knob.text = it; onChange() }, Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(16.dp), label = { Text("Nilai custom") },
                    placeholder = { Text("mis. ${spec.example}") }, isError = bad || outside,
                    keyboardOptions = KeyboardOptions(keyboardType = if (spec.decimals == 0) KeyboardType.Number else KeyboardType.Decimal),
                    supportingText = { Text(when {
                        bad -> "Bukan angka yang valid."
                        outside -> "Di luar rentang disarankan (${fmt(spec.softMin)}–${fmt(spec.softMax)}). Provider mungkin menolak."
                        else -> spec.hint + " Kosong = default."
                    }) }
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    i: Int, n: Int, title: String, sub: String?, icon: ImageVector?, onClick: () -> Unit,
    enabled: Boolean = true, trailing: @Composable () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    // Semua isi ditaruh di satu Row (CenterVertically) supaya ikon & trailing selalu
    // di tengah vertikal, berapa pun jumlah baris judul + deskripsi.
    SegmentedListItem(
        onClick = onClick, shapes = ListItemDefaults.segmentedShapes(i, n), enabled = enabled,
        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) Icon(icon, null, tint = cs.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Box(contentAlignment = Alignment.Center) { trailing() }
        }
    }
}

/** Baris dengan nilai saat ini + panah naik/turun; klik membuka daftar pilihan mengambang. */
@Composable
fun DropdownRow(
    i: Int, n: Int, title: String, sub: String?, icon: ImageVector?, options: List<String>, sel: Int,
    optionSubs: List<String>? = null, onSel: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    SettingRow(i, n, title, sub, icon, { open = true }) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(options.getOrElse(sel) { "" }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Icon(Icons.Rounded.UnfoldMore, null, tint = cs.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = open, onDismissRequest = { open = false },
                offset = DpOffset(0.dp, 8.dp), shape = RoundedCornerShape(28.dp),
                containerColor = cs.surfaceContainerHighest, tonalElevation = 6.dp, shadowElevation = 8.dp
            ) {
                options.forEachIndexed { j, o ->
                    val picked = j == sel
                    val bg by animateColorAsState(if (picked) cs.primaryContainer else Color.Transparent, label = "menuItemBg")
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp).widthIn(min = 220.dp)
                            .clip(RoundedCornerShape(if (picked) 22.dp else 16.dp)).background(bg)
                            .clickable { open = false; onSel(j) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(o, style = MaterialTheme.typography.titleMedium,
                                color = if (picked) cs.onPrimaryContainer else cs.onSurface)
                            optionSubs?.getOrNull(j)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = if (picked) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
                            }
                        }
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (picked) Icon(Icons.Rounded.Check, null, tint = cs.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwitchSettingRow(i: Int, n: Int, title: String, sub: String, icon: ImageVector?, checked: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(i, n, title, sub, icon, { onChange(!checked) }) {
        Switch(checked, null, thumbContent = {
            Crossfade(checked, label = "thumb") { on ->
                Icon(if (on) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize))
            }
        })
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
    LaunchedEffect(Unit) { if (vm.mode == Mode.ROOT && vm.rootOk == null) vm.checkRoot() }
    Page("Setelan") {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val cs0 = MaterialTheme.colorScheme
            var showKey by remember { mutableStateOf(false) }
            var modelMenu by remember { mutableStateOf(false) }
            val anth = vm.kind == 1
            SettingGroup("Provider AI", buildList<@Composable (Int, Int) -> Unit> {
                add { i, n -> DropdownRow(i, n, "Provider", "Layanan AI yang dipakai untuk chat.", Icons.Rounded.Cloud,
                    Provider.entries.map { it.label }, vm.provider.ordinal, Provider.entries.map { it.sub }) { vm.pickProvider(Provider.entries[it]) } }
                if (vm.provider == Provider.CUSTOM) add { i, n -> DropdownRow(i, n, "Format API", "Format request yang dipakai endpoint custom.", Icons.Rounded.Code,
                    listOf("OpenAI-compatible", "Anthropic (Messages)"), if (vm.customAnthropic) 1 else 0) { vm.customAnthropic = it == 1; vm.save() } }
            })
            Section("Kredensial · ${vm.provider.label}") {
                OutlinedTextField(vm.baseUrl, { vm.baseUrl = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("Endpoint (base URL)") }, singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    supportingText = { Text(if (anth) "Dipanggil: …/messages" else "Dipanggil: …/chat/completions") })
                OutlinedTextField(vm.apiKey, { vm.apiKey = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Tampilkan/sembunyikan") } })
                Box {
                    OutlinedTextField(vm.model, { vm.model = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        trailingIcon = {
                            if (vm.modelsLoading) LoadingIndicator(Modifier.size(32.dp))
                            else IconButton({ if (vm.models.isEmpty()) vm.fetchModels() else modelMenu = true }) { Icon(Icons.Rounded.ArrowDropDown, "Pilih model") }
                        })
                    DropdownMenu(modelMenu, { modelMenu = false }, Modifier.heightIn(max = 320.dp), shape = RoundedCornerShape(24.dp),
                        containerColor = cs0.surfaceContainerHighest) {
                        vm.models.forEach { m ->
                            val picked = m == vm.model
                            DropdownMenuItem(text = { Text(m, color = if (picked) cs0.primary else cs0.onSurface) },
                                onClick = { vm.model = m; vm.save(); modelMenu = false },
                                trailingIcon = if (picked) ({ Icon(Icons.Rounded.Check, null, tint = cs0.primary) }) else null)
                        }
                    }
                }
                FilledTonalButton({ vm.fetchModels(); modelMenu = false }, enabled = !vm.modelsLoading, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                    Icon(Icons.Rounded.Sync, null, Modifier.padding(end = 8.dp).size(18.dp)); Text("Tes koneksi & muat daftar model")
                }
                vm.modelsMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Gagal")) cs0.error else cs0.primary) }
                if (vm.models.isNotEmpty()) TextButton({ modelMenu = true }) { Text("Pilih dari ${vm.models.size} model") }
                when (vm.provider) {
                    Provider.CLAUDE -> "Header x-api-key & anthropic-version dikirim otomatis. Tool use didukung."
                    Provider.OLLAMA -> "API key boleh kosong. Ganti localhost dengan IP server bila Ollama tidak berjalan di perangkat ini."
                    Provider.OPENROUTER -> "Nama model berformat vendor/model, mis. anthropic/claude-sonnet-4.5."
                    Provider.CUSTOM -> "Isi endpoint yang kompatibel. API key dikirim sebagai " + if (anth) "x-api-key." else "Bearer token."
                    else -> null
                }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs0.onSurfaceVariant) }
            }
            SettingGroup("Respons", buildList<@Composable (Int, Int) -> Unit> {
                add { i, n -> SwitchSettingRow(i, n, "Streaming", "Tampilkan jawaban AI sambil diketik, tidak menunggu selesai.", Icons.Rounded.Bolt, vm.streaming) { vm.streaming = it; vm.save() } }
                if (!anth) add { i, n -> DropdownRow(i, n, "Reasoning effort", "Untuk model penalaran (o-series, GPT-5, dll). Mati = tidak dikirim.", Icons.Rounded.Psychology,
                    listOf("Mati", "Rendah", "Sedang", "Tinggi"), vm.reasoning) { vm.reasoning = it; vm.save() } }
            })
            val tMax = if (anth) 1f else 2f
            SettingGroup("Parameter model", buildList<@Composable (Int, Int) -> Unit> {
                add { i, n -> ParamRow(i, n, "Temperature", Icons.Rounded.Thermostat,
                    "Tingkat acak jawaban. Rendah = konsisten & fokus, tinggi = kreatif." + if (anth) " Claude maksimal 1.0." else "",
                    vm.kTemp, ParamSpec(0f, tMax, 0.05f, 2, 1f.coerceAtMost(tMax), 0f, tMax, "0.7", "Disarankan 0–${if (anth) "1" else "2"}.")) { vm.save() } }
                add { i, n -> ParamRow(i, n, "Top P", Icons.Rounded.FilterAlt,
                    if (anth) "Batasi pilihan token ke probabilitas teratas. Claude: jangan diatur bersama Temperature." else "Batasi pilihan token ke massa probabilitas teratas (nucleus sampling).",
                    vm.kTopP, ParamSpec(0f, 1f, 0.05f, 2, 1f, 0f, 1f, "0.9", "Disarankan 0–1.")) { vm.save() } }
                add { i, n -> ParamRow(i, n, "Maks token output", Icons.Rounded.Notes,
                    if (anth) "Batas panjang jawaban. Claude wajib punya batas; default 4096." else "Batas panjang jawaban AI. Default mengikuti provider.",
                    vm.kTokens, ParamSpec(256f, 32768f, 256f, 0, 4096f, 1f, 200000f, "8192", "Slider sampai 32.768; custom bebas sesuai batas model.")) { vm.save() } }
                if (!anth) {
                    add { i, n -> ParamRow(i, n, "Frequency penalty", Icons.Rounded.Repeat, "Positif = kurangi pengulangan kata yang sama.",
                        vm.kFreq, ParamSpec(-2f, 2f, 0.1f, 1, 0f, -2f, 2f, "0.5", "Disarankan -2 sampai 2.")) { vm.save() } }
                    add { i, n -> ParamRow(i, n, "Presence penalty", Icons.Rounded.AutoAwesome, "Positif = dorong AI membahas topik baru.",
                        vm.kPres, ParamSpec(-2f, 2f, 0.1f, 1, 0f, -2f, 2f, "0.5", "Disarankan -2 sampai 2.")) { vm.save() } }
                }
            })
            SettingGroup("Agen & jaringan",
                { i, n -> ParamRow(i, n, "Langkah tool maksimum", Icons.Rounded.Layers, "Batas putaran AI memanggil tool dalam satu pesan.",
                    vm.kSteps, ParamSpec(1f, 25f, 1f, 0, 8f, 1f, 50f, "12", "Disarankan 1–50."), "Default (8)") { vm.save() } },
                { i, n -> ParamRow(i, n, "Timeout", Icons.Rounded.Timer, "Batas waktu menunggu data dari server (detik).",
                    vm.kTimeout, ParamSpec(30f, 600f, 15f, 0, 180f, 5f, 3600f, "300", "Disarankan 5–3600 detik."), "Default (180)") { vm.save() } },
                { i, n -> ParamRow(i, n, "Coba ulang otomatis", Icons.Rounded.Replay, "Saat kena rate limit (429) atau error server sementara.",
                    vm.kRetries, ParamSpec(0f, 6f, 1f, 0, 3f, 0f, 10f, "5", "Disarankan 0–10."), "Default (3)") { vm.save() } },
                { i, n -> SettingRow(i, n, "Setel ulang semua parameter", "Kembalikan semua parameter ke mode Default.", Icons.Rounded.RestartAlt, { vm.resetParams() }) }
            )
            Section("Instruksi sistem") {
                OutlinedTextField(vm.system, { vm.system = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("System prompt") }, minLines = 3, shape = RoundedCornerShape(20.dp))
            }
            SettingGroup("Akses",
                { i, n -> DropdownRow(i, n, "Mode akses",
                    when (vm.mode) {
                        Mode.NONROOT -> "Terbatas: sandbox app + storage yang diizinkan (semua file lewat izin 'All files access')."
                        Mode.SHIZUKU -> "Menengah: shell uid 2000 (adb). Akses lebih luas dari app biasa, tidak penuh."
                        Mode.ROOT -> "Penuh: seluruh sistem. Berisiko tinggi." + (vm.rootOk?.let { if (it) " (root terdeteksi)" else " (root TIDAK tersedia)" } ?: "")
                    }, Icons.Rounded.AdminPanelSettings, listOf("Non-root", "Shizuku", "Root"), vm.mode.ordinal,
                    listOf("Sandbox app + storage izin", "Shell uid 2000 (adb)", "Seluruh sistem")) { vm.pickMode(Mode.entries[it]) } },
                { i, n -> SwitchSettingRow(i, n, "Konfirmasi perintah", "Minta persetujuan sebelum shell/tulis file", Icons.Rounded.VerifiedUser, vm.confirm) { vm.confirm = it; vm.save() } }
            )
            val cs = MaterialTheme.colorScheme
            val su = remember(vm.rootOk) { vm.suBinaryVisible() }
            SettingGroup("Izin",
                { i, n -> SettingRow(i, n, "Akses semua file", if (allFiles) "Diberikan" else "Belum diberikan", Icons.Rounded.Folder, {
                    if (Build.VERSION.SDK_INT >= 30) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    else perm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }) { Icon(if (allFiles) Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight, null, tint = if (allFiles) cs.primary else cs.onSurfaceVariant) } },
                { i, n -> SettingRow(i, n, "Shizuku",
                    if (!shizukuUp) "Tidak berjalan (ketuk untuk menyegarkan)" else if (shizukuOk) "Berjalan, izin diberikan" else "Berjalan, izin belum diberikan (ketuk untuk meminta)",
                    Icons.Rounded.Adb, { runCatching { if (shizukuUp && !shizukuOk) Shizuku.requestPermission(0) }; tick++ }) {
                    if (shizukuUp && shizukuOk) Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary)
                    else Icon(Icons.Rounded.Refresh, null, tint = cs.onSurfaceVariant)
                } },
                { i, n -> SettingRow(i, n, "Akses root aplikasi",
                    when {
                        vm.rootChecking -> "Memeriksa… setujui permintaan root jika muncul."
                        vm.rootOk == true -> "Sudah diberikan. MiraiCode bisa menjalankan perintah sebagai root."
                        vm.rootOk == false -> "Belum diberikan / tidak tersedia. " + if (su) "su terdeteksi: izinkan MiraiCode di Magisk/KernelSU, lalu ketuk untuk periksa ulang." else "su tidak terdeteksi, perangkat mungkin tidak ter-root."
                        else -> "Belum diperiksa (ketuk untuk memeriksa). " + if (su) "su terdeteksi di perangkat." else "su tidak terlihat oleh aplikasi (belum tentu tidak ter-root)."
                    },
                    Icons.Rounded.Security, { vm.checkRoot() }, enabled = !vm.rootChecking) {
                    if (vm.rootChecking) LoadingIndicator(Modifier.size(32.dp))
                    else when (vm.rootOk) {
                        true -> Icon(Icons.Rounded.CheckCircle, "Diberikan", tint = cs.primary)
                        false -> Icon(Icons.Rounded.Cancel, "Belum", tint = cs.error)
                        null -> Icon(Icons.Rounded.Info, "Belum diperiksa", tint = cs.onSurfaceVariant)
                    }
                } }
            )
            SettingGroup("Tampilan",
                { i, n -> DropdownRow(i, n, "Tema", "Ikuti sistem, terang, gelap, atau hitam pekat (AMOLED).", Icons.Rounded.DarkMode, listOf("Sistem", "Terang", "Gelap", "AMOLED"), vm.theme) { vm.theme = it; vm.save() } },
                { i, n -> SwitchSettingRow(i, n, "Dynamic color", if (Build.VERSION.SDK_INT >= 31) "Ikuti wallpaper (Android 12+)" else "Perlu Android 12+", Icons.Rounded.Palette, vm.dynamic) { vm.dynamic = it; vm.save() } }
            )
        }
    }
}
