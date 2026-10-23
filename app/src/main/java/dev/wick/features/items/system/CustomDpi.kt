package dev.wick.features.items.system

import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.Modifiers
import dev.ujhhgtg.reflekt.utils.toClass
import dev.wick.R
import dev.wick.dexkit.abc.IResolveDex
import dev.wick.dexkit.dsl.dexMethod
import dev.wick.features.core.ClickableFeature
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.data.KvStore.prefOption
import dev.wick.ui.content.AlertDialogContent
import dev.wick.ui.content.Button
import dev.wick.ui.content.TextButton
import dev.wick.ui.utils.showComposeDialog
import dev.wick.utils.android.showToast
import dev.wick.utils.reflection.bool
import dev.wick.utils.reflection.float
import dev.wick.utils.reflection.int

object CustomDpi : ClickableFeature(), IResolveDex {

    override val technicalId = "DPI 修改"
    override val nameRes = R.string.feature_custom_dpi_name
    override val categoryIds = listOf(FeatureCategoryIds.BEAUTIFY, FeatureCategoryIds.SYSTEM_PRIVACY)
    override val descriptionRes = R.string.feature_custom_dpi_description

    private val methodGetDisplayMetrics by dexMethod {
        matcher {
            declaredClass {
                usingEqStrings("MicroMsg.MMDensityManager", "screenResolution_target_field")
            }

            modifiers = Modifiers.PUBLIC
            returnType = DisplayMetrics::class.java.name
            paramCount = 0

            addInvoke {
                returnType = "boolean"
            }
        }
    }

    private var customDpi by prefOption("custom_dpi", 360)

    override fun onEnable() {
        methodGetDisplayMetrics.hookAfter {
            val metrics = result as? DisplayMetrics ?: return@hookAfter
            applyCustomDpi(metrics)
        }

        hookTabIconScale()
    }

    override fun onClick(context: ComponentActivity) {
        showComposeDialog(context) {
            var value by remember { mutableStateOf(customDpi.toString()) }

            AlertDialogContent(
                title = { Text(stringResource(R.string.feature_custom_dpi_name)) },
                text = {
                    TextField(
                        value = value,
                        onValueChange = { value = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.system_custom_dpi_width)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                dismissButton = {
                    TextButton(onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
                },
                confirmButton = {
                    Button(onClick = {
                        val dpiInput = value.toIntOrNull()
                        if (dpiInput == null || dpiInput <= 0) {
                            showToast(localizedSystemString(R.string.system_invalid_number))
                            return@Button
                        }
                        customDpi = dpiInput
                        onDismiss()
                    }) { Text(stringResource(R.string.dialog_confirm)) }
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun applyCustomDpi(metrics: DisplayMetrics) {
        val dpi = customDpi
        val fontScale = metrics.scaledDensity / metrics.density
        metrics.density = dpi / 160.0f
        metrics.densityDpi = dpi
        metrics.scaledDensity = dpi / 160.0f * fontScale
    }

    private fun hookTabIconScale() {
        val tabIconView = "com.tencent.mm.ui.TabIconView".toClass()
        val method = tabIconView.reflekt().firstMethod {
            parameters(int, int, int, bool)
        }

        method.hookBefore {
            val view = thisObject ?: return@hookBefore
            val field = view.reflekt().firstField {
                type = float
                modifiers { !it.contains(Modifiers.STATIC) }
            }

            field.set(customDpi * 1.1666666f / 400.0f)
        }
    }
}
