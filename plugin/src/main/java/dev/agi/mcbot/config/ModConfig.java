package dev.agi.mcbot.config;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/**
 * Mod config.
 *
 * Config file location: server run dir / config / agi-mc-bot.properties
 */
public class ModConfig {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Properties props;

    private ModConfig(Properties props) {
        this.props = props;
    }

    public static ModConfig load() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path configPath = configDir.resolve("agi-mc-bot.properties");
        Properties props = new Properties();

        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            } catch (IOException e) {
                LOGGER.error("[AGI-MC] Failed to read config, using defaults", e);
            }
        } else {
            // First start: generate default config + random token
            props.setProperty("bind.address", "127.0.0.1");
            props.setProperty("bind.port", "25580");
            props.setProperty("auth.token", generateToken());
            props.setProperty("enabled", "true");
            props.setProperty("idle.timeout", "300");
            props.setProperty("audit.enabled", "true");
            props.setProperty("audit.file", "logs/agi-audit.log");
            props.setProperty("rate.limit", "60");
            save(configPath, props);

            LOGGER.info("[AGI-MC] Generated default config: {}", configPath.toAbsolutePath());
            LOGGER.info("[AGI-MC] Auth token: {}", props.getProperty("auth.token"));
        }

        return new ModConfig(props);
    }

    private static void save(Path path, Properties props) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "AGI MC Bot Bridge config");
            }
        } catch (IOException e) {
            LOGGER.error("[AGI-MC] Failed to save config", e);
        }
    }

    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(props.getProperty("enabled", "true"));
    }

    public String getBindAddress() {
        return props.getProperty("bind.address", "127.0.0.1");
    }

    public int getBindPort() {
        try {
            return Integer.parseInt(props.getProperty("bind.port", "25580"));
        } catch (NumberFormatException e) {
            return 25580;
        }
    }

    public String getAuthToken() {
        return props.getProperty("auth.token", "");
    }

    public int getIdleTimeout() {
        try {
            return Integer.parseInt(props.getProperty("idle.timeout", "300"));
        } catch (NumberFormatException e) {
            return 300;
        }
    }

    public boolean isAuditEnabled() {
        return Boolean.parseBoolean(props.getProperty("audit.enabled", "true"));
    }

    public String getAuditFile() {
        return props.getProperty("audit.file", "logs/agi-audit.log");
    }

    public int getRateLimit() {
        try {
            return Integer.parseInt(props.getProperty("rate.limit", "60"));
        } catch (NumberFormatException e) {
            return 60;
        }
    }
}
