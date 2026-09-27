package com.glucarb

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime

/**
 * Offline crash capture. Glucarb has no INTERNET permission, so nothing is ever sent by
 * itself: the trace is written to a private file and, on the next launch, the user is
 * offered to share it through whatever app they choose.
 */
object CrashReporter {

    const val CONTACT_EMAIL = "overmeulen85@gmail.com"

    /** Enough for a deep Compose trace plus causes; keeps an email body sane. */
    private const val MAX_CHARS = 30_000

    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    /** Must run before anything that could crash, i.e. before Hilt's injection. */
    fun install(context: Context) {
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(format(error, thread.name, deviceInfo(app), OffsetDateTime.now().toString()))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? =
        file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun deviceInfo(context: Context): String {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("?")
        return "App: $version\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    fun format(error: Throwable, threadName: String, deviceInfo: String, time: String): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = "Glucarb crash report\n" +
            "Time: $time\n" +
            deviceInfo + "\n" +
            "Thread: $threadName\n\n" +
            trace
        return if (report.length <= MAX_CHARS) report else report.take(MAX_CHARS) + "\n[truncated]"
    }
}
