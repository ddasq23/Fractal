// SPDX-License-Identifier: GPL-3.0-only
//
// Based on WinlatorHUD and CPUStatus from Winlator-Ludashi by StevenMXZ
// (https://github.com/StevenMXZ/Winlator-Ludashi), licensed under the GPL-3.0.
// Ported to Kotlin and adapted to Fractal: the sysfs nodes are probed once and only the ones that answered are read,
// and the statistics nobody shows are not read at all. GPU usage comes first from Fractal's own command buffer
// timestamps (any driver, no sysfs needed); the sysfs nodes are the fallback. Winlator's Adreno memstore fallback
// (KgslLoadFallback, native gpuusage library) is not part of this port.

package dev.dfd.fractal.emulation.hud

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import dev.dfd.fractal.common.gpu.GpuInfo
import dev.dfd.fractal.common.settings.HudItem
import dev.dfd.fractal.nativeinterface.NativeEmulation
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reads the device statistics the HUD shows. Used from the HUD's stats thread only. Every reading is -1 when the
 * device doesn't expose it to apps.
 */
internal class HudSensors(context: Context) {
    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private val buffer = ByteArray(1024)

    /** Readings, by [HudItem] ordinal; [HudItem.CHARGING] is 1 or 0. */
    fun sample(mask: Int, out: IntArray) {
        val wantsGpu = mask and HudItem.GPU_USAGE.bit != 0
        setEmulatorGpuLoad(wantsGpu)
        if (wantsGpu) out[HudItem.GPU_USAGE.ordinal] = readGpu()
        if (mask and HudItem.CPU_USAGE.bit != 0) out[HudItem.CPU_USAGE.ordinal] = readCpuUsage()
        if (mask and HudItem.CPU_TEMP.bit != 0) out[HudItem.CPU_TEMP.ordinal] = readCpuTemp()
        if (mask and HudItem.RAM.bit != 0) out[HudItem.RAM.ordinal] = readRam()
        val batteryBits = HudItem.POWER.bit or HudItem.BATTERY_LEVEL.bit or HudItem.BATTERY_TEMP.bit or HudItem.CHARGING.bit
        if (mask and batteryBits != 0) readBattery(out)
        val wantsEmulator = mask and HudItem.EMULATOR_MASK != 0
        setEmulatorStats(wantsEmulator)
        if (wantsEmulator) readEmulator(mask, out)
    }

    /** Turns off what the emulator measures for the HUD, while nobody looks at it. */
    fun stopEmulatorReadings() {
        setEmulatorGpuLoad(false)
        setEmulatorStats(false)
    }

    /** "Adreno 740", "Mali-G57"; empty when unknown. Asks the GPU once (a tiny EGL context). */
    val gpuName: String by lazy { runCatching { GpuInfo.current.displayName }.getOrDefault("").trim() }

    // ---- GPU usage ----

    private var gpuPaths: Array<String>? = null
    private var gpuEverRead = false
    private var gpuFailures = 0
    private var maliGpuMs = -1L
    private var maliWallMs = 0L

    private var emulatorGpuLoadOn = false

    /** The emulator's own GPU timestamps: they work on any driver, also where the system hides the load files. */
    fun setEmulatorGpuLoad(on: Boolean) {
        if (on == emulatorGpuLoadOn) return
        emulatorGpuLoadOn = on
        runCatching { NativeEmulation.setGpuLoadWanted(on) }
    }

    private fun readGpu(): Int {
        val emulator = runCatching { NativeEmulation.getGpuLoadPercent() }.getOrDefault(-1)
        if (emulator >= 0) return emulator
        var paths = gpuPaths ?: detectGpuPaths().also { gpuPaths = it }
        for (path in paths) {
            val value = readGpuSample(path)
            if (value >= 0) {
                gpuEverRead = true
                gpuFailures = 0
                return value
            }
            // the system took the node away (or never really let it be read): stop trying it
            if (value == UNREADABLE) {
                paths = paths.filter { it != path }.toTypedArray()
                gpuPaths = paths
            }
        }
        // nodes that open but never give a number
        if (!gpuEverRead && paths.isNotEmpty() && ++gpuFailures >= GPU_GIVE_UP_SAMPLES) gpuPaths = emptyArray()
        return -1
    }

    private fun detectGpuPaths(): Array<String> {
        val found = LinkedHashSet<String>()
        for (path in GPU_STATIC_PATHS) if (canRead(path)) found += path
        scanGpuNodes(File("/sys/class/devfreq"), byPath = true, found)
        scanGpuNodes(File("/sys/devices/virtual/devfreq"), byPath = true, found)
        scanGpuNodes(File("/sys/devices/platform"), byPath = false, found)
        // SELinux lets apps see far more nodes than it lets them read: keep the ones that can be read now
        return found.filter { readText(it) != null }.toTypedArray()
    }

    private fun scanGpuNodes(root: File, byPath: Boolean, found: MutableSet<String>) {
        val nodes = runCatching { root.listFiles() }.getOrNull() ?: return
        for (node in nodes) {
            val key = (if (byPath) node.path else node.name).lowercase(Locale.US)
            if (GPU_NODE_TOKENS.none { key.contains(it) }) continue
            for (name in GPU_USAGE_FILES) {
                val candidate = File(node, name)
                if (candidate.canRead()) found += candidate.path
            }
        }
    }

    /** The load in percent, -1 when this reading gave none, [UNREADABLE] when the node can't be read. */
    private fun readGpuSample(path: String): Int = try {
        val line = readLine(path)
        when {
            line == null -> UNREADABLE

            path.endsWith("/gpubusy") -> {
                // "busy total" in kgsl ticks; no ticks at all means the GPU was idle
                val parts = line.trim().split(WHITESPACE)
                if (parts.size < 2) return -1
                val busy = parts[0].toLong()
                val total = parts[1].toLong()
                if (total > 0) clampPercent((busy * 100 / total).toInt()) else 0
            }

            path.endsWith("/gpuinfo") -> {
                // Mali: running GPU time in ms; the load is its growth over the wall clock
                val gpuMs = line.trim().split(WHITESPACE).last().toLong()
                val nowMs = SystemClock.elapsedRealtime()
                val oldGpuMs = maliGpuMs
                val oldWallMs = maliWallMs
                maliGpuMs = gpuMs
                maliWallMs = nowMs
                if (oldGpuMs < 0 || oldWallMs <= 0) return -1
                val wall = nowMs - oldWallMs
                if (wall > 0) clampPercent(((gpuMs - oldGpuMs).coerceAtLeast(0) * 100 / wall).toInt()) else -1
            }

            path == "/proc/mtk_mali/utilization" -> {
                val text = readText(path) ?: return UNREADABLE
                val at = text.indexOf("ACTIVE=")
                if (at < 0) return -1
                val digits = text.substring(at + 7).takeWhile { it != ' ' && it != '\n' }.filter { it.isDigit() }
                if (digits.isEmpty()) -1 else clampPercent(digits.toInt())
            }

            else -> parsePercent(line)
        }
    } catch (_: Exception) {
        -1
    }

    private fun parsePercent(line: String): Int {
        for (token in line.trim().split(WHITESPACE)) {
            val digits = token.filter { it.isDigit() }
            if (digits.isNotEmpty()) return digits.toIntOrNull()?.let(::clampPercent) ?: -1
        }
        return -1
    }

    // ---- the emulator's statistics (those of Cemu's overlay box) ----

    private var emulatorStatsOn = false
    private val emulatorStats = FloatArray(EMULATOR_STAT_COUNT)

    private fun setEmulatorStats(on: Boolean) {
        if (on == emulatorStatsOn) return
        emulatorStatsOn = on
        runCatching { NativeEmulation.setHudStatsWanted(on) }
    }

    private fun readEmulator(mask: Int, out: IntArray) {
        if (runCatching { NativeEmulation.getHudStats(emulatorStats) }.isFailure) {
            for (item in HudItem.entries) if (item.bit and HudItem.EMULATOR_MASK != 0) out[item.ordinal] = -1
            return
        }
        val stats = emulatorStats
        val fps = stats[STAT_FPS]
        if (mask and HudItem.FRAME_TIME.bit != 0) {
            // tenths of a millisecond
            out[HudItem.FRAME_TIME.ordinal] = if (fps > 0.5f) (10_000f / fps).roundToInt() else -1
        }
        if (mask and HudItem.FRAME_GEN.bit != 0) {
            // 0 while frame generation is off: no text then
            out[HudItem.FRAME_GEN.ordinal] = if (stats[STAT_FRAME_GEN_ACTIVE] > 0f) {
                stats[STAT_PRESENTED_FPS].roundToInt().coerceAtLeast(1)
            } else 0
        }
        if (mask and HudItem.DRAW_CALLS.bit != 0) {
            out[HudItem.DRAW_CALLS.ordinal] = packDrawCalls(stats[STAT_DRAW_CALLS].toInt(), stats[STAT_FAST_DRAW_CALLS].toInt())
        }
        if (mask and HudItem.EMU_CPU.bit != 0) {
            out[HudItem.EMU_CPU.ordinal] = stats[STAT_CPU].roundToInt().coerceIn(0, 100)
        }
        if (mask and HudItem.EMU_RAM.bit != 0) {
            val mb = stats[STAT_RAM_MB].toInt()
            out[HudItem.EMU_RAM.ordinal] = if (mb > 0) mb else -1
        }
    }

    // ---- CPU usage and temperature ----

    private var procStatReadable: Boolean? = null
    private var previousCpuTotal = -1L
    private var previousCpuIdle = -1L
    private var cpuMaxFreqs: IntArray? = null
    private var cpuTempPaths: Array<String>? = null

    /** From /proc/stat when the system lets apps read it (it doesn't since Android 8), else from the clock speeds. */
    private fun readCpuUsage(): Int {
        if (procStatReadable != false) {
            val usage = readProcStatUsage()
            if (procStatReadable == null) procStatReadable = usage != PROC_STAT_FAILED
            if (usage != PROC_STAT_FAILED) return usage
        }
        return readClockLoad()
    }

    private fun readProcStatUsage(): Int {
        val line = readLine("/proc/stat") ?: return PROC_STAT_FAILED
        val parts = line.trim().split(WHITESPACE)
        if (parts.size < 5 || parts[0] != "cpu") return PROC_STAT_FAILED
        var total = 0L
        var idle = 0L
        for (i in 1 until parts.size) {
            val value = parts[i].toLongOrNull() ?: return PROC_STAT_FAILED
            total += value
            if (i == 4 || i == 5) idle += value
        }
        val oldTotal = previousCpuTotal
        val oldIdle = previousCpuIdle
        previousCpuTotal = total
        previousCpuIdle = idle
        if (oldTotal < 0) return -1
        val deltaTotal = total - oldTotal
        val deltaIdle = (idle - oldIdle).coerceAtLeast(0)
        return if (deltaTotal > 0) clampPercent(((deltaTotal - deltaIdle) * 100 / deltaTotal).toInt()) else -1
    }

    /** Sum of the cores' current clocks over the sum of their maximums. */
    private fun readClockLoad(): Int {
        val maxFreqs = cpuMaxFreqs ?: IntArray(Runtime.getRuntime().availableProcessors()) { core ->
            readLong("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq").toInt()
        }.also { cpuMaxFreqs = it }
        var current = 0L
        var maximum = 0L
        for (core in maxFreqs.indices) {
            val max = maxFreqs[core]
            if (max <= 0) continue
            val cur = readLong("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
            if (cur > 0) {
                current += cur
                maximum += max
            }
        }
        return if (maximum > 0) clampPercent((current * 100 / maximum).toInt()) else -1
    }

    private fun readCpuTemp(): Int {
        val paths = cpuTempPaths ?: discoverCpuTempPaths().also { cpuTempPaths = it }
        for (path in paths) {
            val raw = readLong(path)
            if (raw <= 0) continue
            // millidegrees on most kernels, degrees on some
            val celsius = if (raw > 1000) ((raw + 500) / 1000).toInt() else raw.toInt()
            if (celsius in 1..150) return celsius
        }
        return -1
    }

    private fun discoverCpuTempPaths(): Array<String> {
        val found = ArrayList<Pair<Int, String>>()
        for (root in listOf(File("/sys/class/thermal"), File("/sys/devices/virtual/thermal"))) {
            val zones = runCatching { root.listFiles { _, name -> name.startsWith("thermal_zone") } }.getOrNull() ?: continue
            for (zone in zones) {
                val type = readLine(File(zone, "type").path)?.trim()?.lowercase(Locale.US) ?: continue
                val rank = rankCpuZone(type)
                if (rank < 0) continue
                val temp = File(zone, "temp")
                if (!temp.canRead()) continue
                val path = temp.absolutePath
                if (found.none { it.second == path }) found += rank to path
            }
        }
        return found.sortedWith(compareBy({ it.first }, { it.second })).map { it.second }.toTypedArray()
    }

    private fun rankCpuZone(type: String): Int = when {
        type.contains("gpu") -> -1
        type.contains("cpu-silicon") -> 0
        type.contains("cpu-0") -> 1
        type.contains("cpuss") || type.contains("mtktscpu") || type.contains("cpu") -> 2
        type.contains("s5p-tmu") -> 3
        type.contains("soc") -> 4
        type.contains("cputop") -> 5
        type.contains("tsens") -> 6
        type.contains("cluster") -> 7
        type.contains("big") || type.contains("little") -> 8
        else -> -1
    }

    // ---- RAM ----

    private fun readRam(): Int {
        val text = readText("/proc/meminfo") ?: return -1
        val total = meminfoKb(text, "MemTotal:")
        val available = meminfoKb(text, "MemAvailable:")
        return if (total > 0 && available >= 0) clampPercent((100 * (total - available) / total).toInt()) else -1
    }

    private fun meminfoKb(text: String, key: String): Long {
        val at = text.indexOf(key)
        if (at < 0) return -1
        var i = at + key.length
        while (i < text.length && text[i] == ' ') i++
        var value = 0L
        var digits = 0
        while (i < text.length && text[i].isDigit()) {
            value = value * 10 + (text[i] - '0')
            i++
            digits++
        }
        return if (digits > 0) value else -1
    }

    // ---- Battery ----

    private var batteryIntent: Intent? = null
    private var batteryReadAtMs = 0L
    private var batteryFailed = false

    private fun readBattery(out: IntArray) {
        if (batteryFailed) {
            out[HudItem.POWER.ordinal] = -1
            out[HudItem.BATTERY_LEVEL.ordinal] = -1
            out[HudItem.BATTERY_TEMP.ordinal] = -1
            out[HudItem.CHARGING.ordinal] = 0
            return
        }
        try {
            // the sticky broadcast is a call into the system: every few seconds is enough for temperature and level
            val now = SystemClock.elapsedRealtime()
            if (batteryIntent == null || now - batteryReadAtMs >= BATTERY_INTENT_INTERVAL_MS) {
                batteryIntent = appContext.registerReceiver(null, batteryFilter)
                batteryReadAtMs = now
            }
            val battery = batteryIntent ?: return

            val rawTemp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            out[HudItem.BATTERY_TEMP.ordinal] = if (rawTemp > 0) (rawTemp / 10f).roundToInt() else -1

            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            out[HudItem.BATTERY_LEVEL.ordinal] = if (level >= 0 && scale > 0) clampPercent(level * 100 / scale) else -1

            val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            out[HudItem.CHARGING.ordinal] = if (charging) 1 else 0

            out[HudItem.POWER.ordinal] = readPowerMilliwatts(battery)
        } catch (_: Exception) {
            batteryFailed = true
        }
    }

    private fun readPowerMilliwatts(battery: Intent): Int {
        var voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        if (voltageMv <= 0) {
            val voltageUv = readFirstLong(VOLTAGE_CHANNELS)
            if (voltageUv != 0L) voltageMv = (abs(voltageUv) / 1000).toInt()
        }
        val amps = batteryCurrentAmps()
        var watts = if (amps > 0f && voltageMv > 0) voltageMv / 1000f * amps else -1f
        if (watts <= 0f) {
            val powerUw = readFirstLong(POWER_CHANNELS)
            if (powerUw != 0L) watts = abs(powerUw) / 1_000_000f
        }
        return if (watts > 0f) (watts * 1000f).roundToInt() else -1
    }

    private fun batteryCurrentAmps(): Float {
        var raw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        if (raw == 0L || raw == Long.MIN_VALUE) raw = readFirstLong(CURRENT_CHANNELS)
        if (raw == 0L || raw == Long.MIN_VALUE) return -1f
        val magnitude = abs(raw)
        // microamperes on most devices, milliamperes on some
        return if (magnitude < 20_000L) magnitude / 1000f else magnitude / 1_000_000f
    }

    // ---- file reading ----

    private fun canRead(path: String) = runCatching { File(path).canRead() }.getOrDefault(false)

    /** The start of a small sysfs or proc file, in the reused buffer; null when it can't be read. */
    private fun readText(path: String): String? = try {
        FileInputStream(path).use { input ->
            val count = input.read(buffer)
            if (count > 0) String(buffer, 0, count, Charsets.US_ASCII) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun readLine(path: String): String? = readText(path)?.let { text ->
        val end = text.indexOf('\n')
        if (end >= 0) text.substring(0, end) else text
    }

    /** A file holding one number; 0 when it can't be read. */
    private fun readLong(path: String): Long = try {
        FileInputStream(path).use { input ->
            val count = input.read(buffer)
            parseLong(buffer, count)
        }
    } catch (_: Exception) {
        0L
    }

    private fun readFirstLong(paths: Array<String>): Long {
        for (path in paths) {
            val value = readLong(path)
            if (value != 0L && value != Long.MIN_VALUE) return value
        }
        return 0L
    }

    private companion object {
        const val PROC_STAT_FAILED = Int.MIN_VALUE
        const val UNREADABLE = -2
        const val GPU_GIVE_UP_SAMPLES = 5
        const val BATTERY_INTENT_INTERVAL_MS = 5_000L
        val WHITESPACE = Regex("\\s+")

        val GPU_STATIC_PATHS = arrayOf(
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/class/misc/mali0/device/utilisation",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/class/misc/mali0/device/gpuinfo",
            "/sys/devices/platform/mali/utilization",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/devices/platform/gpusysfs/gpu_busy",
            "/sys/class/misc/pvrsrvkm/device/utilisation",
            "/sys/class/pvr/utilisation",
            "/sys/class/pvr/gpu_utilisation",
            "/sys/class/drm/card0/device/gpu_busy_percent",
            "/sys/class/devfreq/gpu/load",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
            "/proc/mtk_mali/utilization",
        )

        val GPU_USAGE_FILES = arrayOf(
            "gpu_busy_percentage", "gpu_busy_percent", "gpu_load", "utilisation",
            "utilization", "load", "gpu_busy", "gpuinfo",
        )

        val GPU_NODE_TOKENS = arrayOf("gpu", "mali", "g3d", "kgsl", "panfrost", "pvr", "powervr", "xclipse", "sgpu")

        val CURRENT_CHANNELS = arrayOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/main/current_now",
        )

        val VOLTAGE_CHANNELS = arrayOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/bms/voltage_now",
            "/sys/class/power_supply/main/voltage_now",
        )

        val POWER_CHANNELS = arrayOf(
            "/sys/class/power_supply/battery/power_now",
            "/sys/class/power_supply/bms/power_now",
            "/sys/class/power_supply/main/power_now",
        )

        fun clampPercent(value: Int) = value.coerceIn(0, 100)

        // order of NativeEmulation.getHudStats
        const val STAT_FPS = 0
        const val STAT_PRESENTED_FPS = 1
        const val STAT_FRAME_GEN_ACTIVE = 3
        const val STAT_DRAW_CALLS = 4
        const val STAT_FAST_DRAW_CALLS = 5
        const val STAT_CPU = 6
        const val STAT_RAM_MB = 7
        const val EMULATOR_STAT_COUNT = 10

        /** The first (optionally negative) integer in [count] bytes of [bytes]; 0 when there is none. */
        fun parseLong(bytes: ByteArray, count: Int): Long {
            var i = 0
            while (i < count && (bytes[i] == ' '.code.toByte() || bytes[i] == '\t'.code.toByte())) i++
            var negative = false
            if (i < count && bytes[i] == '-'.code.toByte()) {
                negative = true
                i++
            }
            var value = 0L
            var digits = 0
            while (i < count) {
                val d = bytes[i] - '0'.code.toByte()
                if (d !in 0..9) break
                value = value * 10 + d
                i++
                digits++
            }
            if (digits == 0) return 0L
            return if (negative) -value else value
        }
    }
}

/** Draw calls and fast draw calls per frame as one reading (the draws in the upper half), so it changes as one. */
internal fun packDrawCalls(draws: Int, fast: Int): Int = (draws.coerceIn(0, 0x7FFF) shl 16) or fast.coerceIn(0, 0xFFFF)

internal fun drawCallsOf(packed: Int): Int = packed ushr 16

internal fun fastDrawCallsOf(packed: Int): Int = packed and 0xFFFF
