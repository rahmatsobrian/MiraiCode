package com.rahmatsobrian.miraicode

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Workspace(val name: String, val path: String)
/** Lampiran: file teks/kode (dimasukkan ke prompt) atau gambar (JPEG base64, dikirim sebagai image_url). */
class Attachment(val name: String, val text: String? = null, val imageB64: String? = null, val thumb: android.graphics.Bitmap? = null)
data class Msg(val role: String, val text: String, val extra: String = "")
/** Metadata satu percakapan di riwayat. [text] = gabungan teks user+AI (maks 12 rb karakter) untuk pencarian. */
data class ChatMeta(val id: String, val title: String, val updated: Long, val text: String)
class Pending(val title: String, val detail: String, val result: CompletableDeferred<Boolean> = CompletableDeferred())

class MainVM(app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("mirai", 0)
    var baseUrl by mutableStateOf(sp.getString("baseUrl", "https://api.openai.com/v1")!!)
    var apiKey by mutableStateOf(sp.getString("apiKey", "")!!)
    var model by mutableStateOf(sp.getString("model", "gpt-4o-mini")!!)
    var system by mutableStateOf(sp.getString("system", "Kamu MiraiCode, asisten coding agentik di Android. Gunakan tool untuk membaca/menulis file dan menjalankan shell di workspace. Jelaskan singkat apa yang kamu lakukan.")!!)
    var theme by mutableIntStateOf(sp.getInt("theme", 0))        // 0 sistem,1 terang,2 gelap,3 amoled
    var dynamic by mutableStateOf(sp.getBoolean("dynamic", true))
    var confirm by mutableStateOf(sp.getBoolean("confirm", true))
    var mode by mutableStateOf(Mode.valueOf(sp.getString("mode", "NONROOT")!!))
    var active by mutableIntStateOf(sp.getInt("active", 0))
    val workspaces = mutableStateListOf<Workspace>()
    val msgs = mutableStateListOf<Msg>()
    private val hist = JSONArray()
    var busy by mutableStateOf(false)
    var pending by mutableStateOf<Pending?>(null)
    var rootWarn by mutableStateOf(false)
    var rootOk by mutableStateOf<Boolean?>(null)
    var rootChecking by mutableStateOf(false)
    var toolName by mutableStateOf<String?>(null)
    var crash by mutableStateOf<String?>(null)
    private val crashFile = java.io.File(app.filesDir, "crash.txt")
    fun dismissCrash() { crash = null; crashFile.delete() }
    private var job: Job? = null
    val attachments = mutableStateListOf<Attachment>()

    private fun toast(t: String) = android.widget.Toast.makeText(getApplication(), t, android.widget.Toast.LENGTH_SHORT).show()
    private fun displayName(uri: Uri, fallback: String): String = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: fallback

    fun addFile(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = getApplication<Application>().contentResolver
                    val bytes = cr.openInputStream(uri)!!.use { it.readNBytes(200_001) }
                    if (bytes.take(4096).any { it == 0.toByte() }) error("Hanya file teks/kode yang didukung")
                    val cut = bytes.size > 200_000
                    var t = String(bytes, 0, minOf(bytes.size, 200_000), Charsets.UTF_8)
                    if (cut) t += "\n… (dipotong, file lebih dari 200 KB)"
                    Attachment(displayName(uri, "file.txt"), text = t)
                }
            }
            r.onSuccess { attachments.add(it) }.onFailure { toast(it.message ?: "Gagal membaca file") }
        }
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = getApplication<Application>().contentResolver
                    // ImageDecoder menerapkan rotasi EXIF otomatis; dikecilkan ke maks 1280px agar request ringan
                    val bmp = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(cr, uri)) { d, info, _ ->
                        val m = maxOf(info.size.width, info.size.height)
                        if (m > 1280) { val f = 1280f / m; d.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1)) }
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    val bos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, bos)
                    val f = 160f / maxOf(bmp.width, bmp.height)
                    val th = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
                    Attachment(displayName(uri, "foto.jpg"), imageB64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP), thumb = th)
                }
            }
            r.onSuccess { attachments.add(it) }.onFailure { toast("Gagal membaca gambar") }
        }
    }
    fun removeAttachment(i: Int) { if (i in attachments.indices) attachments.removeAt(i) }
    private val oldChatFile = java.io.File(app.filesDir, "chat.json")   // format lama (1 chat) -> dimigrasi
    private val chatDir = java.io.File(app.filesDir, "chats").apply { mkdirs() }
    private val indexFile = java.io.File(chatDir, "index.json")
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()   // tulis file berurutan, tidak di main thread
    val chats = mutableStateListOf<ChatMeta>()           // riwayat, terbaru di atas
    var currentId by mutableStateOf<String?>(null)       // null = chat baru yang belum tersimpan
    var renameAsk by mutableStateOf<ChatMeta?>(null)
    var deleteAsk by mutableStateOf<String?>(null)

    /** Hentikan generate: putus koneksi, tolak konfirmasi yang menunggu, batalkan coroutine. */
    fun stop() { Llm.abort(); pending?.result?.complete(false); job?.cancel() }

    /** Hapus pesan user ke-[msgIdx] beserta semua balasan setelahnya (msgs + riwayat API). Mengembalikan teksnya. */
    fun rollback(msgIdx: Int): String? {
        if (busy) return null
        val m = msgs.getOrNull(msgIdx)?.takeIf { it.role == "user" } ?: return null
        val k = msgs.subList(0, msgIdx).count { it.role == "user" }
        var seen = -1; var cut = -1
        for (i in 0 until hist.length()) if (hist.getJSONObject(i).optString("role") == "user") { seen++; if (seen == k) { cut = i; break } }
        if (cut >= 0) while (hist.length() > cut) hist.remove(hist.length() - 1)
        while (msgs.size > msgIdx) msgs.removeAt(msgs.lastIndex)
        saveChat(); return m.text
    }
    fun retry() {
        val i = msgs.indexOfLast { it.role == "user" }
        if (i >= 0) rollback(i)?.let { send(it) }
    }
    fun transcript() = msgs.joinToString("\n\n") {
        val who = when (it.role) { "user" -> "Kamu"; "assistant" -> "AI"; "tool" -> "Tool"; else -> "Error" }
        "$who:\n${it.text}" + if (it.extra.isNotBlank()) "\n${it.extra}" else ""
    }

    // ───── Riwayat chat: satu file per percakapan + index.json ─────
    private fun clearHist() { while (hist.length() > 0) hist.remove(hist.length() - 1) }
    private fun indexJson(): String = JSONArray(chats.map {
        JSONObject().put("id", it.id).put("t", it.title).put("u", it.updated).put("x", it.text)
    }).toString()
    private fun writeIndex() { val idx = indexJson(); writer.execute { runCatching { indexFile.writeText(idx) } } }

    private fun titleOf(): String {
        val u = msgs.firstOrNull { it.role == "user" }
        val t = u?.text?.trim()?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
        return (t ?: u?.extra?.lines()?.firstOrNull())?.take(40)?.ifBlank { null } ?: "Chat baru"
    }

    /** Simpan percakapan aktif (dipanggil tiap ada perubahan). Chat kosong tidak disimpan / dihapus dari riwayat. */
    private fun saveChat() {
        if (msgs.isEmpty()) { currentId?.let { deleteChat(it) }; return }
        val id = currentId ?: java.util.UUID.randomUUID().toString().also { currentId = it }
        val title = chats.firstOrNull { it.id == id }?.title ?: titleOf()
        val text = msgs.filter { it.role == "user" || it.role == "assistant" }.joinToString("\n") { it.text }.take(12000)
        chats.removeAll { it.id == id }
        chats.add(0, ChatMeta(id, title, System.currentTimeMillis(), text))
        val body = JSONObject()
            .put("msgs", JSONArray(msgs.map { JSONObject().put("r", it.role).put("t", it.text).put("x", it.extra) }))
            .put("hist", hist).toString()
        val idx = indexJson()
        writer.execute { runCatching { java.io.File(chatDir, "$id.json").writeText(body); indexFile.writeText(idx) } }
    }

    private fun applyChat(o: JSONObject) {
        msgs.clear(); clearHist(); attachments.clear()
        o.getJSONArray("msgs").let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { msgs.add(Msg(it.getString("r"), it.getString("t"), it.optString("x"))) } }
        o.getJSONArray("hist").let { a -> for (i in 0 until a.length()) hist.put(a.get(i)) }
    }

    fun newChat() { if (busy) return; msgs.clear(); clearHist(); attachments.clear(); currentId = null }

    fun openChat(id: String) {
        if (busy || id == currentId) return
        viewModelScope.launch {
            val o = withContext(Dispatchers.IO) { runCatching { JSONObject(java.io.File(chatDir, "$id.json").readText()) }.getOrNull() }
            if (o == null) { toast("Chat tidak ditemukan"); chats.removeAll { it.id == id }; writeIndex(); return@launch }
            applyChat(o); currentId = id
        }
    }

    fun deleteChat(id: String) {
        if (busy && id == currentId) return
        chats.removeAll { it.id == id }
        if (id == currentId) { msgs.clear(); clearHist(); currentId = null }
        val idx = indexJson()
        writer.execute { runCatching { java.io.File(chatDir, "$id.json").delete(); indexFile.writeText(idx) } }
    }

    fun renameChat(id: String, title: String) {
        val i = chats.indexOfFirst { it.id == id }
        if (i < 0 || title.isBlank()) return
        chats[i] = chats[i].copy(title = title.trim().take(80)); writeIndex()
    }

    private fun loadChats() {
        runCatching {
            val l = mutableListOf<ChatMeta>()
            if (indexFile.exists()) JSONArray(indexFile.readText()).let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { l.add(ChatMeta(it.getString("id"), it.getString("t"), it.getLong("u"), it.optString("x"))) }
            }
            chats.addAll(l.sortedByDescending { it.updated })
        }
        if (oldChatFile.exists()) {   // migrasi chat.json lama menjadi satu percakapan
            runCatching { applyChat(JSONObject(oldChatFile.readText())); saveChat(); oldChatFile.delete() }
        } else chats.firstOrNull()?.let { openChat(it.id) }   // buka chat terakhir
    }

    init {
        runCatching { if (crashFile.exists()) crash = crashFile.readText() }
        runCatching {
            val a = JSONArray(sp.getString("ws", "[]"))
            for (i in 0 until a.length()) workspaces.add(Workspace(a.getJSONObject(i).getString("n"), a.getJSONObject(i).getString("p")))
        }
        if (workspaces.isEmpty()) workspaces.add(Workspace("Default", "/storage/emulated/0/MiraiCode"))
        if (active !in workspaces.indices) active = 0
        loadChats()
    }

    fun save() = sp.edit {
        putString("baseUrl", baseUrl); putString("apiKey", apiKey); putString("model", model); putString("system", system)
        putInt("theme", theme); putBoolean("dynamic", dynamic); putBoolean("confirm", confirm)
        putString("mode", mode.name); putInt("active", active)
        putString("ws", JSONArray(workspaces.map { JSONObject().put("n", it.name).put("p", it.path) }).toString())
    }

    fun pickMode(m: Mode) { if (m == Mode.ROOT) rootWarn = true else { mode = m; save() } }
    fun confirmRoot() { rootWarn = false; mode = Mode.ROOT; save(); checkRoot() }
    /** Minta shell root (memunculkan dialog Magisk/KernelSU bila belum diizinkan). Shell lama yang non-root ditutup agar bisa dicek ulang. */
    fun checkRoot() {
        if (rootChecking) return
        rootChecking = true
        viewModelScope.launch(Dispatchers.IO) {
            rootOk = runCatching { Shell.getCachedShell()?.takeIf { !it.isRoot }?.close(); Shell.getShell().isRoot }.getOrDefault(false)
            rootChecking = false
        }
    }
    /** Petunjuk saja (tidak pasti): apakah binary su terlihat oleh app. */
    fun suBinaryVisible() = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sbin/su", "/vendor/bin/su", "/product/bin/su", "/debug_ramdisk/su")
        .any { java.io.File(it).exists() }

    private suspend fun ask(title: String, detail: String): Boolean {
        val p = Pending(title, detail); pending = p
        val r = p.result.await(); pending = null; return r
    }

    fun send(text: String) {
        val atts = attachments.toList()
        if (busy || (text.isBlank() && atts.isEmpty())) return
        attachments.clear()
        busy = true
        job = viewModelScope.launch {
            // File teks masuk ke prompt; gambar dikirim sebagai image_url (butuh model yang mendukung vision)
            val full = buildString {
                append(text)
                atts.filter { it.text != null }.forEach { append("\n\n[File: ${it.name}]\n```\n${it.text}\n```") }
            }.trim()
            val images = atts.filter { it.imageB64 != null }
            val content: Any = if (images.isEmpty()) full else JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", full.ifBlank { "Lihat gambar ini." }))
                images.forEach { put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${it.imageB64}"))) }
            }
            msgs.add(Msg("user", text, atts.joinToString("\n") { (if (it.imageB64 != null) "🖼 " else "📎 ") + it.name }))
            hist.put(JSONObject().put("role", "user").put("content", content))
            saveChat()   // percakapan langsung muncul di riwayat
            val base = hist.length(); var done = false
            try {
                for (step in 0 until 8) {
                    val all = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                    for (i in 0 until hist.length()) all.put(hist.get(i))
                    val m = runInterruptible(Dispatchers.IO) { Llm.chat(baseUrl, apiKey, model, all, Tools.schema) }
                    hist.put(m)
                    if (!m.isNull("content") && m.getString("content").isNotBlank()) msgs.add(Msg("assistant", m.getString("content")))
                    val calls = m.optJSONArray("tool_calls")
                    if (calls == null || calls.length() == 0) break
                    for (i in 0 until calls.length()) {
                        val c = calls.getJSONObject(i); val f = c.getJSONObject("function"); val name = f.getString("name")
                        val args = runCatching { JSONObject(f.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                        msgs.add(Msg("tool", "$name ${args.toString().take(300)}")); val ti = msgs.lastIndex
                        val risky = name == "shell" || name == "write_file"
                        val ok = if (risky && confirm) ask(name, args.toString(2).take(1500)) else true
                        toolName = name
                        val res = if (!ok) "Ditolak user." else runCatching {
                            Tools.call(name, args, mode, workspaces[active].path)
                        }.getOrElse { "Error: ${it.message}" }
                        toolName = null
                        msgs[ti] = msgs[ti].copy(extra = res.take(4000))   // hasil tool bisa dibuka di bubble
                        hist.put(JSONObject().put("role", "tool").put("tool_call_id", c.getString("id")).put("content", res))
                    }
                }
                done = true
            } catch (e: CancellationException) { msgs.add(Msg("error", "Dihentikan.")); throw e }
            catch (e: Exception) { msgs.add(Msg("error", e.message ?: e.toString())) }
            finally {
                // Gagal/dibatalkan di tengah loop tool -> riwayat API bisa menggantung (tool_calls tanpa hasil). Pangkas ke pesan user.
                if (!done) while (hist.length() > base) hist.remove(hist.length() - 1)
                busy = false; toolName = null; saveChat()
            }
        }
    }
}
