/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.features.module.modules.client

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

object TabGUIModule : Module("TabGUI", Category.CLIENT) {

    private val tabShowPlayerSkin by boolean("Show Player Heads", true)
    val tabShowPlayerPing by boolean("Show Ping Numbers", true)
    val hidePingTag by boolean("Show Ping MS Tag", false) { tabShowPlayerPing }
    val pingTextShadow by boolean("Ping Text Shadow", true) { tabShowPlayerPing }

    val tabMoveSelfToTop by boolean("Move Self To Top", true)

    val tabDisableHeader by boolean("Show Header", false)
    val tabDisableFooter by boolean("Show Footer", false)

    val tabScale by choices("Scale", arrayOf("Small", "Normal", "Large", "Extra Large", "Default"), "Default")

    var flagRenderTabOverlay = false
        get() = field && tabShowPlayerSkin
}