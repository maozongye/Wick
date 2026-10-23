package dev.wick.features.items.debug

import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.tencent.mm.plugin.setting.ui.setting.SettingsAboutMMHeaderPreference
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.createInstance
import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.ClickableFeature
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.utils.android.copyToClipboard
import dev.wick.utils.android.showToast
import dev.wick.utils.hookBeforeDirectly

object CopyWeChatDebugInfo : ClickableFeature(), IResolveDex {

    override val technicalId = "复制调试信息"
    override val nameRes = R.string.feature_copy_we_chat_debug_info_name
    override val categoryIds = listOf(FeatureCategoryIds.DEBUG)
    override val descriptionRes = R.string.feature_copy_we_chat_debug_info_description

    override val noSwitchWidget = true

    override fun onClick(context: ComponentActivity) {
        val unhook = TextView::class.reflekt()
            .firstMethod {
                name = "setText"
                parameters(CharSequence::class)
            }.hookBeforeDirectly {
                val debugText = (args[0] as StringBuilder).toString()
                copyToClipboard(context, debugText)
                showToast(context, context.localizedDebugString(R.string.debug_copied))
                throwable = RuntimeException("halt method")
            }

        val onClickListener = methodOnClick.method.declaringClass
            .createInstance(SettingsAboutMMHeaderPreference(context))
        // WeChat has a check:
        // long jCurrentTimeMillis = System.currentTimeMillis();
        //        long j16 = this.f158935d;
        //        if (j16 > jCurrentTimeMillis || jCurrentTimeMillis - j16 > 300) {
        //            this.f158935d = jCurrentTimeMillis;
        //            return;
        //        }
        onClickListener.reflekt()
            .firstField {
                type = Long::class
            }.set(System.currentTimeMillis())

        runCatching {
            methodOnClick.method.invoke(onClickListener, View(context))
        }
        unhook.unhook()
    }

    private val methodOnClick by dexMethod {
        searchPackages("com.tencent.mm.plugin.setting.ui.setting")
        matcher {
            name = "onClick"
            usingEqStrings("com/tencent/mm/plugin/setting/ui/setting/SettingsAboutMMHeaderPreference$1", $$"android/view/View$OnClickListener", "onClick")
        }
    }
}
