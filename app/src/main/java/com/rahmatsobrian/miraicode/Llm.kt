package com.rahmatsobrian.miraicode

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TreeMap
import java.util.UUID

/**
 * Provider yang didukung. [kind] 0 = OpenAI-compatible (/chat/completions), 1 = Anthropic (/messages).
 * Semua provider selain Claude memakai format OpenAI; Custom bisa memilih salah satu format.
 */
enum class Provider(val label: String, val sub: String, val kind: Int, val url: String, val model: String) {
    OPENAI("OpenAI", "Chat Completions", 0, "https://api.openai.com/v1", "gpt-4o-mini"),
    CLAUDE("Claude (Anthropic)", "Messages API", 1, "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
    GEMINI("Google Gemini", "Endpoint OpenAI-compatible", 0, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
    OPENROUTER("OpenRouter", "Ratusan model, satu API", 0, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    GROQ("Groq", "Inferensi cepat", 0, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    DEEPSEEK("DeepSeek", "OpenAI-compatible", 0, "https://api.deepseek.com/v1", "deepseek-chat"),
    OLLAMA("Ollama (lokal)", "Server di jaringan/perangkat sendiri", 0, "http://localhost:11434/v1", "llama3.2"),
    CUSTOM("Custom", "Endpoint & format API sendiri", 0, "", "")
}

/** Konfigurasi satu panggilan. Nilai NaN / 0 = "default provider" (tidak dikirim). */
class LlmCfg(
    val kind: Int, val baseUrl: String, val key: String, val model: String,
    val temperature: Float, val topP: Float, val maxTokens: Int,
    val freq: Float, val pres: Float, val reasoning: Int,
    val stream: Boolean, val timeoutSec: Int, val retries: Int
)

object Llm {
    private val RETRY = setOf(429, 500, 502, 503, 504, 529)
    @Volatile private var conn: HttpURLConnection? = null
    /** Putuskan koneksi yang sedang berjalan (tombol Berhenti). */
    fun abort() { runCatching { conn?.disconnect() } }

    private fun r2(f: Float) = Math.round(f * 100) / 100.0

    private fun open(url: String, cfg: LlmCfg, method: String, body: ByteArray?, sse: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        conn = c
        c.requestMethod = method
        c.connectTimeout = 30000; c.readTimeout = cfg.timeoutSec.coerceAtLeast(10) * 1000
        c.setRequestProperty("Content-Type", "application/json")
        if (sse) c.setRequestProperty("Accept", "text/event-stream")
        if (cfg.kind == 1) {
            if (cfg.key.isNotBlank()) c.setRequestProperty("x-api-key", cfg.key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
        } else if (cfg.key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer ${cfg.key}")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body) } }
        return c
    }

    // ───────── Bangun body ─────────
    private fun openAiBody(cfg: LlmCfg, system: String, hist: JSONArray, tools: JSONArray): JSONObject {
        val all = JSONArray()
        if (system.isNotBlank()) all.put(JSONObject().put("role", "system").put("content", system))
        for (i in 0 until hist.length()) all.put(hist.get(i))
        val b = JSONObject().put("model", cfg.model).put("messages", all)
        if (tools.length() > 0) b.put("tools", tools)
        if (!cfg.temperature.isNaN()) b.put("temperature", r2(cfg.temperature))
        if (!cfg.topP.isNaN()) b.put("top_p", r2(cfg.topP))
        if (!cfg.freq.isNaN()) b.put("frequency_penalty", r2(cfg.freq))
        if (!cfg.pres.isNaN()) b.put("presence_penalty", r2(cfg.pres))
        if (cfg.maxTokens > 0) b.put(if (cfg.baseUrl.contains("api.openai.com")) "max_completion_tokens" else "max_tokens", cfg.maxTokens)
        if (cfg.reasoning in 1..3) b.put("reasoning_effort", listOf("low", "medium", "high")[cfg.reasoning - 1])
        if (cfg.stream) b.put("stream", true)
        return b
    }

    private fun anthropicBody(cfg: LlmCfg, system: String, hist: JSONArray, tools: JSONArray): JSONObject {
        val b = JSONObject().put("model", cfg.model)
            .put("max_tokens", if (cfg.maxTokens > 0) cfg.maxTokens else 4096)
            .put("messages", toAnthropicMessages(hist))
        if (system.isNotBlank()) b.put("system", system)
        if (tools.length() > 0) b.put("tools", JSONArray().also { a ->
            for (i in 0 until tools.length()) tools.getJSONObject(i).getJSONObject("function").let {
                a.put(JSONObject().put("name", it.getString("name")).put("description", it.optString("description"))
                    .put("input_schema", it.getJSONObject("parameters")))
            }
        })
        if (!cfg.temperature.isNaN()) b.put("temperature", r2(cfg.temperature.coerceIn(0f, 1f)))
        if (!cfg.topP.isNaN()) b.put("top_p", r2(cfg.topP))
        if (cfg.stream) b.put("stream", true)
        return b
    }

    /** Riwayat disimpan dalam format OpenAI; ubah ke format Messages Anthropic saat kirim. */
    private fun toAnthropicMessages(hist: JSONArray): JSONArray {
        val out = JSONArray()
        var i = 0
        while (i < hist.length()) {
            val m = hist.getJSONObject(i)
            when (m.optString("role")) {
                "user" -> {
                    val c = m.opt("content")
                    if (c is JSONArray) {
                        val blocks = JSONArray()
                        for (k in 0 until c.length()) {
                            val p = c.getJSONObject(k)
                            if (p.optString("type") == "text") blocks.put(JSONObject().put("type", "text").put("text", p.optString("text")))
                            else if (p.optString("type") == "image_url") {
                                val u = p.getJSONObject("image_url").optString("url")
                                val data = u.substringAfter("base64,", "")
                                if (data.isNotEmpty()) blocks.put(JSONObject().put("type", "image").put("source",
                                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", data)))
                            }
                        }
                        out.put(JSONObject().put("role", "user").put("content", blocks))
                    } else out.put(JSONObject().put("role", "user").put("content", c?.toString().orEmpty().ifBlank { " " }))
                }
                "assistant" -> {
                    val blocks = JSONArray()
                    val t = if (m.isNull("content")) "" else m.optString("content")
                    if (t.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", t))
                    m.optJSONArray("tool_calls")?.let { tc ->
                        for (k in 0 until tc.length()) {
                            val c = tc.getJSONObject(k); val f = c.getJSONObject("function")
                            val input = runCatching { JSONObject(f.optString("arguments", "{}").ifBlank { "{}" }) }.getOrDefault(JSONObject())
                            blocks.put(JSONObject().put("type", "tool_use").put("id", c.getString("id")).put("name", f.getString("name")).put("input", input))
                        }
                    }
                    if (blocks.length() > 0) out.put(JSONObject().put("role", "assistant").put("content", blocks))
                }
                "tool" -> {
                    // Hasil tool berurutan digabung jadi satu pesan user berisi banyak tool_result
                    val blocks = JSONArray()
                    while (i < hist.length() && hist.getJSONObject(i).optString("role") == "tool") {
                        val tm = hist.getJSONObject(i)
                        blocks.put(JSONObject().put("type", "tool_result").put("tool_use_id", tm.getString("tool_call_id"))
                            .put("content", tm.optString("content").ifBlank { "(kosong)" }))
                        i++
                    }
                    out.put(JSONObject().put("role", "user").put("content", blocks))
                    continue
                }
            }
            i++
        }
        return out
    }

    // ───────── Panggilan utama ─────────
    /**
     * Kirim [hist] (format OpenAI) dan kembalikan pesan assistant dalam format OpenAI
     * (content + tool_calls). Bila [LlmCfg.stream], [onText] dipanggil berulang dengan teks KUMULATIF.
     */
    fun chat(cfg: LlmCfg, system: String, hist: JSONArray, tools: JSONArray, onText: (String) -> Unit): JSONObject {
        val body = (if (cfg.kind == 1) anthropicBody(cfg, system, hist, tools) else openAiBody(cfg, system, hist, tools)).toString().toByteArray()
        val url = cfg.baseUrl.trimEnd('/') + if (cfg.kind == 1) "/messages" else "/chat/completions"
        var lastErr = ""
        // Rate limit (429) / error server sementara: coba lagi otomatis dengan backoff
        for (attempt in 0..cfg.retries) {
            val c = open(url, cfg, "POST", body, cfg.stream)
            val code = c.responseCode
            if (code < 400) return if (cfg.stream) readStream(c, cfg.kind, onText) else readJson(c, cfg.kind)
            val txt = c.errorStream?.bufferedReader()?.readText().orEmpty()
            lastErr = "HTTP $code: ${txt.take(600)}"
            if (code !in RETRY || attempt == cfg.retries) break
            val wait = c.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000) ?: (2000L shl attempt.coerceAtMost(4))
            c.disconnect()
            Thread.sleep(wait.coerceIn(1000L, 15000L))
        }
        error(lastErr)
    }

    private fun readJson(c: HttpURLConnection, kind: Int): JSONObject {
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        if (kind == 0) return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val text = StringBuilder(); val calls = JSONArray()
        val blocks = j.optJSONArray("content") ?: JSONArray()
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            when (b.optString("type")) {
                "text" -> text.append(b.optString("text"))
                "tool_use" -> calls.put(JSONObject().put("id", b.getString("id")).put("type", "function")
                    .put("function", JSONObject().put("name", b.getString("name")).put("arguments", b.optJSONObject("input")?.toString() ?: "{}")))
            }
        }
        return JSONObject().put("role", "assistant").put("content", text.toString()).also { if (calls.length() > 0) it.put("tool_calls", calls) }
    }

    private class Acc(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

    /** Baca SSE (OpenAI: choices[].delta, Anthropic: content_block_*), rakit jadi pesan assistant format OpenAI. */
    private fun readStream(c: HttpURLConnection, kind: Int, onText: (String) -> Unit): JSONObject {
        val text = StringBuilder()
        val calls = TreeMap<Int, Acc>()
        var last = 0L; var dirty = false
        fun emit(force: Boolean) {
            val now = System.currentTimeMillis()
            if (dirty && (force || now - last >= 60)) { onText(text.toString()); last = now; dirty = false }
        }
        var aIdx = 0
        c.inputStream.bufferedReader().use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val d = line.substring(5).trim()
                if (d.isEmpty()) continue
                if (d == "[DONE]") break
                val j = runCatching { JSONObject(d) }.getOrNull() ?: continue
                if (kind == 0) {
                    j.optJSONObject("error")?.let { error(it.optString("message", it.toString())) }
                    val delta = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                    if (!delta.isNull("content")) { val s = delta.optString("content"); if (s.isNotEmpty()) { text.append(s); dirty = true } }
                    delta.optJSONArray("tool_calls")?.let { tcs ->
                        for (k in 0 until tcs.length()) {
                            val tc = tcs.getJSONObject(k)
                            val idx = if (tc.has("index")) tc.getInt("index") else calls.size
                            val a = calls.getOrPut(idx) { Acc() }
                            if (!tc.isNull("id") && tc.optString("id").isNotEmpty()) a.id = tc.optString("id")
                            tc.optJSONObject("function")?.let { f ->
                                if (!f.isNull("name") && a.name.isEmpty()) a.name = f.optString("name")
                                if (!f.isNull("arguments")) a.args.append(f.optString("arguments"))
                            }
                        }
                    }
                } else when (j.optString("type")) {
                    "content_block_start" -> {
                        aIdx = j.optInt("index", aIdx)
                        val cb = j.optJSONObject("content_block")
                        if (cb?.optString("type") == "tool_use") calls[aIdx] = Acc(cb.optString("id"), cb.optString("name"))
                    }
                    "content_block_delta" -> {
                        val idx = j.optInt("index", aIdx)
                        val dl = j.optJSONObject("delta") ?: continue
                        when (dl.optString("type")) {
                            "text_delta" -> { text.append(dl.optString("text")); dirty = true }
                            "input_json_delta" -> calls[idx]?.args?.append(dl.optString("partial_json"))
                        }
                    }
                    "error" -> error(j.optJSONObject("error")?.optString("message") ?: d)
                }
                emit(false)
            }
        }
        dirty = true; emit(true)
        val m = JSONObject().put("role", "assistant").put("content", text.toString())
        if (calls.isNotEmpty()) m.put("tool_calls", JSONArray(calls.values.map { a ->
            JSONObject().put("id", a.id.ifBlank { "call_" + UUID.randomUUID().toString().take(12) }).put("type", "function")
                .put("function", JSONObject().put("name", a.name).put("arguments", a.args.toString().ifBlank { "{}" }))
        }))
        return m
    }

    /** Ambil daftar model dari provider ({base}/models). Sekaligus berfungsi sebagai tes koneksi. */
    fun models(cfg: LlmCfg): List<String> {
        val url = cfg.baseUrl.trimEnd('/') + "/models" + if (cfg.kind == 1) "?limit=1000" else ""
        val c = open(url, cfg, "GET", null, false)
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code >= 400) error("HTTP $code: ${txt.take(300)}")
        val arr = runCatching { JSONObject(txt).optJSONArray("data") ?: JSONObject(txt).optJSONArray("models") }.getOrNull()
            ?: runCatching { JSONArray(txt) }.getOrNull() ?: error("Format respons tidak dikenali")
        return (0 until arr.length()).mapNotNull {
            val o = arr.opt(it)
            (if (o is JSONObject) o.optString("id").ifBlank { o.optString("name") } else o?.toString())?.removePrefix("models/")?.ifBlank { null }
        }.sorted()
    }
}
