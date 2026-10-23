package dev.wick.features.items.chat

import android.view.View
import android.widget.ImageButton
import com.tencent.mm.pluginsdk.ui.chat.ChatFooter
import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.api.ui.WeChatInputBarMenuApi
import dev.wick.features.core.ApiFeature
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.ui.utils.findViewByChildIndexes
import dev.wick.ui.utils.findViewsWhich

object ChatFooterHooks : ApiFeature(), IResolveDex {

    override val technicalId = "聊天输入栏钩子"
    override val nameRes = R.string.feature_chat_footer_hooks_name
    override val categoryIds = listOf(FeatureCategoryIds.API)
    override val descriptionRes = R.string.feature_chat_footer_hooks_description

    private val methodInitSmileyBtn by dexMethod {
        searchPackages("com.tencent.mm.pluginsdk.ui.chat")
        matcher {
            usingEqStrings("initSmileyBtn")
        }
    }

    override fun onEnable() {
        methodInitSmileyBtn.hookAfter {
            val chatFooter = thisObject as ChatFooter
            val searchedView = chatFooter.findViewByChildIndexes(0)!!
            val imgButtons = searchedView.findViewsWhich { view ->
                view.javaClass.simpleName == "WeImageButton"
            }.map { it as ImageButton }.toList()

            if (VoicePanel.isEnabled) {
                val voiceBtn = imgButtons.first()
                voiceBtn.setOnLongClickListener { view ->
                    VoicePanel.openPanel(view)
                    true
                }
            }

            if (StickerPanel.isEnabled) {
                val emojiBtn = imgButtons[1]
                emojiBtn.setOnLongClickListener { v ->
                    StickerPanel.openPanel(v)
                    true
                }
            }

            val menuBtn = imgButtons.last()
            val sendBtn = WeChatInputBarMenuApi.findSendButton(chatFooter)

            listOf(menuBtn, sendBtn).forEach {
                it.setOnLongClickListener { view ->
                    WeChatInputBarMenuApi.showMenu(view.context, chatFooter)
                    true
                }
            }
        }
    }
}
