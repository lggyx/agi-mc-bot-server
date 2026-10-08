package dev.agi.mcbot.bot;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.vector.Vector3d;

/**
 * Bot handle, wraps a ServerPlayerEntity.
 */
public class BotHandle {
    private final String name;
    private final ServerPlayerEntity player;

    public BotHandle(String name, ServerPlayerEntity player) {
        this.name = name;
        this.player = player;
    }

    public String getName() {
        return name;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public Vector3d getPosition() {
        return player.position();
    }

    public float getHealth() {
        return player.getHealth();
    }

    public int getFoodLevel() {
        return player.getFoodData().getFoodLevel();
    }

    public boolean isAlive() {
        return player.isAlive();
    }
}
