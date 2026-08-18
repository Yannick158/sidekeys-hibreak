package com.sidekeys.hibreak

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records uncaught exceptions to a local file so the next launch can show what
 * went wrong — this app is used on devices without a PC/adb at hand, so a
 * crash must be diagnosable from the phone itself. Nothing is sent anywhere.
 */
class SideKeysApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                crashFile(this).writeText(
                    "Thread: ${thread.name}\nApp: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android: ${android.os.Build.VERSION.RELEASE} / ${android.os.Build.MODEL}\n\n$sw",
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun crashFile(context: Context): File = File(context.filesDir, "last_crash.txt")

        fun readCrash(context: Context): String? =
            crashFile(context).takeIf { it.exists() }?.readText()

        fun clearCrash(context: Context) {
            crashFile(context).delete()
        }
    }
}
