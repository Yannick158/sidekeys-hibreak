package com.sidekeys.hibreak.service

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Result of a shell command run through Shizuku. */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Runs shell commands with ADB (shell) privileges via Shizuku — no root required.
 * Whether a given command actually succeeds depends on what the `shell` user is
 * allowed to do on the specific device (SELinux / file permissions).
 */
object ShizukuShell {

    /** Shizuku is installed and its service is running and reachable. */
    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isPermissionGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun shouldShowRationale(): Boolean = runCatching {
        Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    /**
     * Executes `sh -c "$command"` with shell privileges.
     * Uses the hidden [Shizuku.newProcess] via reflection (the standard approach
     * used by Shizuku-based apps), wrapped defensively.
     */
    fun run(command: String): ShellResult {
        if (!isAvailable()) return ShellResult(-1, "", "Shizuku not available")
        if (!isPermissionGranted()) return ShellResult(-1, "", "Shizuku permission not granted")
        return runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null,
            ) as Process
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            ShellResult(code, stdout.trim(), stderr.trim())
        }.getOrElse { ShellResult(-1, "", it.message ?: "exec failed") }
    }
}
