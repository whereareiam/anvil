package me.whereareiam.anvil.edition.java.protocol.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The client accepts a teleport of the server ({@link ClientboundPlayerPosition}) by its ID, with the position and view
 * the teleport left the player at, as the vanilla client does when it handles the teleport
 * ({@code ClientPacketListener.handleMovePlayer}); until the server sees it, the server ignores the player's movement.
 * Releases before Minecraft 26.3 carry the ID alone, and their client follows the acceptance with its position and view
 * in a movement packet.
 *
 * <pre>{@code
 * protocol.send(ServerboundAcceptTeleportation.builder().id(2).position(new Vec3d(4.5, 64.0, 10.5)).yaw(90.0F).build());
 * }</pre>
 */
@Value
@Builder
public class ServerboundAcceptTeleportation implements ServerboundPacket {
	/**
	 * The ID of the teleport the client accepts.
	 */
	int id;
	/**
	 * The position of the player's feet the teleport left it at.
	 */
	@Builder.Default
	@NotNull Vec3d position = Vec3d.ZERO;
	/**
	 * The horizontal view direction in degrees the teleport left the player with.
	 */
	float yaw;
	/**
	 * The vertical view direction in degrees the teleport left the player with.
	 */
	float pitch;
}
