package com.rahmatsobrian.miraicode

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Tools {
    private fun s(d: String) = JSONObject().put("type", "string").put("description", d)
    private fun fn(n: String, d: String, props: JSONObject, req: List<String>) =
        JSONObject().put("type", "function").put("function", JSONObject()
            .put("name", n).put("description", d)
            .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(req))))

    val schema = JSONArray()
        .put(fn("shell", "Jalankan perintah shell (sh) di workspace.", JSONObject().put("command", s("perintah")), listOf("command")))
        .put(fn("read_file", "Baca file teks.", JSONObject().put("path", s("path absolut/relatif workspace")), listOf("path")))
        .put(fn("write_file", "Tulis (timpa) file teks.", JSONObject().put("path", s("path")).put("content", s("isi")), listOf("path", "content")))
        .put(fn("list_dir", "List isi folder.", JSONObject().put("path", s("path")), listOf("path")))
        .put(fn("http_get", "HTTP GET sebuah URL.", JSONObject().put("url", s("url")), listOf("url")))

    private fun path(ws: String, p: String) = if (p.startsWith("/")) p else "$ws/$p"

    suspend fun call(name: String, a: JSONObject, mode: Mode, ws: String): String = when (name) {
        "shell" -> Exec.run(mode, a.getString("command"), ws)
        "read_file" -> Exec.run(mode, "cat ${q(path(ws, a.getString("path")))}", null)
        "list_dir" -> Exec.run(mode, "ls -la ${q(path(ws, a.optString("path", ".")))}", null)
        "write_file" -> {
            val p = path(ws, a.getString("path")); val t = a.getString("content")
            if (mode == Mode.NONROOT) withContext(Dispatchers.IO) {
                File(p).apply { parentFile?.mkdirs(); writeText(t) }; "OK ${t.length} karakter"
            } else {
                val b = Base64.encodeToString(t.toByteArray(), Base64.NO_WRAP)
                Exec.run(mode, "mkdir -p ${q(File(p).parent ?: "/")} && echo '$b' | base64 -d > ${q(p)} && echo OK", null)
            }
        }
        "http_get" -> withContext(Dispatchers.IO) {
            val c = URL(a.getString("url")).openConnection() as HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 30000
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
            "HTTP $code\n" + body.take(20000)
        }
        else -> "Tool tidak dikenal: $name"
    }
}
