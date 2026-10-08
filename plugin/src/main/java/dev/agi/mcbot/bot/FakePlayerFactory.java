package dev.agi.mcbot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * FakePlayer factory for Forge 1.16.5.
 *
 * ServerPlayerEntity constructor (official mappings):
 *   ServerPlayerEntity(MinecraftServer, ServerWorld, GameProfile, PlayerInteractionManager)
 *
 * After construction we must:
 *  1. set position / rotation
 *  2. add to world via addFreshEntity (addEntity/spawnEntity do not exist in 1.16.5)
 *  3. register with PlayerList via placeNewPlayer(NetworkManager, ServerPlayerEntity)
 *
 * Note: a directly constructed ServerPlayerEntity has connection == null.
 * Any code path that sends a packet will NPE, so we only use server-side logic.
 */
public final class FakePlayerFactory {
    private static final Logger LOGGER = LogManager.getLogger();

    private FakePlayerFactory() {}

    public static ServerPlayerEntity create(MinecraftServer server, ServerWorld world,
                                            GameProfile profile, Vector3d pos) {
        try {
            // 1. Interaction manager (game mode, digging progress, etc.)
            PlayerInteractionManager interactionManager = new PlayerInteractionManager(world);

            // 2. Construct the player entity
            ServerPlayerEntity player =
                    new ServerPlayerEntity(server, world, profile, interactionManager);

            // 3. Position and rotation
            player.setPos(pos.x, pos.y, pos.z);
            player.yRot = 0f;
            player.xRot = 0f;
            player.setYHeadRot(0f);

            // 4. Add to world (does not trigger the real player connection flow)
            world.addFreshEntity(player);

            // 5. Register with PlayerList so /list, commands and events work
            server.getPlayerList().placeNewPlayer(null, player);

            LOGGER.info("[AGI-MC] FakePlayer created: {} id={}",
                    profile.getName(), player.getId());

            return player;
        } catch (Exception e) {
            LOGGER.error("[AGI-MC] FakePlayer creation failed", e);
            throw new RuntimeException("Failed to create FakePlayer", e);
        }
    }

    /**
     * Derive a stable UUID from a bot name so the same name always maps to the same UUID.
     */
    public static UUID deriveUuid(String name) {
        return UUID.nameUUIDFromBytes(("agimcbot:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
