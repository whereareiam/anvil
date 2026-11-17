package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server tells the player what its game mode lets it do: whether it may fly and flies now, and how fast it flies
 * and walks.
 */
@Value
@Builder
public class ClientboundPlayerAbilities implements ClientboundPacket {
	/**
	 * Whether the player cannot be hurt.
	 */
	boolean invulnerable;
	/**
	 * Whether the player flies now.
	 */
	boolean flying;
	/**
	 * Whether the player may start flying.
	 */
	boolean mayFly;
	/**
	 * Whether the player builds without using up items, as in creative mode.
	 */
	boolean instabuild;
	/**
	 * The speed the player flies at; 0.05 by default.
	 */
	float flyingSpeed;
	/**
	 * The speed the player walks at; 0.1 by default.
	 */
	float walkingSpeed;
}
