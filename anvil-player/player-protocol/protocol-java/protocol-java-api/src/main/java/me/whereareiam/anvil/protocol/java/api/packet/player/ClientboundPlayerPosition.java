package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The server places the player and turns its view, which the player's library accepts on its own.
 */
@Value
@Builder
public class ClientboundPlayerPosition implements ClientboundPacket {
	/**
	 * The position of the player's feet, each coordinate named in {@link #relatives} added to the current one.
	 */
	@NotNull Vec3d position;
	/**
	 * The horizontal view direction in degrees, added to the current one when {@link #relatives} names it.
	 */
	float yaw;
	/**
	 * The vertical view direction in degrees, added to the current one when {@link #relatives} names it.
	 */
	float pitch;
	/**
	 * The coordinates and angles that are relative to the current ones.
	 */
	@NotNull Set<Relative> relatives;
}
