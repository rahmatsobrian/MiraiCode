package com.rahmatsobrian.miraicode

/** Berjalan di proses Shizuku (uid shell/adb). */
class ShellService : IShellService.Stub() {
    override fun destroy() { System.exit(0) }
    override fun exec(cmd: String): String {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }
}
