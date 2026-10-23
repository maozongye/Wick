package dev.wick.features.items.system

import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature

object NerfBackgroundProcessChecker : SwitchFeature(), IResolveDex {

    override val technicalId = "禁用微信进程状态检测器"
    override val nameRes = R.string.feature_nerf_background_process_checker_name
    override val categoryIds = listOf(FeatureCategoryIds.SYSTEM_PRIVACY)
    override val descriptionRes = R.string.feature_nerf_background_process_checker_description

    private val methodPerformProcessCheck by dexMethod {
        matcher {
            usingEqStrings("MicroMsg.AbstractProcessChecker", "pass this check,because request is null! ????")
        }
    }

    override fun onEnable() {
        methodPerformProcessCheck.hookBefore {
            result = null
        }
    }
}
