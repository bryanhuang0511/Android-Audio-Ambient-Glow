package com.example.audioambientglow.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {
    private const val TAG = "CrashHandler"
    private const val PREFS_NAME = "glow_crash_prefs"
    private const val KEY_LAST_CRASH = "key_last_crash_report"
    private const val CRASH_FILE_NAME = "crash_log.txt"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "CrashHandler initialized successfully.")
    }

    fun recordException(tag: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        } ?: "No stack trace provided."

        val logEntry = "[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}] [$tag] $message\n$stackTrace\n"
        Log.e(TAG, logEntry)
        saveCrashReport(logEntry)
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val report = buildString {
            appendLine("=== 手機音效氣氛燈 崩潰日誌報告 ===")
            appendLine("時間: $timeStr")
            appendLine("設備: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("系統版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("執行緒: ${thread.name} (ID: ${thread.id})")
            appendLine("例外類型: ${throwable.javaClass.name}")
            appendLine("例外訊息: ${throwable.message}")
            appendLine("堆疊追蹤 (Stack Trace):")
            appendLine(stackTrace)
            appendLine("=================================")
        }

        saveCrashReport(report)
    }

    private fun saveCrashReport(report: String) {
        val ctx = appContext ?: return
        try {
            // 1. Save to SharedPreferences
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH, report)
                .apply()

            // 2. Save to internal file
            val file = File(ctx.filesDir, CRASH_FILE_NAME)
            file.writeText(report)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    fun getLatestCrashLog(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LAST_CRASH, null)
        if (!saved.isNullOrEmpty()) return saved

        val file = File(context.filesDir, CRASH_FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clearCrashLog(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .apply()

        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }
}
