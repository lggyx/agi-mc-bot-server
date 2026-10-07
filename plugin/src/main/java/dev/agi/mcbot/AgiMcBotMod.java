package dev.agi.mcbot;

import dev.agi.mcbot.config.ModConfig;
import dev.agi.mcbot.net.HttpServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AGI MC Bot Bridge 主入口。
 *
 * 职责：
 * - 加载配置
 * - 启动 HTTP 控制服务
 * - 管理 Bot 生命周期
 */
public class AgiMcBotMod implements ModInitializer {
    public static final String MOD_ID = "agi-mc-bot-bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AgiMcBotMod instance;
    private ModConfig config;
    private HttpServer httpServer;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("[AGI-MC] 正在初始化...");

        // 1. 加载配置
        this.config = ModConfig.load();

        if (!config.isEnabled()) {
            LOGGER.warn("[AGI-MC] 配置中 enabled=false，Mod 已禁用");
            return;
        }

        // 2. 注册服务端生命周期
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        LOGGER.info("[AGI-MC] 初始化完成，等待服务端启动");
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
        LOGGER.info("[AGI-MC] 服务端已启动，启动 HTTP 服务于 {}:{}",
                config.getBindAddress(), config.getBindPort());

        try {
            this.httpServer = new HttpServer(config, server);
            this.httpServer.start();
            LOGGER.info("[AGI-MC] HTTP 服务已就绪");
        } catch (Exception e) {
            LOGGER.error("[AGI-MC] HTTP 服务启动失败", e);
        }
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("[AGI-MC] 服务端正在关闭，清理资源...");

        if (httpServer != null) {
            httpServer.stop();
        }

        BotManager.getInstance().despawnAll();
        LOGGER.info("[AGI-MC] 清理完成");
    }

    public static AgiMcBotMod getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
