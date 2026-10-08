package dev.agi.mcbot;

import dev.agi.mcbot.bot.BotManager;
import dev.agi.mcbot.config.ModConfig;
import dev.agi.mcbot.net.HttpServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * AGI MC Bot Bridge main entry (Forge 1.16.5).
 *
 * Responsibilities:
 * - Load config
 * - Start HTTP control service
 * - Manage Bot lifecycle
 */
@Mod("agimcbot")
public class AgiMcBotMod {
    public static final String MOD_ID = "agimcbot";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static AgiMcBotMod instance;

    private ModConfig config;
    private HttpServer httpServer;

    public AgiMcBotMod() {
        instance = this;
        LOGGER.info("[AGI-MC] Initializing...");

        // Load config
        this.config = ModConfig.load();

        if (!config.isEnabled()) {
            LOGGER.warn("[AGI-MC] enabled=false in config, Mod disabled");
            return;
        }

        // Register server lifecycle
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("[AGI-MC] Init complete, waiting for server start");
    }

    public static AgiMcBotMod getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        LOGGER.info("[AGI-MC] Server started, starting HTTP service on {}:{}",
                config.getBindAddress(), config.getBindPort());

        BotManager.init(event.getServer());

        this.httpServer = new HttpServer(config, event.getServer());
        try {
            this.httpServer.start();
        } catch (Exception e) {
            LOGGER.error("[AGI-MC] HTTP service start failed", e);
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        LOGGER.info("[AGI-MC] Server stopping, cleaning up");

        if (this.httpServer != null) {
            this.httpServer.stop();
            this.httpServer = null;
        }

        BotManager.shutdown();
    }
}
