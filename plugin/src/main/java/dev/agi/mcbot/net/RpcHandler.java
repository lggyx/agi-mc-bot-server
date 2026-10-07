package dev.agi.mcbot.net;

import com.fasterxml.jackson.databind.JsonNode;
import dev.agi.mcbot.bot.BotManager;
import net.minecraft.server.MinecraftServer;

import java.util.*;

/**
 * RPC 方法分发。
 *
 * 方法清单见 protocol/rpc.json。
 */
public class RpcHandler {
    private final MinecraftServer server;

    public RpcHandler(MinecraftServer server) {
        this.server = server;
    }

    public Object dispatch(String method, JsonNode params) {
        return switch (method) {
            case "sense.getState" -> senseGetState(params);
            case "sense.getNearbyBlocks" -> senseGetNearbyBlocks(params);
            case "sense.getNearbyEntities" -> senseGetNearbyEntities(params);
            case "sense.getInventory" -> senseGetInventory(params);

            case "action.moveTo" -> actionMoveTo(params);
            case "action.lookAt" -> actionLookAt(params);
            case "action.mineBlock" -> actionMineBlock(params);
            case "action.placeBlock" -> actionPlaceBlock(params);
            case "action.attack" -> actionAttack(params);
            case "action.useItem" -> actionUseItem(params);

            case "server.getStatus" -> serverGetStatus();
            case "bot.list" -> botList();
            case "bot.spawn" -> botSpawn(params);
            case "bot.despawn" -> botDespawn(params);

            default -> throw new RpcException(-32601, "Method not found: " + method);
        };
    }

    // ---------- sense ----------

    private Object senseGetState(JsonNode p) {
        String botId = require(p, "bot_id");
        var bot = findBot(botId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bot_id", bot.getName());
        r.put("name", bot.getName());
        r.put("online", bot.isOnline());
        r.put("position", vec(bot.getSpawnPos()));
        r.put("idle_ms", bot.getIdleMs());
        return r;
    }

    private Object senseGetNearbyBlocks(JsonNode p) {
        String botId = require(p, "bot_id");
        findBot(botId);
        // TODO Phase 2
        return Map.of("blocks", List.of());
    }

    private Object senseGetNearbyEntities(JsonNode p) {
        String botId = require(p, "bot_id");
        findBot(botId);
        // TODO Phase 2
        return Map.of("entities", List.of());
    }

    private Object senseGetInventory(JsonNode p) {
        String botId = require(p, "bot_id");
        findBot(botId);
        // TODO Phase 2
        return Map.of("items", List.of());
    }

    // ---------- action ----------

    private Object actionMoveTo(JsonNode p) {
        String botId = require(p, "bot_id");
        var bot = findBot(botId);
        bot.touch();
        // TODO Phase 1: 实现移动
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("error", "not_implemented");
        return r;
    }

    private Object actionLookAt(JsonNode p) {
        String botId = require(p, "bot_id");
        var bot = findBot(botId);
        bot.touch();
        // TODO Phase 1
        return Map.of("yaw", 0.0, "pitch", 0.0);
    }

    private Object actionMineBlock(JsonNode p) {
        String botId = require(p, "bot_id");
        findBot(botId);
        // TODO Phase 2
        return Map.of("success", false, "error", "not_implemented");
    }

    private Object actionPlaceBlock(JsonNode p) {
        String botId = require(p, "bot_id");
        findBot(botId);
        // TODO Phase 2
        return Map.of("success", false, "error", "not_implemented");
    }

    private Object actionAttack(JsonNode p) {
        String botId = require(p, "bot_id");
        findBot(botId);
        // TODO Phase 2
        return Map.of("success", false, "error", "not_implemented");
    }

    private Object actionUseItem(JsonNode p) {
        String botId = require(p, "bot_id");
        findBot(botId);
        // TODO Phase 2
        return Map.of("success", false, "error", "not_implemented");
    }

    // ---------- server ----------

    private Object serverGetStatus() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tps", 20.0);           // TODO Phase 4: 真实 TPS
        r.put("mspt", 0.0);
        r.put("online_players", server.getCurrentPlayerCount());
        r.put("max_players", server.getMaxPlayerCount());
        r.put("uptime_seconds", 0);
        return r;
    }

    // ---------- bot ----------

    private Object botList() {
        List<Map<String, Object>> bots = new ArrayList<>();
        for (var b : BotManager.getInstance().list()) {
            bots.add(Map.of(
                    "bot_id", b.getName(),
                    "name", b.getName(),
                    "online", b.isOnline()
            ));
        }
        return Map.of("bots", bots);
    }

    private Object botSpawn(JsonNode p) {
        String name = require(p, "name");
        double x = p.path("position").path("x").asDouble(0);
        double y = p.path("position").path("y").asDouble(64);
        double z = p.path("position").path("z").asDouble(0);

        var bot = BotManager.getInstance().spawn(name, new net.minecraft.util.math.Vec3d(x, y, z));
        return Map.of("bot_id", bot.getName());
    }

    private Object botDespawn(JsonNode p) {
        String botId = require(p, "bot_id");
        BotManager.getInstance().despawn(botId);
        return Map.of("success", true);
    }

    // ---------- helpers ----------

    private BotManager.BotHandle findBot(String id) {
        return BotManager.getInstance().get(id)
                .orElseThrow(() -> new RpcException(-32010, "Bot not found: " + id));
    }

    private static String require(JsonNode p, String field) {
        if (!p.has(field) || p.get(field).isNull()) {
            throw new RpcException(-32602, "Missing required param: " + field);
        }
        return p.get(field).asText();
    }

    private static Map<String, Object> vec(net.minecraft.util.math.Vec3d v) {
        return Map.of("x", v.x, "y", v.y, "z", v.z);
    }
}
