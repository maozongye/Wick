package dev.wick.features.items.chat

import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.utils.reflection.bool

object DisableMessageCollapsing : SwitchFeature(), IResolveDex {

    override val technicalId = "禁用消息折叠"
    override val nameRes = R.string.feature_disable_message_collapsing_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_disable_message_collapsing_description

    private val methodFoldMsg by dexMethod {
        matcher {
            usingStrings(".msgsource.sec_msg_node.clip-len")
            paramTypes(null, CharSequence::class.java, null, bool, null, null)
        }
    }

    override fun onEnable() {
        methodFoldMsg.hookBefore {
            result = null
        }
    }
}
