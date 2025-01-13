/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.config

import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.FDPClient.moduleManager
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.handler.api.ClientApi
import net.ccbluex.liquidbounce.features.module.modules.client.TargetModule
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.io.HttpUtils
import net.ccbluex.liquidbounce.utils.kotlin.StringUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.translateAlternateColorCodes
import org.lwjgl.input.Keyboard
import java.awt.Color
import javax.vecmath.Vector2f
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

/**
 * Utility class for handling settings and scripts in LiquidBounce.
 */
object SettingsUtils {

    /**
     * Execute settings script.
     * @param script The script to apply.
     */
    fun applyScript(script: String) {
        script.lineSequence().forEachIndexed { index, s ->
            if (s.isEmpty() || s.startsWith('#')) {
                return@forEachIndexed
            }

            val args = s.split(" ").toTypedArray()

            if (args.size <= 1) {
                chat("§7[§3§lAutoSettings§7] §cSyntax error at line '$index' in setting script.\n§8§lLine: §7$s")
                return@forEachIndexed
            }

            when (args[0]) {
                "chat" -> chat(
                    "§e${
                        translateAlternateColorCodes(
                            StringUtils.toCompleteString(
                                args,
                                1
                            )
                        )
                    }"
                )

                "unchat" -> chat(
                    translateAlternateColorCodes(
                        StringUtils.toCompleteString(
                            args,
                            1
                        )
                    )
                )

                "load" -> {
                    val url = StringUtils.toCompleteString(args, 1)
                    runCatching {
                        val settings = if (url.startsWith("http")) {
                            val (text, code) = HttpUtils.get(url)

                            if (code != 200) {
                                error(text)
                            }

                            text
                        } else {
                            runBlocking {
                                ClientApi.getSettingsScript(settingId = url)
                            }
                        }

                        applyScript(settings)
                    }.onSuccess {
                        chat("§7[§3§lAutoSettings§7] §7Loaded settings §a§l$url§7.")
                    }.onFailure {
                        chat("§7[§3§lAutoSettings§7] §7Failed to load settings §a§l$url§7.")
                    }
                }

                "targetPlayer", "targetPlayers" -> setTargetSetting(TargetModule::playerValue, args)
                "targetMobs" -> setTargetSetting(TargetModule::mobValue, args)
                "targetAnimals" -> setTargetSetting(TargetModule::animalValue, args)
                "targetInvisible" -> setTargetSetting(TargetModule::invisibleValue, args)
                "targetDead" -> setTargetSetting(TargetModule::deadValue, args)

                else -> {
                    if (args.size < 3) {
                        chat("§7[§3§lAutoSettings§7] §cSyntax error at line '$index' in setting script.\n§8§lLine: §7$s")
                        return@forEachIndexed
                    }

                    val moduleName = args[0]
                    val valueName = args[1]
                    val value = args[2]
                    val module = moduleManager[moduleName]

                    if (module == null) {
                        chat("§7[§3§lAutoSettings§7] §cModule §a§l$moduleName§c does not exist!")
                        return@forEachIndexed
                    }

                    when (valueName) {
                        "toggle" -> setToggle(module, value)
                        "bind" -> setBind(module, value)
                        else -> setValue(module, valueName, value, args)
                    }
                }
            }
        }

        FileManager.saveConfig(FileManager.valuesConfig)
    }

    // Utility functions for setting target settings
    private fun setTargetSetting(setting: KMutableProperty0<Boolean>, args: Array<String>) {
        setting.set(args[1].equals("true", ignoreCase = true))
        chat("§7[§3§lAutoSettings§7] §a§l${args[0]}§7 set to §c§l${args[1]}§7.")
    }

    // Utility functions for setting toggles
    private fun setToggle(module: Module, value: String) {
        module.state = value.equals("true", ignoreCase = true)
        //chat("§7[§3§lAutoSettings§7] §a§l${module.getName()} §7was toggled §c§l${if (module.state) "on" else "off"}§7.")
    }

    // Utility functions for setting binds
    private fun setBind(module: Module, value: String) {
        module.keyBind = Keyboard.getKeyIndex(value)
        chat(
            "§7[§3§lAutoSettings§7] §a§l${module.getName()} §7was bound to §c§l${
                Keyboard.getKeyName(
                    module.keyBind
                )
            }§7."
        )
    }

    // Utility functions for setting values
    private fun setValue(module: Module, valueName: String, value: String, args: Array<String>) {
        val moduleValue = module[valueName]

        if (moduleValue == null) {
            chat("§7[§3§lAutoSettings§7] §cValue §a§l$valueName§c wasn't found in module §a§l${module.getName()}§c.")
            return
        }

        try {
            when (moduleValue) {
                is BoolValue -> moduleValue.changeValue(value.toBoolean())
                is FloatValue -> moduleValue.changeValue(value.toFloat())
                is IntegerValue -> moduleValue.changeValue(value.toInt())
                is TextValue -> moduleValue.changeValue(StringUtils.toCompleteString(args, 2))
                is ListValue -> moduleValue.changeValue(value)
                is IntegerRangeValue, is FloatRangeValue -> {
                    value.split("..").takeIf { it.size == 2 }?.let {
                        val (min, max) = (it[0].toFloatOrNull() ?: return@let) to (it[1].toFloatOrNull() ?: return@let)

                        if (moduleValue is IntegerRangeValue) {
                            moduleValue.changeValue(min.toInt()..max.toInt())
                        } else (moduleValue as FloatRangeValue).changeValue(min..max)
                    }
                }
                is ColorValue -> {
                    moduleValue.readColorFromConfig(value)?.let { list ->
                        val pos = list[0].toFloatOrNull() to list[1].toFloatOrNull()
                        val hue = list[2].toFloatOrNull()
                        val alpha = list[3].toFloatOrNull()
                        val rainbow = list[4].toBooleanStrictOrNull()

                        rainbow?.let { moduleValue.rainbow = it }

                        if (pos.first != null && pos.second != null && hue != null && alpha != null) {
                            moduleValue.colorPickerPos = Vector2f(pos.first!!, pos.second!!)
                            moduleValue.hueSliderY = hue
                            moduleValue.opacitySliderY = alpha

                            val rgb = Color.HSBtoRGB(hue, pos.first!!, 1 - pos.second!!)

                            val a = (alpha * 255).roundToInt()

                            val r = (rgb shr 16) and 0xFF
                            val g = (rgb shr 8) and 0xFF
                            val b = rgb and 0xFF

                            moduleValue.set(Color(a shl 24 or (r shl 16) or (g shl 8) or b, true))
                        }
                    }
                }

                else -> {}
            }

            // chat("§7[§3§lAutoSettings§7] §a§l${module.getName()}§7 value §8§l${moduleValue.name}§7 set to §c§l$value§7.")
        } catch (e: Exception) {
            chat("§7[§3§lAutoSettings§7] §a§l${e.javaClass.name}§7(${e.message}) §cAn Exception occurred while setting §a§l$value§c to §a§l${moduleValue.name}§c in §a§l${module.getName()}§c.")
        }
    }

    /**
     * Generate settings script.
     * @param values Include values in the script.
     * @param binds Include key binds in the script.
     * @param states Include module states in the script.
     * @return The generated script.
     */
    fun generateScript(values: Boolean, binds: Boolean, states: Boolean): String {
        val all = values && binds && states

        return buildString {
            for (module in moduleManager) {
                if (all || !module.subjective) {
                    if (values) {
                        for (value in module.values) {
                            if (all || !value.subjective && value.shouldRender()) {
                                val valueString = "${module.name} ${value.name} ${value.getString()}"

                                if (valueString.isNotBlank()) {
                                    appendLine(valueString)
                                }
                            }
                        }
                    }

                    if (states) {
                        appendLine("${module.name} toggle ${module.state}")
                    }

                    if (binds) {
                        appendLine("${module.name} bind ${Keyboard.getKeyName(module.keyBind)}")
                    }
                }
            }
        }.trimEnd()
    }

}