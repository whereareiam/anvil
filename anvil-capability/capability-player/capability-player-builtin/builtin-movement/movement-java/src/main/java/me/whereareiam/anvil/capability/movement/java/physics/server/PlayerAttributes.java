package me.whereareiam.anvil.capability.movement.java.physics.server;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.Attribute;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.AttributeSupplier;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.LivingAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * The attributes only a player has, as vanilla's {@code Attributes} registers them, and the attributes a player starts
 * with ({@code Player.createAttributes}): every living entity's, a movement speed of {@code 0.1F}, and its own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlayerAttributes {
	/**
	 * {@code sneaking_speed}: the factor of the input while the player crouches, {@code 0.3}.
	 */
	public static final @NotNull Attribute SNEAKING_SPEED =
			Attribute.of("minecraft:sneaking_speed", "1.21", 0.3, 0.0, 1.0);
	/**
	 * {@code submerged_mining_speed}: how fast the player mines under water, which movement does not read.
	 */
	public static final @NotNull Attribute SUBMERGED_MINING_SPEED =
			Attribute.of("minecraft:submerged_mining_speed", "1.21", 0.2, 0.0, 20.0);

	/**
	 * Returns the attributes a new player has and their bases.
	 *
	 * @return the player's attribute supplier
	 */
	public static @NotNull AttributeSupplier supplier() {
		return AttributeSupplier.living()
				.with(LivingAttributes.MOVEMENT_SPEED, 0.1F)
				.with(SNEAKING_SPEED)
				.with(SUBMERGED_MINING_SPEED);
	}
}
