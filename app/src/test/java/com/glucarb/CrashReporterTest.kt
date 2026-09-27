package com.glucarb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class CrashReporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var saved: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() {
        saved = Thread.getDefaultUncaughtExceptionHandler()
        CrashReporter.clear(context)
    }

    @After fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(saved)
        CrashReporter.clear(context)
    }

    @Test fun crashIsSavedAndStillHandedToTheSystem() {
        var forwarded: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> forwarded = e }
        CrashReporter.install(context)

        val boom = IllegalStateException("boom", RuntimeException("root cause"))
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), boom)

        assertEquals(boom, forwarded)
        val report = CrashReporter.pending(context)!!
        assertTrue(report.contains("IllegalStateException: boom"))
        assertTrue(report.contains("Caused by: java.lang.RuntimeException: root cause"))
        assertTrue(report.contains("Android:"))
    }

    @Test fun clearRemovesTheReport() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        CrashReporter.install(context)
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), Error("x"))
        CrashReporter.clear(context)
        assertNull(CrashReporter.pending(context))
    }

    @Test fun hugeTracesAreTruncated() {
        val report = CrashReporter.format(RuntimeException("y".repeat(100_000)), "main", "info", "now")
        assertTrue(report.length < 31_000)
        assertTrue(report.endsWith("[truncated]"))
    }
}
