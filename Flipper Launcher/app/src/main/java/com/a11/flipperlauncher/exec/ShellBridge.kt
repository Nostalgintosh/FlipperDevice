package com.a11.flipperlauncher.exec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native Android shell — runs in the launcher's own process via `sh -c`, so
 * pipes / globs / redirects work. User-space only: no root, so privileged
 * commands (tcpdump, hardware overrides) return "permission denied".
 */
class ShellBridge {

    /** Streams merged stdout+stderr line-by-line via [onLine]; returns the exit code. */
    suspend fun run(command: String, onLine: suspend (String) -> Unit): Int = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    onLine(line)
                    line = reader.readLine()
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            onLine("error: ${e.message}")
            -1
        }
    }
}
