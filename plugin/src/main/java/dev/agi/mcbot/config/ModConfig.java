package dev.agi.mcbot.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Mod 配置。
 *
 * 配置文件位置：服务端运行目录 / config / agi-mc-bot.properties
 */
public class ModConfig {
    private final Properties props;

    private ModConfig(Properties props) {
        this.props = props;
    }

    public static ModConfig load() {
        Path configPath = Path.of("config", "agi-mc-bot.properties");
        Properties props = new Properties();

        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            } catch (IOException e) {
                AgiMcBotMod.LOGGER.error("[AGI-MC] 读取配置失败，使用默认值", e);
            }
        } else {
            // 首次启动：生成默认配置 + 随机 Token
            props.setProperty("bind.address", "127.0.0.1");
            props.setProperty("bind.port", "25580");
            props.setProperty("auth.token", generateToken());
            props.setProperty("enabled", "true");
            props.setProperty("idle.timeout", "300");
            props.setProperty("audit.enabled", "true");
            props.setProperty("audit.file", "logs/agi-audit.log");
            props.setProperty("rate.limit", "60");
            save(configPath, props);

            AgiMcBotMod.LOGGER.info("[AGI-MC] 已生成默认配置: {}", configPath.toAbsolutePath());
            AgiMcBotMod.LOGGER.info("[AGI-MC] 鉴权 Token: {}", props.getProperty("auth.token"));
        }

        return new ModConfig(props);
    }

    private static void save(Path path, Properties props) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "AGI MC Bot Bridge 配置");
            }
        } catch (IOException e) {
            AgiMcBotMod.LOGGER.error("[AGI-MC] 保存配置失败", e);
        }
    }

    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public String getBindAddress() {
        return props.getProperty("bind.address", "127.0.0.1");
    }

    public int getBindPort() {
        return Integer.parseInt(props.getProperty("bind.port", "25580"));
    }

    public String getAuthToken() {
        return props.getProperty("auth.token", "");
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(props.getProperty("enabled", "true"));
    }

    public int getIdleTimeout() {
        return Integer.parseInt(props.getProperty("idle.timeout", "300"));
    }

    public boolean isAuditEnabled() {
        return Boolean.parseBoolean(props.getProperty("audit.enabled", "true"));
    }

    public String getAuditFile() {
        return props.getProperty("audit.file", "logs/agi-audit.log");
    }

    public int getRateLimit() {
        return Integer.parseInt(props.getProperty("rate.limit", "60"));
    }
}
