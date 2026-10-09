package com.rahmatsobrian.miraicode

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Klien untuk endpoint OpenAI-compatible: {baseUrl}/chat/completions */
object Llm {
    fun chat(baseUrl: String, key: String, model: String, messages: JSONArray, tools: JSONArray): JSONObject {
        val c = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 30000; c.readTimeout = 180000
        c.setRequestProperty("Content-Type", "application/json")
        if (key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $key")
        val body = JSONObject().put("model", model).put("messages", messages).put("tools", tools)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code >= 400) error("HTTP $code: ${txt.take(600)}")
        return JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
    }
}
