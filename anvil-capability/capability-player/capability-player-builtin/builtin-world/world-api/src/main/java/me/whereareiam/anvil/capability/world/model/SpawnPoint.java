package me.whereareiam.anvil.capability.world.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The world's spawn point as the client was told it last: where its compass points and where a player without a bed or
 * respawn anchor of its own respawns.
 */
@Value
@Builder(toBuilder = true)
public class SpawnPoint {
	/**
	 * The key of the spawn point's level, such as {@code minecraft:overworld}. Releases before Minecraft 1.21.9 do not
	 * send it, and the client takes the level it was in when the server told it.
	 */
	@NotNull String dimension;
	/**
	 * The east-west coordinate of the spawn point's block.
	 */
	int x;
	/**
	 * The height of the spawn point's block.
	 */
	int y;
	/**
	 * The north-south coordinate of the spawn point's block.
	 */
	int z;
	/**
	 * The direction a player who spawns there faces, in degrees; {@code 0} before Minecraft 1.17, which does not send
	 * it.
	 */
	float yaw;
	/**
	 * The pitch a player who spawns there looks at, in degrees; {@code 0} before Minecraft 1.21.9, which does not send
	 * it.
	 */
	float pitch;
}
