package dev.wick.features.items.system

import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.data
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature

object DisableWebViewSafetyWarnings : SwitchFeature(), IResolveDex {

    override val technicalId = "禁用 WebView 安全警告"
    override val nameRes = R.string.feature_disable_web_view_safety_warnings_name
    override val categoryIds = listOf(FeatureCategoryIds.SYSTEM_PRIVACY)
    override val descriptionRes = R.string.feature_disable_web_view_safety_warnings_description

    private val methodGetIsInterceptEnabled by dexMethod {
        matcher {
            usingEqStrings(
                "MicroMsg.WebViewHighRiskAdH5Interceptor",
                "isInterceptEnabled, expt="
            )
        }
    }
    private val methodGetIsUrlSafe by dexMethod {
        matcher {
            declaredClass(methodGetIsInterceptEnabled.data.declaredClassName)
            usingEqStrings("http", "https")
        }
    }

    override fun onEnable() {
        methodGetIsInterceptEnabled.hookBefore {
            result = false
        }

        methodGetIsUrlSafe.hookBefore {
            result = true
        }
    }
}
