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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.draw.alpha
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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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
    val q = query.trim()
    val hl = cs.primary.copy(alpha = 0.3f)
    val snapshot = vm.chats.toList()
    val rows: List<Any> = remember(q, snapshot, hl) {
        if (q.isEmpty()) buildList { var last = ""; snapshot.forEach { m -> val b = bucket(m.updated); if (b != last) { add(b); last = b }; add(m) } }
        else snapshot.mapNotNull { hitOf(it, q, hl) }
    }
    Text("MiraiCode", Modifier.padding(28.dp, 20.dp, 16.dp, 8.dp), style = MaterialTheme.typography.headlineSmall)
    NavigationDrawerItem(
        label = { Text("Chat baru") }, icon = { Icon(Icons.Rounded.Add, null) }, selected = false,
        onClick = { if (!vm.busy) { vm.newChat(); onTab(0) }; close() },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
    OutlinedTextField(
        query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        placeholder = { Text("Cari chat") }, singleLine = true, shape = RoundedCornerShape(28.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Hapus pencarian") } }
    )
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (rows.isEmpty()) item {
            Text(if (q.isEmpty()) "Belum ada riwayat chat." else "Tidak ada chat yang cocok dengan \"$q\".",
                Modifier.padding(28.dp, 16.dp), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        items(rows) { r ->
            when (r) {
                is String -> Text(r, Modifier.padding(28.dp, 14.dp, 16.dp, 4.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                is ChatMeta -> ChatRow(vm, r, null, q, hl, tab == 0 && r.id == vm.currentId) { onTab(0); vm.openChat(r.id); close() }
                is Hit -> ChatRow(vm, r.meta, r.snippet, q, hl, tab == 0 && r.meta.id == vm.currentId) { onTab(0); vm.openChat(r.meta.id); close() }
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
                Text(if (q.isEmpty()) AnnotatedString(m.title) else highlight(m.title, q, hl), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                if (snippet != null) Text(snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Box {
                IconButton({ menu = true }, Modifier.size(36.dp)) { Icon(Icons.Rounded.MoreVert, "Opsi chat", Modifier.size(18.dp)) }
                DropdownMenu(menu, { menu = false }) {
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
    Column(Modifier.fillMaxSize().statusBarsPadding().then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)) {
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
@Composable
fun ChatScreen(vm: MainVM) {
    val ls = rememberLazyListState()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
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
    Page(vm.chats.firstOrNull { it.id == vm.currentId }?.title ?: "MiraiCode", actions = {
        AssistChip(onClick = {}, label = { Text(vm.mode.name) }, leadingIcon = { Icon(Icons.Rounded.Security, null, Modifier.size(18.dp)) })
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
                            colors = IconButtonDefaults.filledIconButtonColors(disabledContainerColor = cs.surfaceContainerHighest, disabledContentColor = cs.onSurfaceVariant.copy(alpha = 0.5f))
                        ) { if (vm.busy) Icon(Icons.Rounded.Stop, "Berhenti") else Icon(Icons.Rounded.ArrowUpward, "Kirim") }
                    }
                }
            }
        }
    }
    if (sheet) AddSheet(vm, onDismiss = { sheet = false },
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
private fun AddSheet(vm: MainVM, onDismiss: () -> Unit, onCamera: () -> Unit, onPhoto: () -> Unit, onFile: () -> Unit) {
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
            AddRow("Konfirmasi perintah", "Tanya dulu sebelum shell / tulis file", Icons.Rounded.VerifiedUser,
                onClick = { vm.confirm = !vm.confirm; vm.save() }) { Switch(vm.confirm, { vm.confirm = it; vm.save() }) }
            AddRow("Ulangi pesan terakhir", null, Icons.Rounded.Refresh, enabled = !vm.busy && vm.msgs.any { it.role == "user" }, onClick = { close { vm.retry() } })
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
                else -> SelectionContainer { Markdown(m.text, Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
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
    LaunchedEffect(Unit) { if (vm.mode == Mode.ROOT && vm.rootOk == null) vm.checkRoot() }
    Page("Setelan") {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Provider AI (OpenAI-compatible)") {
                OutlinedTextField(vm.baseUrl, { vm.baseUrl = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("Endpoint (base URL)") }, singleLine = true)
                OutlinedTextField(vm.apiKey, { vm.apiKey = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(vm.model, { vm.model = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                OutlinedTextField(vm.system, { vm.system = it; vm.save() }, Modifier.fillMaxWidth(), label = { Text("System prompt") }, minLines = 3)
            }
            Section("Status root") {
                val cs = MaterialTheme.colorScheme
                val su = remember(vm.rootOk) { vm.suBinaryVisible() }
                ListItem(
                    headlineContent = { Text("Akses root aplikasi") },
                    supportingContent = { Text(when {
                        vm.rootChecking -> "Memeriksa… setujui permintaan root jika muncul."
                        vm.rootOk == true -> "Sudah diberikan. MiraiCode bisa menjalankan perintah sebagai root."
                        vm.rootOk == false -> "Belum diberikan / tidak tersedia. " + if (su) "su terdeteksi: izinkan MiraiCode di Magisk/KernelSU, lalu periksa ulang." else "su tidak terdeteksi, perangkat mungkin tidak ter-root."
                        else -> "Belum diperiksa. " + if (su) "su terdeteksi di perangkat." else "su tidak terlihat oleh aplikasi (belum tentu tidak ter-root)."
                    }) },
                    leadingContent = {
                        if (vm.rootChecking) LoadingIndicator(Modifier.size(32.dp))
                        else when (vm.rootOk) {
                            true -> Icon(Icons.Rounded.CheckCircle, "Diberikan", tint = cs.primary)
                            false -> Icon(Icons.Rounded.Cancel, "Belum", tint = cs.error)
                            null -> Icon(Icons.Rounded.Info, "Belum diperiksa", tint = cs.onSurfaceVariant)
                        }
                    }
                )
                FilledTonalButton({ vm.checkRoot() }, enabled = !vm.rootChecking) { Text(if (vm.rootOk == null) "Periksa akses root" else "Periksa ulang") }
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
