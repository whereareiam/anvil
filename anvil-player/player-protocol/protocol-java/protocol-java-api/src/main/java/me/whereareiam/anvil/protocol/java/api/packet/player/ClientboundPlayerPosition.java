package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The server places the player, turns its view and sets its velocity, which the player's library accepts on its own.
 * Releases before Minecraft 1.21.2 carry no velocity: their client keeps the velocity along each relative coordinate
 * and stops it along each absolute one, which they report as a zero velocity that is relative along each relative
 * coordinate.
 */
@Value
@Builder
public class ClientboundPlayerPosition implements ClientboundPacket {
	/**
	 * The position of the player's feet, each coordinate named in {@link #relatives} added to the current one.
	 */
	@NotNull Vec3d position;
	/**
	 * The player's velocity in blocks per tick, each component named in {@link #relatives} added to the current one.
	 */
	@Builder.Default
	@NotNull Vec3d deltaMovement = Vec3d.ZERO;
	/**
	 * The horizontal view direction in degrees, added to the current one when {@link #relatives} names it.
	 */
	float yaw;
	/**
	 * The vertical view direction in degrees, added to the current one when {@link #relatives} names it.
	 */
	float pitch;
	/**
	 * The coordinates, angles and velocity components that are relative to the current ones.
	 */
	@NotNull Set<Relative> relatives;
}
