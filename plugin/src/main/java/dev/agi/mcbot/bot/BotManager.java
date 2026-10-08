package dev.agi.mcbot.bot;

import com.mojang.authlib.GameProfile;
import dev.agi.mcbot.AgiMcBotMod;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot manager.
 *
 * Handles Bot entity creation, lookup, destruction.
 *
 * Uses the ServerPlayerEntity constructor directly (see FakePlayerFactory)
 * to build a player entity without a real network connection.
 */
public class BotManager {
    private static final Logger LOGGER = LogManager.getLogger();

    private static BotManager instance;

    private final MinecraftServer server;
    private final Map<String, BotHandle> bots = new ConcurrentHashMap<>();

    private BotManager(MinecraftServer server) {
        this.server = server;
    }

    public static BotManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BotManager not initialized");
        }
        return instance;
    }

    public static void init(MinecraftServer server) {
        instance = new BotManager(server);
    }

    public static void shutdown() {
        if (instance != null) {
            instance.despawnAll();
            instance = null;
        }
    }

    /**
     * Spawn a Bot.
     *
     * @param name Player name (shown in player list)
     * @param pos  Spawn position
     * @return Bot handle
     */
    public synchronized BotHandle spawn(String name, Vector3d pos) {
        if (bots.containsKey(name)) {
            throw new IllegalArgumentException("Bot already exists: " + name);
        }

        ServerWorld world = server.overworld();
        if (world == null) {
            throw new IllegalStateException("Overworld not loaded");
        }

        // Derive a stable UUID from the name so same-named Bots always get the same UUID
        UUID botId = FakePlayerFactory.deriveUuid(name);
        GameProfile profile = new GameProfile(botId, name);

        ServerPlayerEntity player = FakePlayerFactory.create(server, world, profile, pos);

        BotHandle handle = new BotHandle(name, player);
        bots.put(name, handle);

        LOGGER.info("[AGI-MC] Bot spawned: {} at {}", name, formatPos(pos));
        return handle;
    }

    /**
     * Remove a Bot.
     */
    public synchronized void despawn(String name) {
        BotHandle handle = bots.remove(name);
        if (handle == null) {
            throw new IllegalArgumentException("Bot not found: " + name);
        }
        handle.getPlayer().remove();
        LOGGER.info("[AGI-MC] Bot removed: {}", name);
    }

    /**
     * Remove all Bots.
     */
    public synchronized void despawnAll() {
        for (BotHandle handle : bots.values()) {
            try {
                handle.getPlayer().remove();
            } catch (Exception e) {
                LOGGER.warn("[AGI-MC] Failed to remove Bot: {}", handle.getName(), e);
            }
        }
        bots.clear();
    }

    /**
     * Find a Bot.
     */
    public BotHandle find(String name) {
        BotHandle handle = bots.get(name);
        if (handle == null) {
            throw new IllegalArgumentException("Bot not found: " + name);
        }
        return handle;
    }

    /**
     * List all Bot names.
     */
    public java.util.Set<String> list() {
        return bots.keySet();
    }

    public MinecraftServer getServer() {
        return server;
    }

    private static String formatPos(Vector3d pos) {
        return String.format("(%.1f, %.1f, %.1f)", pos.x, pos.y, pos.z);
    }
}
