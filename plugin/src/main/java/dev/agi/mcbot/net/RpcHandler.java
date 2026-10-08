package dev.agi.mcbot.net;

import com.fasterxml.jackson.databind.JsonNode;
import dev.agi.mcbot.bot.BotHandle;
import dev.agi.mcbot.bot.BotManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RPC method dispatch.
 *
 * Method list: see protocol/rpc.json.
 * All game-state mutations are wrapped in server.execute() for thread safety.
 */
public class RpcHandler {
    private final MinecraftServer server;

    public RpcHandler(MinecraftServer server) {
        this.server = server;
    }

    public Object dispatch(String method, JsonNode params) {
        switch (method) {
            // ---------- sense ----------
            case "sense.getState":          return senseGetState(params);
            case "sense.getNearbyBlocks":   return senseGetNearbyBlocks(params);
            case "sense.getNearbyEntities": return senseGetNearbyEntities(params);
            case "sense.getInventory":      return senseGetInventory(params);

            // ---------- action ----------
            case "action.moveTo":           return actionMoveTo(params);
            case "action.lookAt":           return actionLookAt(params);
            case "action.mineBlock":        return actionMineBlock(params);
            case "action.placeBlock":       return actionPlaceBlock(params);
            case "action.attack":           return actionAttack(params);
            case "action.useItem":          return actionUseItem(params);

            // ---------- server ----------
            case "server.getStatus":        return serverGetStatus();

            // ---------- bot ----------
            case "bot.list":                return botList();
            case "bot.spawn":               return botSpawn(params);
            case "bot.despawn":             return botDespawn(params);

            default:
                throw new RpcException(-32601, "Method not found: " + method);
        }
    }

    // ========================================================================
    // sense
    // ========================================================================

    private Object senseGetState(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        ServerPlayerEntity player = bot.getPlayer();
        Vector3d pos = player.position();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bot_id", bot.getName());
        r.put("position", mapOf("x", pos.x, "y", pos.y, "z", pos.z));
        r.put("yaw", player.yRot);
        r.put("pitch", player.xRot);
        r.put("health", player.getHealth());
        r.put("max_health", player.getMaxHealth());
        r.put("food", player.getFoodData().getFoodLevel());
        r.put("saturation", player.getFoodData().getSaturationLevel());
        r.put("alive", player.isAlive());
        r.put("on_ground", player.isOnGround());
        r.put("dimension", player.level.dimension().location().toString());
        r.put("game_mode", player.gameMode.getGameModeForPlayer().getName());
        return r;
    }

    private Object senseGetNearbyBlocks(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        String blockId = require(p, "block_id");
        int radius = p.path("radius").asInt(16);
        int limit = p.path("limit").asInt(16);

        World world = bot.getPlayer().level;
        BlockPos center = bot.getPlayer().blockPosition();

        List<Map<String, Object>> found = new ArrayList<>();
        int r2 = radius * radius;

        for (int dx = -radius; dx <= radius && found.size() < limit; dx++) {
            for (int dy = -radius; dy <= radius && found.size() < limit; dy++) {
                for (int dz = -radius; dz <= radius && found.size() < limit; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;

                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    String id = state.getBlock().getRegistryName().toString();
                    if (!id.equals(blockId)) continue;

                    found.add(mapOf(
                            "x", pos.getX(),
                            "y", pos.getY(),
                            "z", pos.getZ(),
                            "distance", Math.sqrt(dx * dx + dy * dy + dz * dz)));
                }
            }
        }

        return mapOf("blocks", found, "count", found.size());
    }

    private Object senseGetNearbyEntities(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        double radius = p.path("radius").asDouble(16);
        boolean includePlayers = p.path("include_players").asBoolean(true);

        ServerPlayerEntity self = bot.getPlayer();
        World world = self.level;
        Vector3d pos = self.position();

        List<Map<String, Object>> found = new ArrayList<>();
        AxisAlignedBB box = self.getBoundingBox().inflate(radius);
        for (Entity e : world.getEntitiesOfClass(Entity.class, box, ent -> true)) {
            if (e == self) continue;
            if (!includePlayers && e instanceof ServerPlayerEntity) continue;

            Vector3d ep = e.position();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("entity_id", e.getId());
            info.put("type", e.getType().getRegistryName().toString());
            info.put("name", e.getName().getString());
            info.put("position", mapOf("x", ep.x, "y", ep.y, "z", ep.z));
            info.put("distance", pos.distanceTo(ep));
            if (e instanceof LivingEntity) {
                info.put("health", ((LivingEntity) e).getHealth());
            }
            found.add(info);
        }

        return mapOf("entities", found, "count", found.size());
    }

    private Object senseGetInventory(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        ServerPlayerEntity player = bot.getPlayer();

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            ItemStack stack = player.inventory.getItem(i);
            if (stack.isEmpty()) continue;

            items.add(mapOf(
                    "slot", i,
                    "item", stack.getItem().getRegistryName().toString(),
                    "count", stack.getCount(),
                    "display_name", stack.getDisplayName().getString()));
        }

        return mapOf(
                "items", items,
                "selected_slot", player.inventory.selected);
    }

    // ========================================================================
    // action
    // ========================================================================

    private Object actionMoveTo(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        JsonNode t = requireNode(p, "target");
        double x = t.path("x").asDouble();
        double y = t.path("y").asDouble();
        double z = t.path("z").asDouble();

        ServerPlayerEntity player = bot.getPlayer();

        // Teleport directly (Phase 1 simplification, pathfinding comes later)
        server.execute(() -> player.teleportTo(
                (ServerWorld) player.level, x, y, z, player.yRot, player.xRot));

        return mapOf("ok", true, "position", mapOf("x", x, "y", y, "z", z));
    }

    private Object actionLookAt(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        JsonNode t = requireNode(p, "target");
        double x = t.path("x").asDouble();
        double y = t.path("y").asDouble();
        double z = t.path("z").asDouble();

        ServerPlayerEntity player = bot.getPlayer();
        Vector3d eye = player.getEyePosition(1.0f);
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;

        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        server.execute(() -> {
            player.yRot = yaw;
            player.xRot = pitch;
            player.setYHeadRot(yaw);
        });

        return mapOf("ok", true, "yaw", yaw, "pitch", pitch);
    }

    private Object actionMineBlock(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        JsonNode pos = requireNode(p, "pos");
        BlockPos bp = new BlockPos(
                pos.path("x").asInt(), pos.path("y").asInt(), pos.path("z").asInt());

        ServerPlayerEntity player = bot.getPlayer();
        World world = player.level;

        // Look at the block first, then mine
        actionLookAt(p);

        server.execute(() -> {
            // Break the block directly (Phase 1 simplification, no dig animation or drop logic)
            world.destroyBlock(bp, true, player, 0);
        });

        return mapOf("ok", true, "broken", mapOf("x", bp.getX(), "y", bp.getY(), "z", bp.getZ()));
    }

    private Object actionPlaceBlock(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        JsonNode pos = requireNode(p, "pos");
        BlockPos bp = new BlockPos(
                pos.path("x").asInt(), pos.path("y").asInt(), pos.path("z").asInt());

        ServerPlayerEntity player = bot.getPlayer();
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            throw new RpcException(-32010, "Bot main hand is empty");
        }

        // Phase 1 simplification: only placeable block items supported
        throw new RpcException(-32601, "placeBlock not implemented in Phase 1");
    }

    private Object actionAttack(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        int entityId = requireNode(p, "entity_id").asInt();

        ServerPlayerEntity player = bot.getPlayer();
        Entity target = player.level.getEntity(entityId);
        if (target == null) {
            throw new RpcException(-32020, "Entity not found: " + entityId);
        }

        server.execute(() -> player.attack(target));

        return mapOf("ok", true, "attacked", entityId);
    }

    private Object actionUseItem(JsonNode p) {
        BotHandle bot = findBot(require(p, "bot_id"));
        ServerPlayerEntity player = bot.getPlayer();

        // Phase 1 simplification: use main hand item (eat, drink potion, etc.)
        throw new RpcException(-32601, "useItem not implemented in Phase 1");
    }

    // ========================================================================
    // server
    // ========================================================================

    private Object serverGetStatus() {
        return mapOf(
                "version", server.getServerVersion(),
                "online_players", server.getPlayerCount(),
                "max_players", server.getMaxPlayers(),
                "tps", 20.0,
                "memory_used", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                "memory_max", Runtime.getRuntime().maxMemory());
    }

    // ========================================================================
    // bot
    // ========================================================================

    private Object botList() {
        List<String> names = new ArrayList<>(BotManager.getInstance().list());
        return mapOf("bots", names, "count", names.size());
    }

    private Object botSpawn(JsonNode p) {
        String name = require(p, "bot_id");
        JsonNode pos = p.path("position");
        Vector3d v = new Vector3d(
                pos.path("x").asDouble(0),
                pos.path("y").asDouble(64),
                pos.path("z").asDouble(0));

        BotHandle handle = BotManager.getInstance().spawn(name, v);
        return mapOf("ok", true, "bot_id", handle.getName());
    }

    private Object botDespawn(JsonNode p) {
        String name = require(p, "bot_id");
        BotManager.getInstance().despawn(name);
        return mapOf("ok", true);
    }

    // ========================================================================
    // helpers
    // ========================================================================

    private BotHandle findBot(String name) {
        try {
            return BotManager.getInstance().find(name);
        } catch (IllegalArgumentException e) {
            throw new RpcException(-32001, e.getMessage());
        }
    }

    private static String require(JsonNode p, String field) {
        JsonNode n = p.get(field);
        if (n == null || n.isNull()) {
            throw new RpcException(-32602, "Missing required param: " + field);
        }
        return n.isTextual() ? n.asText() : n.toString();
    }

    private static JsonNode requireNode(JsonNode p, String field) {
        JsonNode n = p.get(field);
        if (n == null || n.isNull()) {
            throw new RpcException(-32602, "Missing required param: " + field);
        }
        return n;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
