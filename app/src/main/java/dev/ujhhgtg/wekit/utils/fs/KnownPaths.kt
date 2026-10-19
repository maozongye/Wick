package dev.ujhhgtg.wekit.utils.fs

import android.os.Environment
import android.util.Log
import dev.ujhhgtg.wekit.BuildConfig
import dev.ujhhgtg.wekit.utils.BuildFingerprint
import dev.ujhhgtg.wekit.utils.HostInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div

object KnownPaths {

    /**
     * Root for all WeKit-owned files that need a real filesystem path.
     *
     * Keep the directory names below this root stable: script loaders and extension runtimes
     * use their relative layout as part of their contract.
     *
     * The root itself lives under `noBackupFilesDir` with a derived name: `filesDir/wekit`
     * used to sit inside the host's own scanned storage area, which made it a one-string
     * fingerprint.
     */
    val moduleRoot: Path by lazy {
        val modern = HostInfo.application.noBackupFilesDir.asPath / BuildFingerprint.dir
        relocateLegacyRoot(modern)
        modern.createDirsSafe()
    }

    /**
     * Forces the one-shot relocation eagerly. Call this once from the unified entry point,
     * before any database handle or writer thread exists: moving the tree while SQLite has an
     * open file (or a WAL beside it) would split the database from its sidecars.
     */
    fun ensureRootReady() {
        moduleRoot.toFile().exists()
    }

    /**
     * Moves a pre-hardening installation's data to [modern] once. Same filesystem, so this is
     * an atomic rename; the copy fallback only covers exotic layouts. Best effort by design —
     * a failed relocation must not block module startup.
     */
    private fun relocateLegacyRoot(modern: Path) {
        val legacy = HostInfo.application.filesDir.asPath / "wekit"
        if (!Files.isDirectory(legacy) || Files.exists(modern)) return
        runCatching {
            modern.parent?.let { Files.createDirectories(it) }
            Files.move(legacy, modern)
        }.recoverCatching {
            // A concurrent process may have completed the move between the checks above.
            if (Files.exists(modern)) return@recoverCatching modern
            legacy.toFile().copyRecursively(modern.toFile(), overwrite = false)
            legacy.toFile().deleteRecursively()
            modern
        }.onFailure {
            Log.e(BuildConfig.INTERNAL_TAG, "legacy module root relocation failed", it)
        }
    }

    /**
     * A Dex cache is tied to both the WeChat build and its distribution channel.  Domestic and
     * Google Play builds can share a version name/code while exposing different DEX layouts, so
     * they must never address the same cache partition.
     */
    fun hostVersionKey(): String {
        val channel = if (HostInfo.isHostGooglePlay) "play" else "domestic"
        return sanitizeVersionKey("${HostInfo.versionName}-${HostInfo.versionCode}-$channel")
    }

    private fun sanitizeVersionKey(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    val internalStorage: Path by lazy {
        Environment.getExternalStorageDirectory().asPath
    }

    val codeCacheDir: Path by lazy {
        HostInfo.application.codeCacheDir.asPath
    }

    val moduleCache by lazy {
        (moduleRoot / "cache").createDirsSafe()
    }

    val moduleAssets by lazy {
        (moduleRoot / "assets").createDirsSafe()
    }

    val userAssets by lazy {
        (moduleAssets / "user").createDirsSafe()
    }

    val downloads by lazy {
        // Stay inside the public Downloads tree (users expect to find exported media there)
        // but derive the directory name, so the folder is not a one-string fingerprint.
        (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toPath() / BuildFingerprint.label)
            .createDirsSafe()
    }
}
