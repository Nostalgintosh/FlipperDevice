package com.a11.flipperlauncher.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A point-in-time readout of device vitals shown in the status bar. */
data class SystemStats(
    /** Internal storage used, 0–100 %. */
    val storageUsedPct: Int = 0,
    /** RAM used, 0–100 %. */
    val ramUsedPct: Int = 0,
    /** CPU busy %, or -1 when the kernel hides /proc/stat (Android 8+). */
    val cpuPct: Int = -1,
    val cpuCores: Int = 0,
    /** Current cpu0 frequency in MHz, or 0 if unreadable. */
    val cpuMhz: Int = 0,
    /** Active transport: "wifi" / "cell" / "eth" / "off". */
    val netLabel: String = "off",
    /** True when the active network has validated internet. */
    val online: Boolean = false,
)

/**
 * Reads device vitals (storage, RAM, CPU, network) with no runtime permissions —
 * only the install-time ACCESS_NETWORK_STATE. Each field is best-effort: anything
 * the platform blocks (notably system-wide CPU on Android 8+) degrades gracefully.
 */
class SystemMonitor(private val context: Context) {

    // Previous (idle, total) jiffies, kept to compute the CPU delta between samples.
    private var lastCpu: Pair<Long, Long>? = null

    suspend fun sample(): SystemStats = withContext(Dispatchers.IO) {
        val (label, online) = netState()
        SystemStats(
            storageUsedPct = storagePct(),
            ramUsedPct = ramPct(),
            cpuPct = cpuPct(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuMhz = cpuMhz(),
            netLabel = label,
            online = online,
        )
    }

    private fun storagePct(): Int = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        if (total <= 0L) 0 else (((total - free) * 100L) / total).toInt()
    }.getOrDefault(0)

    private fun ramPct(): Int = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        if (mi.totalMem <= 0L) 0 else (((mi.totalMem - mi.availMem) * 100L) / mi.totalMem).toInt()
    }.getOrDefault(0)

    private fun cpuPct(): Int = runCatching {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() }
        if (line == null || !line.startsWith("cpu ")) return@runCatching -1
        val v = line.trim().split(Regex("\\s+")).drop(1).map { it.toLongOrNull() ?: 0L }
        if (v.size < 4) return@runCatching -1
        val idle = v[3] + v.getOrElse(4) { 0L }      // idle + iowait
        val total = v.sum()
        val prev = lastCpu
        lastCpu = idle to total
        if (prev == null) return@runCatching -1       // first sample: no delta yet
        val dIdle = idle - prev.first
        val dTotal = total - prev.second
        if (dTotal <= 0L) -1 else (((dTotal - dIdle) * 100L) / dTotal).toInt().coerceIn(0, 100)
    }.getOrDefault(-1)

    private fun cpuMhz(): Int = runCatching {
        val khz = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").readText().trim().toLong()
        (khz / 1000L).toInt()
    }.getOrDefault(0)

    private fun netState(): Pair<String, Boolean> = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return@runCatching "off" to false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching "off" to false
        val label = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "net"
        }
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        label to online
    }.getOrDefault("off" to false)
}
