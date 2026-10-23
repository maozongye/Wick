package dev.wick.features.items.system

import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.Modifiers
import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.utils.fs.KnownPaths
import java.lang.reflect.Field

object PreventModuleDataDeletion : SwitchFeature(), IResolveDex {

    override val technicalId = "阻止微信清理模块数据"
    override val nameRes = R.string.feature_prevent_module_data_deletion_name
    override val categoryIds = listOf(FeatureCategoryIds.SYSTEM_PRIVACY)
    override val descriptionRes = R.string.feature_prevent_module_data_deletion_description

    private val methodNativeFileSystemEntryDelete by dexMethod {
        matcher {
            declaredClass {
                usingEqStrings("VFS.NativeFileSystem", "Base directory exists but is not a directory, delete and proceed.Base path: ")
            }

            paramTypes(String::class.java)
            returnType = "boolean"

            invokeMethods {
                add {
                    declaredClass = "java.io.File"
                    name = "delete"
                }
            }
        }
    }
    private lateinit var basePathField: Field

    override fun onEnable() {
        methodNativeFileSystemEntryDelete.hookBefore {
            val relPath = args[0] as String
            if (!::basePathField.isInitialized) {
                basePathField = thisObject!!.reflekt()
                    .firstField {
                        type = String::class
                        modifiers(Modifiers.FINAL)
                    }.self
            }
            val basePath = basePathField.get(thisObject) as String

            val path = "$basePath/$relPath"
            // Match only real module-owned locations. The old `path.contains(BuildConfig.TAG)`
            // also matched unrelated host files whose names happen to contain the product name.
            if (path.startsWith(KnownPaths.moduleRoot.toFile().absolutePath) ||
                path.contains("Layout Inspect")
            ) {
                result = true
            }
        }
    }
}
