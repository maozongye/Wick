package dev.wick.features.items.miniapps

import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.utils.TargetProcess

object SkipSplash : SwitchFeature(), IResolveDex {

    override val technicalId = "跳过启动页面"
    override val nameRes = R.string.feature_skip_splash_name
    override val categoryIds = listOf(FeatureCategoryIds.MINIAPPS)
    override val descriptionRes = R.string.feature_skip_splash_description

    private val methodShowSplash by dexMethod {
        searchPackages("com.tencent.mm.plugin.appbrand")
        matcher {
            declaredClass = "com.tencent.mm.plugin.appbrand.AppBrandRuntime"
            returnType = "void"
            paramCount = 0
            usingEqStrings(
                "public:prepare",
                "Loading页展示",
                "MicroMsg.AppBrandRuntime",
                "showSplash[AppBrandSplashAd], appId:%s, splash:%s"
            )
        }
    }

    override val targetProcesses = setOf(TargetProcess.MAIN, TargetProcess.APPBRAND)

    override fun onEnable() {
        methodShowSplash.hookBefore { result = null }
    }
}
