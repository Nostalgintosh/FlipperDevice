package com.a11.flipperlauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Workspace for the Code Editor.
 *
 * Files want to be real files on disk that **Termux** can reach, so `▶ run` / `lx`
 * can compile them. On Android 11+ an app's own `Android/data/…` dir is sandboxed
 * away from other apps, so the workspace lives in shared storage at
 * `/sdcard/FlipperCode` whenever we hold storage access ([hasSharedAccess]).
 *
 * Without that grant we fall back to the app-private external dir: the editor
 * still works standalone, but Termux can't see the files to build them.
 */
class CodeRepository(private val context: Context) {

    /** Shared, Termux-reachable workspace — usable only with storage access. */
    private val sharedDir: File = File(Environment.getExternalStorageDirectory(), WORKSPACE)

    /** App-private fallback — always writable, but invisible to Termux on 11+. */
    private val privateDir: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "code")

    /** True when we can read/write the shared workspace (so Termux can too). */
    fun hasSharedAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    /** Active workspace dir, resolved each call so a mid-session grant takes effect. */
    val dir: File
        get() = (if (hasSharedAccess()) sharedDir else privateDir).apply { runCatching { mkdirs() } }

    val dirPath: String get() = dir.absolutePath

    /** True once the workspace is the shared, Termux-reachable location. */
    val reachableByTermux: Boolean get() = hasSharedAccess()

    fun absolutePath(name: String): String = File(dir, sanitize(name)).absolutePath

    fun list(): List<String> =
        dir.listFiles()?.filter { it.isFile }?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()

    fun exists(name: String): Boolean = File(dir, sanitize(name)).exists()

    suspend fun read(name: String): String = withContext(Dispatchers.IO) {
        runCatching { File(dir, sanitize(name)).readText() }.getOrDefault("")
    }

    suspend fun write(name: String, content: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(dir, sanitize(name)).writeText(content); true }.getOrDefault(false)
    }

    suspend fun create(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(dir, sanitize(name))
            if (f.exists()) true else f.createNewFile()
        }.getOrDefault(false)
    }

    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(dir, sanitize(name)).delete() }.getOrDefault(false)
    }

    /**
     * Copy any files left in the private fallback into the now-reachable shared
     * dir (skipping names that already exist there). Called once access is granted
     * so work done before the grant isn't stranded. Returns the count moved.
     */
    suspend fun migrateToShared(): Int = withContext(Dispatchers.IO) {
        if (!hasSharedAccess()) return@withContext 0
        runCatching { sharedDir.mkdirs() }
        val pending = privateDir.listFiles()?.filter { it.isFile } ?: return@withContext 0
        var moved = 0
        for (f in pending) {
            val dest = File(sharedDir, f.name)
            if (dest.exists()) continue
            runCatching { f.copyTo(dest, overwrite = false); moved++ }
        }
        moved
    }

    /** Strip any path components so writes stay inside the workspace. */
    fun sanitize(name: String): String =
        name.trim().substringAfterLast('/').substringAfterLast('\\').ifBlank { "untitled.txt" }

    private companion object {
        const val WORKSPACE = "FlipperCode"
    }
}
