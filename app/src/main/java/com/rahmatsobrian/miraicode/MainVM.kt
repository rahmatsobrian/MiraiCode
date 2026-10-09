package com.rahmatsobrian.miraicode

import android.app.Application
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Workspace(val name: String, val path: String)
data class Msg(val role: String, val text: String)
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

    init {
        runCatching {
            val a = JSONArray(sp.getString("ws", "[]"))
            for (i in 0 until a.length()) workspaces.add(Workspace(a.getJSONObject(i).getString("n"), a.getJSONObject(i).getString("p")))
        }
        if (workspaces.isEmpty()) workspaces.add(Workspace("Default", "/storage/emulated/0/MiraiCode"))
        if (active !in workspaces.indices) active = 0
    }

    fun save() = sp.edit {
        putString("baseUrl", baseUrl); putString("apiKey", apiKey); putString("model", model); putString("system", system)
        putInt("theme", theme); putBoolean("dynamic", dynamic); putBoolean("confirm", confirm)
        putString("mode", mode.name); putInt("active", active)
        putString("ws", JSONArray(workspaces.map { JSONObject().put("n", it.name).put("p", it.path) }).toString())
    }

    fun pickMode(m: Mode) { if (m == Mode.ROOT) rootWarn = true else { mode = m; save() } }
    fun confirmRoot() { rootWarn = false; mode = Mode.ROOT; save(); checkRoot() }
    fun checkRoot() { viewModelScope.launch(Dispatchers.IO) { rootOk = runCatching { Shell.getShell().isRoot }.getOrDefault(false) } }
    fun clearChat() { msgs.clear(); while (hist.length() > 0) hist.remove(0) }

    private suspend fun ask(title: String, detail: String): Boolean {
        val p = Pending(title, detail); pending = p
        val r = p.result.await(); pending = null; return r
    }

    fun send(text: String) {
        if (busy || text.isBlank()) return
        viewModelScope.launch {
            busy = true
            msgs.add(Msg("user", text)); hist.put(JSONObject().put("role", "user").put("content", text))
            try {
                for (step in 0 until 8) {
                    val all = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                    for (i in 0 until hist.length()) all.put(hist.get(i))
                    val m = withContext(Dispatchers.IO) { Llm.chat(baseUrl, apiKey, model, all, Tools.schema) }
                    hist.put(m)
                    if (!m.isNull("content") && m.getString("content").isNotBlank()) msgs.add(Msg("assistant", m.getString("content")))
                    val calls = m.optJSONArray("tool_calls")
                    if (calls == null || calls.length() == 0) break
                    for (i in 0 until calls.length()) {
                        val c = calls.getJSONObject(i); val f = c.getJSONObject("function"); val name = f.getString("name")
                        val args = runCatching { JSONObject(f.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                        msgs.add(Msg("tool", "$name ${args.toString().take(300)}"))
                        val risky = name == "shell" || name == "write_file"
                        val ok = if (risky && confirm) ask(name, args.toString(2).take(1500)) else true
                        val res = if (!ok) "Ditolak user." else runCatching {
                            Tools.call(name, args, mode, workspaces[active].path)
                        }.getOrElse { "Error: ${it.message}" }
                        hist.put(JSONObject().put("role", "tool").put("tool_call_id", c.getString("id")).put("content", res))
                    }
                }
            } catch (e: Exception) { msgs.add(Msg("error", e.message ?: e.toString())) }
            finally { busy = false }
        }
    }
}
