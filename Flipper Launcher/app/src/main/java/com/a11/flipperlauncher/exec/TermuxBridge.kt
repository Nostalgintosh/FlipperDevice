package com.a11.flipperlauncher.exec

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Linux bridge — relays commands to the Termux environment's RunCommandService
 * (`com.termux.RUN_COMMAND`). Output comes back asynchronously via a result
 * PendingIntent broadcast to [RESULT_ACTION].
 *
 * Requirements on the device:
 *  - Termux installed (F-Droid build; Play-store Termux is deprecated).
 *  - `allow-external-apps=true` in ~/.termux/termux.properties.
 */
class TermuxBridge(private val context: Context) {

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PKG, 0); true
    }.getOrDefault(false)

    fun run(command: String, background: Boolean = true): Result<Unit> = try {
        // Token Termux fills with stdout/stderr/exit, then broadcasts back to us.
        val resultBase = Intent(RESULT_ACTION).setPackage(context.packageName)
        val piFlags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0) or
            PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, seq.incrementAndGet(), resultBase, piFlags)

        val intent = Intent().apply {
            setClassName(TERMUX_PKG, "$TERMUX_PKG.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "$PREFIX/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        }
        // RunCommandService promotes itself to a foreground service.
        ContextCompat.startForegroundService(context, intent)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    @SuppressLint("SdCardPath")
    companion object {
        const val TERMUX_PKG = "com.termux"
        private const val PREFIX = "/data/data/com.termux/files/usr"
        private const val HOME = "/data/data/com.termux/files/home"

        const val RESULT_ACTION = "com.a11.flipperlauncher.TERMUX_RESULT"
        const val RESULT_BUNDLE = "result"
        const val KEY_STDOUT = "stdout"
        const val KEY_STDERR = "stderr"
        const val KEY_EXIT = "exitCode"
        const val KEY_ERR = "err"
        const val KEY_ERRMSG = "errmsg"

        private val seq = AtomicInteger(7000)
    }
}
