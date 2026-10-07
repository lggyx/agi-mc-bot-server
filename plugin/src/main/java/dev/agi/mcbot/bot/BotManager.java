package dev.agi.mcbot.bot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot 管理器。
 *
 * 负责 Bot 实体的创建、查找、销毁。
 *
 * 注意：当前为骨架实现，FakePlayer 的具体构造方式待 Phase 1 确定。
 */
public class BotManager {
    private static BotManager instance;

    private final MinecraftServer server;
    private final Map<String, BotHandle> bots = new ConcurrentHashMap<>();

    private BotManager(MinecraftServer server) {
        this.server = server;
    }

    public static BotManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BotManager 尚未初始化");
        }
        return instance;
    }

    public static void init(MinecraftServer server) {
        instance = new BotManager(server);
    }

    /**
     * 生成一个 Bot。
     *
     * @param name 玩家名（会显示在玩家列表）
     * @param pos  出生位置
     * @return Bot 句柄
     */
    public synchronized BotHandle spawn(String name, Vec3d pos) {
        if (bots.containsKey(name)) {
            throw new IllegalArgumentException("Bot 已存在: " + name);
        }

        // TODO Phase 1: 实现 FakePlayer 构造
        // 方案 A: 构造 ServerPlayerEntity，绕过网络层
        // 方案 B: 自写轻量实体
        //
        // 当前先返回占位句柄，便于打通 HTTP -> RPC 链路
        BotHandle handle = new BotHandle(name, pos);
        bots.put(name, handle);

        AgiMcBotMod.LOGGER.info("[AGI-MC] Bot 已生成: {} @ {}", name, pos);
        return handle;
    }

    public Optional<BotHandle> get(String name) {
        return Optional.ofNullable(bots.get(name));
    }

    public List<BotHandle> list() {
        return new ArrayList<>(bots.values());
    }

    public synchronized void despawn(String name) {
        BotHandle handle = bots.remove(name);
        if (handle != null) {
            // TODO Phase 1: 移除实体
            AgiMcBotMod.LOGGER.info("[AGI-MC] Bot 已移除: {}", name);
        }
    }

    public synchronized void despawnAll() {
        for (String name : new ArrayList<>(bots.keySet())) {
            despawn(name);
        }
    }

    /**
     * Bot 句柄。
     *
     * 封装 Bot 实体引用与元数据。
     */
    public static class BotHandle {
        private final String name;
        private final Vec3d spawnPos;
        private ServerPlayerEntity entity;
        private long lastActionTime = System.currentTimeMillis();

        public BotHandle(String name, Vec3d spawnPos) {
            this.name = name;
            this.spawnPos = spawnPos;
        }

        public String getName() {
            return name;
        }

        public Vec3d getSpawnPos() {
            return spawnPos;
        }

        public ServerPlayerEntity getEntity() {
            return entity;
        }

        public void setEntity(ServerPlayerEntity entity) {
            this.entity = entity;
        }

        public boolean isOnline() {
            return entity != null && !entity.isRemoved();
        }

        public void touch() {
            this.lastActionTime = System.currentTimeMillis();
        }

        public long getIdleMs() {
            return System.currentTimeMillis() - lastActionTime;
        }
    }
}
