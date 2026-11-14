package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The server tells the player its world's spawn point, where the compass points and where a player without a bed or
 * respawn anchor of its own respawns, when the player joins and whenever the spawn point moves.
 */
@Value
@Builder
public class ClientboundSetDefaultSpawnPosition implements ClientboundPacket {
	/**
	 * The key of the level the spawn point is in, such as {@code minecraft:overworld}, or {@code null} before
	 * Minecraft 1.21.9, whose client takes the level it is in.
	 */
	@Nullable String dimension;
	/**
	 * The block of the spawn point.
	 */
	@NotNull BlockPosition position;
	/**
	 * The direction a player who spawns there faces, in degrees; releases before Minecraft 1.17 send none and report 0.
	 */
	float yaw;
	/**
	 * The pitch a player who spawns there looks at, in degrees; releases before Minecraft 1.21.9 send none and report
	 * 0.
	 */
	float pitch;
}
