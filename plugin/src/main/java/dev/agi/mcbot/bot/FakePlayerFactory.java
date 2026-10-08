package dev.agi.mcbot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.play.ServerPlayNetHandler;
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
 * This mirrors what net.minecraftforge.common.util.FakePlayer does internally:
 *
 *  1. Build a PlayerInteractionManager for the world.
 *  2. Construct ServerPlayerEntity(server, world, profile, interactionManager).
 *  3. Install a ServerPlayNetHandler backed by a dummy NetworkManager so that
 *     the entity has a non-null `connection` field. Without this, the entity
 *     tracker (TrackedEntity) NPEs on the first world tick and crashes the
 *     server.
 *  4. Set position / rotation.
 *  5. Add to the world with addFreshEntity.
 *
 * We deliberately do NOT call PlayerList.placeNewPlayer(): it sends join
 * packets, registers the player in the player list and requires a real
 * NetworkManager. A bot is a server-side entity only.
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

            // 3. Give it a connection backed by a dummy NetworkManager.
            //    ServerPlayNetHandler's constructor also assigns player.connection.
            NetworkManager dummy = new NetworkManager(PacketDirection.CLIENTBOUND);
            new ServerPlayNetHandler(server, dummy, player);

            // 4. Position and rotation
            player.setPos(pos.x, pos.y, pos.z);
            player.yRot = 0f;
            player.xRot = 0f;
            player.setYHeadRot(0f);

            // 5. Add to world so it is tracked and ticked
            world.addFreshEntity(player);

            LOGGER.info("[AGI-MC] FakePlayer created: {} id={} at ({}, {}, {})",
                    profile.getName(), player.getId(), pos.x, pos.y, pos.z);

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
