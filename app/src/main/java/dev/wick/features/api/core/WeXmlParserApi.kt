package dev.wick.features.api.core

import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.LazyFeature
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.utils.HookParam
import dev.wick.utils.WeLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * XML parse hook, installed only once something actually subscribes.
 *
 * Previously an eager [ApiFeature]: the hook on the host's XmlParser ran for every WeChat launch
 * even when no feature listened, which is a permanent, enumerable modification of a host method.
 * Now the listener set is what arms it — no subscriber, no hook.
 */
object WeXmlParserApi : LazyFeature(), IResolveDex {

    override val technicalId = "XML 解析钩子服务"
    override val nameRes = R.string.feature_we_xml_parser_api_name
    override val categoryIds = listOf(FeatureCategoryIds.API)
    override val descriptionRes = R.string.feature_we_xml_parser_api_description

    private const val TAG = "WeXmlParserApi"

    fun interface IAfterParseListener {
        fun onParse(param: HookParam, result: MutableMap<String, Any?>)
    }

    private val listeners = CopyOnWriteArrayList<IAfterParseListener>()

    fun addListener(listener: IAfterParseListener) {
        listeners.add(listener)
        arm()
    }

    fun removeListener(listener: IAfterParseListener) {
        listeners.remove(listener)
    }

    private val methodXmlParser by dexMethod {
        searchPackages("com.tencent.mm.sdk.platformtools")
        matcher {
            usingEqStrings("MicroMsg.SDK.XmlParser", "[ %s ]")
        }
    }

    override fun installHooks() {
        methodXmlParser.hookAfter {
            val param = this

            @Suppress("UNCHECKED_CAST")
            val result = result as? MutableMap<String, Any?>? ?: return@hookAfter
            listeners.forEach { listener ->
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    listener.onParse(param, result)
                }.onFailure { WeLogger.e(TAG, "failed to execute listener ${listener.javaClass.name}", it) }
            }
        }
    }

    override val hookIdleTimeoutMs: Long = IDLE_TIMEOUT_MS

    override fun onDisable() {
        listeners.clear()
    }

    private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
}
