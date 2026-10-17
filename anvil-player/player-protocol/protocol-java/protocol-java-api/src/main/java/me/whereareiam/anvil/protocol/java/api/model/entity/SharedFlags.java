package me.whereareiam.anvil.protocol.java.api.model.entity;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The flags every entity keeps in its first entity data value, a byte with one bit per flag.
 */
@Value
@Builder
public class SharedFlags implements EntityDataValue {
	private static final int ON_FIRE = 0x01;
	private static final int SNEAKING = 0x02;
	private static final int SPRINTING = 0x08;
	private static final int SWIMMING = 0x10;
	private static final int INVISIBLE = 0x20;
	private static final int GLOWING = 0x40;
	private static final int GLIDING = 0x80;

	/**
	 * Whether the entity is on fire.
	 */
	boolean onFire;
	/**
	 * Whether the entity, usually a player, sneaks.
	 */
	boolean sneaking;
	/**
	 * Whether the entity sprints.
	 */
	boolean sprinting;
	/**
	 * Whether the entity swims.
	 */
	boolean swimming;
	/**
	 * Whether the entity is invisible.
	 */
	boolean invisible;
	/**
	 * Whether the entity glows, outlined through walls.
	 */
	boolean glowing;
	/**
	 * Whether the entity glides with elytra.
	 */
	boolean gliding;

	/**
	 * Reads the flags from the byte the server sends, whose bits every release since Minecraft 1.13 keeps.
	 *
	 * @param flags the entity data byte
	 * @return the flags it sets
	 */
	public static @NotNull SharedFlags of(byte flags) {
		return SharedFlags.builder()
				.onFire((flags & ON_FIRE) != 0)
				.sneaking((flags & SNEAKING) != 0)
				.sprinting((flags & SPRINTING) != 0)
				.swimming((flags & SWIMMING) != 0)
				.invisible((flags & INVISIBLE) != 0)
				.glowing((flags & GLOWING) != 0)
				.gliding((flags & GLIDING) != 0)
				.build();
	}
}
