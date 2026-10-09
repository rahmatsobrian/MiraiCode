package com.rahmatsobrian.miraicode

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Klien untuk endpoint OpenAI-compatible: {baseUrl}/chat/completions */
object Llm {
    private val RETRY = setOf(429, 500, 502, 503, 504)

    fun chat(baseUrl: String, key: String, model: String, messages: JSONArray, tools: JSONArray): JSONObject {
        val body = JSONObject().put("model", model).put("messages", messages).put("tools", tools).toString().toByteArray()
        var lastErr = ""
        // Rate limit (429) / error server sementara: coba lagi otomatis dengan backoff
        for (attempt in 0 until 4) {
            val c = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 30000; c.readTimeout = 180000
            c.setRequestProperty("Content-Type", "application/json")
            if (key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $key")
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code < 400) return JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            lastErr = "HTTP $code: ${txt.take(600)}"
            if (code !in RETRY || attempt == 3) break
            val wait = c.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000) ?: (2000L shl attempt)
            c.disconnect()
            Thread.sleep(wait.coerceIn(1000L, 15000L))
        }
        error(lastErr)
    }
}
