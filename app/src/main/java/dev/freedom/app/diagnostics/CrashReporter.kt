package dev.freedom.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlin.system.exitProcess

object CrashReporter {
    private const val DIRECTORY = "diagnostics"
    private const val CRASH_FILE = "last_crash.txt"
    private const val HANDLED_ERROR_FILE = "last_handled_error.txt"
    private const val BREADCRUMB_FILE = "breadcrumbs.log"
    private const val PREFERENCES = "freedom.diagnostics.v1"
    private const val LAST_SEEN = "last_seen_crash"
    private const val MAX_BREADCRUMB_CHARS = 16_000
    private const val MAX_STACK_CHARS = 48_000

    private val lock = Any()

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(lock) {
            if (installed) return
            val appContext = context.applicationContext
            record(appContext, "application_process_started")

            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                writeCrash(appContext, thread, error)
                if (previous != null) {
                    previous.uncaughtException(thread, error)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
            installed = true
        }
    }

    fun record(context: Context, event: String) {
        val safeEvent = event
            .replace(Regex("[^a-zA-Z0-9_.:-]"), "_")
            .take(120)
        synchronized(lock) {
            runCatching {
                val file = breadcrumbFile(context)
                file.parentFile?.mkdirs()
                file.appendText("${Instant.now()} $safeEvent\n")
                trimBreadcrumbs(file)
            }
        }
    }

    fun lastCrash(context: Context): String? = synchronized(lock) {
        runCatching {
            crashFile(context).takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    fun recordHandledError(context: Context, stage: String, error: Throwable) {
        val safeStage = stage.replace(Regex("[^a-zA-Z0-9_.:-]"), "_").take(120)
        synchronized(lock) {
            runCatching {
                val report = buildString {
                    appendLine("Freedom handled error report")
                    appendLine("captured_at=${Instant.now()}")
                    appendLine(deviceSummary(context))
                    appendLine("stage=$safeStage")
                    appendLine()
                    appendLine("Safe breadcrumbs:")
                    appendLine(readBreadcrumbs(context))
                    appendLine("Exception:")
                    append(safeStack(error))
                }
                val file = handledErrorFile(context)
                file.parentFile?.mkdirs()
                file.writeText(report)
            }
        }
    }

    fun reportForSharing(context: Context): String =
        latestSavedReport(context) ?: buildString {
            appendLine("Freedom diagnostic report")
            appendLine("No uncaught Java/Kotlin crash has been captured in this installation.")
            appendLine(deviceSummary(context))
            appendLine()
            appendLine("Recent safe breadcrumbs:")
            append(readBreadcrumbs(context))
        }

    private fun latestSavedReport(context: Context): String? = synchronized(lock) {
        listOf(crashFile(context), handledErrorFile(context))
            .filter(File::isFile)
            .maxByOrNull(File::lastModified)
            ?.let { runCatching { it.readText().takeIf(String::isNotBlank) }.getOrNull() }
    }

    fun hasUnseenCrash(context: Context): Boolean {
        val crashTimestamp = crashFile(context).takeIf(File::isFile)?.lastModified() ?: return false
        val lastSeen = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(LAST_SEEN, 0L)
        return crashTimestamp > lastSeen
    }

    fun markCrashSeen(context: Context) {
        val timestamp = crashFile(context).takeIf(File::isFile)?.lastModified() ?: return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_SEEN, timestamp)
            .apply()
    }

    private fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        synchronized(lock) {
            runCatching {
                val report = buildString {
                    appendLine("Freedom crash report")
                    appendLine("captured_at=${Instant.now()}")
                    appendLine(deviceSummary(context))
                    appendLine("thread=${thread.name}")
                    appendLine()
                    appendLine("Safe breadcrumbs (no keys, QR values, messages, contact names or numbers):")
                    appendLine(readBreadcrumbs(context))
                    appendLine("Exception:")
                    append(safeStack(error))
                }
                val file = crashFile(context)
                file.parentFile?.mkdirs()
                file.writeText(report)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun deviceSummary(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        return "app=${packageInfo.versionName}($versionCode) " +
            "android=${Build.VERSION.RELEASE}(sdk=${Build.VERSION.SDK_INT}) " +
            "device=${Build.MANUFACTURER}/${Build.MODEL}"
    }

    private fun readBreadcrumbs(context: Context): String =
        runCatching { breadcrumbFile(context).readText().takeLast(MAX_BREADCRUMB_CHARS) }
            .getOrDefault("breadcrumbs_unavailable\n")

    private fun lastHandledError(context: Context): String? = synchronized(lock) {
        runCatching {
            handledErrorFile(context).takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun safeStack(error: Throwable): String =
        StringWriter().also { writer ->
            error.printStackTrace(PrintWriter(writer))
        }.toString().take(MAX_STACK_CHARS)

    private fun trimBreadcrumbs(file: File) {
        if (file.length() <= MAX_BREADCRUMB_CHARS) return
        file.writeText(file.readText().takeLast(MAX_BREADCRUMB_CHARS))
    }

    private fun diagnosticsDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY)

    private fun crashFile(context: Context): File =
        File(diagnosticsDirectory(context), CRASH_FILE)

    private fun handledErrorFile(context: Context): File =
        File(diagnosticsDirectory(context), HANDLED_ERROR_FILE)

    private fun breadcrumbFile(context: Context): File =
        File(diagnosticsDirectory(context), BREADCRUMB_FILE)
}
