package dev.wick.constants

import dev.wick.BuildConfig

object PackageNames {

    const val WECHAT = "com.tencent.mm"

    /**
     * Installed identity, which may carry a per-build suffix (`…wekit.x841c7a09`).
     * Use this when comparing against a runtime package name.
     */
    const val MODULE = BuildConfig.APPLICATION_ID

    /**
     * Source package prefix that every module class name starts with.
     *
     * Distinct from [MODULE]: `applicationId` can be suffixed per build while compiled class names
     * always stay under the fixed namespace. Comparing a class name against [MODULE] therefore
     * fails on every suffixed build, which silently breaks component checks such as deciding
     * whether an activity belongs to this module.
     */
    const val MODULE_CLASS_PREFIX = "dev.wick"

    @Suppress("NOTHING_TO_INLINE")
    @JvmStatic
    inline fun isWeChat(packageName: String) = packageName.startsWith(WECHAT)

    /** True when [className] is defined by this module (not the host, not the framework). */
    @Suppress("NOTHING_TO_INLINE")
    @JvmStatic
    inline fun isModuleClass(className: String?) =
        className != null && className.startsWith(MODULE_CLASS_PREFIX)
}
