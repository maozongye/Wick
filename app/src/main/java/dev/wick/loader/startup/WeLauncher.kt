package dev.wick.loader.startup

import android.content.Context
import dev.wick.constants.PackageNames
import dev.wick.dexkit.cache.DexCacheManager
import dev.wick.features.core.FeaturesLoader
import dev.wick.i18n.WeKitLocaleController
import dev.wick.loader.utils.ActivityProxy
import dev.wick.loader.utils.ParcelableFixer
import dev.wick.loader.utils.ResourcesInjector
import dev.wick.utils.HostInfo
import dev.wick.utils.RuntimeConfig
import dev.wick.utils.TargetProcesses
import dev.wick.utils.WeLogger
import dev.wick.utils.fs.KnownPaths

object WeLauncher {

    fun init(context: Context) {
        WeLogger.d(TAG, "loading in process name=${TargetProcesses.currentName}, type=${TargetProcesses.currentType}")

        ParcelableFixer.init()

        // Relocate the module root before anything opens a database or a log writer: the
        // migration renames the tree, which must not race an open SQLite handle.
        KnownPaths.ensureRootReady()

        DexCacheManager.init(
            KnownPaths.hostVersionKey()
        )

        val appContext = context.applicationContext ?: context
        ResourcesInjector.injectModuleRes(appContext.resources)
        WeKitLocaleController.initializeInjectedHost(HostInfo.application)

        if (TargetProcesses.isInMain) {
            ActivityProxy.init(appContext)

            val prefs =
                context.getSharedPreferences("${PackageNames.WECHAT}_preferences", Context.MODE_PRIVATE)
            RuntimeConfig.mmPrefs = prefs
        }

        runCatching {
            FeaturesLoader.loadFeatures()
        }.onFailure { WeLogger.e(TAG, "failed to load features", it) }
    }

    private const val TAG = "WeLauncher"
}
