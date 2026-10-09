package com.rahmatsobrian.miraicode

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class Mode { NONROOT, SHIZUKU, ROOT }

fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

object Exec {
    private const val PKG = "com.rahmatsobrian.miraicode"
    @Volatile private var svc: IShellService? = null
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) { svc = IShellService.Stub.asInterface(b) }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    private suspend fun shizuku(): IShellService {
        svc?.let { return it }
        val args = Shizuku.UserServiceArgs(ComponentName(PKG, ShellService::class.java.name))
            .daemon(false).processNameSuffix("shell").version(1)
        Shizuku.bindUserService(args, conn)
        repeat(60) { delay(100); svc?.let { return it } }
        error("Shizuku service tidak terhubung (cek Shizuku aktif & izin).")
    }

    suspend fun run(mode: Mode, cmd: String, cwd: String?): String = withContext(Dispatchers.IO) {
        val full = if (cwd.isNullOrBlank()) cmd
        else "mkdir -p ${q(cwd)} 2>/dev/null; cd ${q(cwd)} && {\n$cmd\n}"
        val out = when (mode) {
            Mode.NONROOT -> ProcessBuilder("sh", "-c", full).redirectErrorStream(true).start().let { p ->
                val t = p.inputStream.bufferedReader().readText(); p.waitFor(); t
            }
            Mode.ROOT -> Shell.cmd(full).exec().let { (it.out + it.err).joinToString("\n") }
            Mode.SHIZUKU -> shizuku().exec(full)
        }
        out.take(20000).ifBlank { "(tidak ada output)" }
    }
}
