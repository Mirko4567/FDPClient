/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.FileManager
import net.ccbluex.liquidbounce.config.core.ConfigManager
import net.ccbluex.liquidbounce.event.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.client.ClientSpoof
import net.ccbluex.liquidbounce.features.special.discord.DiscordRPC
import net.ccbluex.liquidbounce.features.special.spoof.ClientSpoofHandler
import net.ccbluex.liquidbounce.handler.combat.CombatManager
import net.ccbluex.liquidbounce.handler.macro.MacroManager
import net.ccbluex.liquidbounce.handler.network.BungeeCordSpoof
import net.ccbluex.liquidbounce.handler.network.ClientFixes
import net.ccbluex.liquidbounce.handler.script.ScriptManager
import net.ccbluex.liquidbounce.ui.cape.GuiCapeManager
import net.ccbluex.liquidbounce.ui.gui.EnumLaunchFilter
import net.ccbluex.liquidbounce.ui.gui.GuiLaunchOptionSelectMenu
import net.ccbluex.liquidbounce.ui.gui.LaunchFilterInfo
import net.ccbluex.liquidbounce.ui.gui.LaunchOption
import net.ccbluex.liquidbounce.ui.hud.HUD
import net.ccbluex.liquidbounce.ui.gui.keybind.KeyBindManager
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.ui.i18n.LanguageManager
import net.ccbluex.liquidbounce.ui.sound.TipSoundManager
import net.ccbluex.liquidbounce.utils.*
import net.ccbluex.liquidbounce.utils.MinecraftInstance.mc
import net.ccbluex.liquidbounce.utils.misc.HttpUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.util.ResourceLocation
import java.util.*
import kotlin.concurrent.thread

object FDPClient {

    // Client information
    const val CLIENT_NAME = "FDPClient"
    const val CLIENT_CHAT = "§7[§b§lFDPClient§7] "
    const val CLIENT_DEV = "Zywl, 1zuna, Yuugiri, Dg636, vPrah, gatodepan, DinoFeng, Co`Dynamic. by (SkidderMC & CCBlueX - TEAM)"
    const val CLIENT_WEBSITE = "https://fdpinfo.github.io"
    const val CLIENT_DISCORD = "https://discord.gg/3XRFGeqEYD"
    const val CLIENT_VERSION = "v5.6.1"

    var USER_NAME = ""

    // Flags
    var isInDev = true
    var isStarting = true
    var isLoadingConfig = true
    private var latest = ""

    @JvmField
    val gitInfo = Properties().also {
        val inputStream = FDPClient::class.java.classLoader.getResourceAsStream("git.properties")
        if (inputStream != null) {
            it.load(inputStream)
        } else {
            it["git.branch"] = "Main"
        }
    }


    // Managers
    lateinit var moduleManager: ModuleManager
    lateinit var commandManager: CommandManager
    lateinit var eventManager: EventManager
    lateinit var fileManager: FileManager
    lateinit var scriptManager: ScriptManager
    lateinit var tipSoundManager: TipSoundManager
    lateinit var combatManager: CombatManager
    lateinit var macroManager: MacroManager
    lateinit var configManager: ConfigManager

    // Some UI things
    lateinit var hud: HUD
    lateinit var mainMenu: GuiScreen
    lateinit var keyBindManager: KeyBindManager

    // Discord RPC
    private lateinit var discordRPC: DiscordRPC


    // Menu Background
    var background: ResourceLocation? = ResourceLocation("fdpclient/gui/design/background.png")

    val launchFilters = mutableListOf<EnumLaunchFilter>()
    private val dynamicLaunchOptions: Array<LaunchOption>
        get() = ClassUtils.resolvePackage(
            "${LaunchOption::class.java.`package`.name}.options",
            LaunchOption::class.java
        )
            .filter {
                val annotation = it.getDeclaredAnnotation(LaunchFilterInfo::class.java)
                if (annotation != null) {
                    return@filter annotation.filters.toMutableList() == launchFilters
                }
                false
            }
            .map {
                try {
                    it.newInstance()
                } catch (e: IllegalAccessException) {
                    ClassUtils.getObjectInstance(it) as LaunchOption
                }
            }.toTypedArray()

    /**
     * Execute if client will be started
     */
    fun initClient() {
        mc.fontRendererObj.also { mc.fontRendererObj = it }
        SplashProgress.setProgress(2, "Initializing Minecraft")
        ClientUtils.logInfo("Loading $CLIENT_NAME $CLIENT_VERSION")
        ClientUtils.logInfo("Initializing...")
        val startTime = System.currentTimeMillis()

        // Initialize managers
        SplashProgress.setProgress(2, "Initializing $CLIENT_NAME")
        SplashProgress.setSecondary("Initializing Managers")
        fileManager = FileManager()
        configManager = ConfigManager()
        eventManager = EventManager()
        commandManager = CommandManager()
        macroManager = MacroManager()
        moduleManager = ModuleManager()
        scriptManager = ScriptManager()
        keyBindManager = KeyBindManager()
        combatManager = CombatManager()
        tipSoundManager = TipSoundManager()

        // Load language
        SplashProgress.setSecondary("Initializing Language")
        LanguageManager.switchLanguage(Minecraft.getMinecraft().gameSettings.language)

        // Register listeners
        SplashProgress.setSecondary("Initializing Listeners")
        eventManager.registerListener(RotationUtils())
        eventManager.registerListener(ClientFixes)
        eventManager.registerListener(InventoryUtils)
        eventManager.registerListener(BungeeCordSpoof())
        eventManager.registerListener(SessionUtils())
        eventManager.registerListener(macroManager)
        eventManager.registerListener(combatManager)
        eventManager.registerListener(ClientSpoofHandler())

        // Init Discord RPC
        discordRPC = DiscordRPC

        // Load client fonts
        Fonts.loadFonts()

        // Setup default states on first launch
        if (!fileManager.loadLegacy()) {
            ClientUtils.logInfo("Setting up default modules...")
            moduleManager.getModule(ClientSpoof::class.java)?.state = true
            moduleManager.getModule(net.ccbluex.liquidbounce.features.module.modules.client.DiscordRPCModule::class.java)?.state = true
        }

        // Setup modules
        SplashProgress.setSecondary("Initializing Modules")
        moduleManager.registerModules()

        SplashProgress.setSecondary("Initializing Scripts")
        // Load and enable scripts
        try {
            scriptManager.loadScripts()
            scriptManager.enableScripts()
        } catch (throwable: Throwable) {
            ClientUtils.logError("Failed to load scripts.", throwable)
        }

        // Register commands
        SplashProgress.setSecondary("Initializing Commands")
        commandManager.registerCommands()

        // Load GUI
        SplashProgress.setSecondary("Initializing GUI")
        GuiCapeManager.load()
        mainMenu = GuiLaunchOptionSelectMenu()
        hud = HUD.createDefault()

        // Load configs
        SplashProgress.setSecondary("Initializing Configs")
        fileManager.loadConfigs(
            fileManager.accountsConfig,
            fileManager.friendsConfig,
            fileManager.specialConfig,
            fileManager.themeConfig,
            fileManager.hudConfig,
            fileManager.xrayConfig
        )

        // Run update checker
        if (CLIENT_VERSION != "unknown") {
            thread(block = this::checkUpdate)
        }

        // Set title
        ClientUtils.setTitle()

        // Log success
        ClientUtils.logInfo("$CLIENT_NAME $CLIENT_VERSION loaded in ${(System.currentTimeMillis() - startTime)}ms!")
        SplashProgress.setSecondary("$CLIENT_NAME $CLIENT_VERSION loaded in ${(System.currentTimeMillis() - startTime)}ms!")
    }

    private fun checkUpdate() {
        try {
            val get = HttpUtils.get("https://api.github.com/repos/SkidderMC/FDPClient/commits/${gitInfo["git.branch"]}")

            val jsonObj = JsonParser()
                .parse(get).asJsonObject

            latest = jsonObj.get("sha").asString.substring(0, 7)

            if (latest != gitInfo["git.commit.id.abbrev"]) {
                ClientUtils.logInfo("New version available: $latest")
            } else {
                ClientUtils.logInfo("No new version available")
            }
        } catch (t: Throwable) {
            ClientUtils.logError("Failed to check for updates.", t)
        }
    }

    /**
     * Execute if client ui type is selected
     */
    // Start dynamic launch options
    fun startClient() {
        dynamicLaunchOptions.forEach {
            it.start()
        }

        // Load configs
        configManager.loadLegacySupport()
        configManager.loadConfigSet()

        // Set is starting status
        isStarting = false
        isLoadingConfig = false

        ClientUtils.logInfo("$CLIENT_NAME $CLIENT_VERSION started!")
        mc.fontRendererObj.also { mc.fontRendererObj = it }
        SplashProgress.setProgress(4, "Initializing $CLIENT_NAME")

    }

    /**
     * Execute if client will be stopped
     */
    fun stopClient() {
        // Check if client is not starting or loading configurations
        if (!isStarting && !isLoadingConfig) {
            ClientUtils.logInfo("Shutting down $CLIENT_NAME $CLIENT_VERSION!")

            // Call client shutdown
            eventManager.callEvent(ClientShutdownEvent())

            // Save configurations
            GuiCapeManager.save() // Save capes
            configManager.save(true, forceSave = true) // Save configs
            fileManager.saveAllConfigs() // Save file manager configs

            // Stop dynamic launch options
            dynamicLaunchOptions.forEach {
                it.stop()
            }

            // Stop Discord RPC
            DiscordRPC.stop()
        }
    }
}
