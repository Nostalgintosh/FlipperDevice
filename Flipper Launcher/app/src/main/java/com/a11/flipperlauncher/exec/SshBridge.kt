package com.a11.flipperlauncher.exec

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class WinResult(val stdout: String, val stderr: String, val exit: Int)

/**
 * Windows bridge — a minimal SSH client (mwiede JSch fork) that relays commands
 * to a remote host running OpenSSH Server (default shell PowerShell or cmd).
 *
 * Credentials live in memory only and are never persisted. Host keys, however,
 * ARE pinned TOFU-style (trust on first use): the first connection to a host
 * records its key in app-private `known_hosts`; a later connection whose key has
 * *changed* is refused — that mismatch is exactly the man-in-the-middle signal,
 * so it's the one case worth blocking even without an interactive prompt.
 */
class SshBridge(context: Context) {

    // Public host keys (not secrets) — safe to persist in app-private storage.
    private val knownHosts: File = File(context.filesDir, "known_hosts").apply {
        runCatching { if (!exists()) createNewFile() }
    }

    private var session: Session? = null
    var target: String? = null
        private set

    /** True when the most recent successful connect pinned a host we'd not seen. */
    var pinnedNewHost: Boolean = false
        private set

    val connected: Boolean get() = session?.isConnected == true

    suspend fun connect(user: String, host: String, port: Int, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            disconnect()
            try {
                val jsch = JSch().apply {
                    runCatching { setKnownHosts(knownHosts.absolutePath) }
                }
                val tofu = TofuUserInfo(password)
                val s = jsch.getSession(user, host, port)
                s.setPassword(password)
                s.userInfo = tofu
                // "ask" routes the unknown/changed-key decision through our
                // UserInfo: accept (and pin) a brand-new key, reject a changed one.
                s.setConfig("StrictHostKeyChecking", "ask")
                s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                s.connect(12_000)
                session = s
                target = "$user@$host:$port"
                pinnedNewHost = tofu.pinnedNew
                Result.success(Unit)
            } catch (e: Exception) {
                session = null; target = null
                Result.failure(e)
            }
        }

    suspend fun exec(command: String): Result<WinResult> = withContext(Dispatchers.IO) {
        val s = session
        if (s == null || !s.isConnected) return@withContext Result.failure(IllegalStateException("not connected"))
        try {
            val channel = s.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val errBuf = ByteArrayOutputStream()
            channel.setErrStream(errBuf)
            val input = channel.inputStream
            channel.connect()
            val stdout = input.bufferedReader().readText()
            while (!channel.isClosed) Thread.sleep(20)
            val exit = channel.exitStatus
            channel.disconnect()
            Result.success(WinResult(stdout, errBuf.toString(), exit))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect() {
        runCatching { session?.disconnect() }
        session = null; target = null
    }
}

/**
 * Drives JSch's host-key and auth prompts without any UI. The only yes/no JSch
 * asks is about host keys, so we accept (pin) an unknown host and refuse one
 * whose key has changed. The password answers password / keyboard-interactive.
 */
private class TofuUserInfo(private val password: String) : UserInfo, UIKeyboardInteractive {

    /** Set when we pinned a host we had not recorded before. */
    var pinnedNew: Boolean = false
        private set

    override fun getPassword(): String = password
    override fun getPassphrase(): String? = null
    override fun promptPassword(message: String?): Boolean = true
    override fun promptPassphrase(message: String?): Boolean = false

    override fun promptYesNo(message: String?): Boolean {
        val m = message?.lowercase().orEmpty()
        // A changed/mismatched key trips OpenSSH's "WARNING … HAS CHANGED" wording;
        // a first contact reads "authenticity … can't be established".
        val changed = "changed" in m || "differs" in m || "warning" in m
        if (!changed) pinnedNew = true
        return !changed
    }

    override fun showMessage(message: String?) { /* no surface to show it on */ }

    override fun promptKeyboardInteractive(
        destination: String?,
        name: String?,
        instruction: String?,
        prompt: Array<out String>?,
        echo: BooleanArray?,
    ): Array<String>? =
        // Answer a single hidden challenge with the password; bail on anything else.
        if (prompt?.size == 1 && echo?.firstOrNull() == false) arrayOf(password) else null
}
