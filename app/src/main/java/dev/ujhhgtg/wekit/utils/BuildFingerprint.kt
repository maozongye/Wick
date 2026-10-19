package dev.ujhhgtg.wekit.utils

import dev.ujhhgtg.wekit.BuildConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Every identifier that could be used to recognise this module at runtime is derived here.
 *
 * Nothing outside this file may hard-code a module-specific name for on-disk or in-memory
 * artefacts: two builds must not share a constant that a scanner could match. The derivation
 * mirrors `deriveFingerprint()` in `app/build.gradle.kts`; keep both in sync.
 *
 * The nonce itself is per-build, so a leaked or decompiled binary reveals an algorithm, not a
 * fingerprint.
 */
object BuildFingerprint {

    private fun derive(tag: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.BUILD_NONCE.toByteArray(), "HmacSHA256"))
        return mac.doFinal(tag.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(8)
    }

    /** Root directory name under the host's `noBackupFilesDir`. */
    val dir: String = ".${derive("root")}"

    /** Room database file name. */
    val dbName: String = "${derive("db")}.db"

    /** Prefix for rotated log files. */
    val logPrefix: String = derive("log")

    /** Abstract unix socket name for the local tool bridge. */
    val socketName: String = derive("sock")

    /** memfd name used when remapping module libraries out of `/proc/self/maps`. */
    val memfdName: String = derive("memfd")

    /** Neutral label for process argv, client identifiers and other externally visible names. */
    val label: String = derive("label")

    /**
     * Intent extra key used by the settings entry handshake. Extras travel through Binder and
     * are readable by the host, so this must not carry the product name.
     */
    val intentKey: String = "x-" + derive("intent")

    /** Worker thread names; shaped like a generic pool rather than a module-specific one. */
    fun threadName(purpose: String): String = "pool-${derive(purpose)}-thread-1"
}
