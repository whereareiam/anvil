package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityTypeKey;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Translates the values that several areas of the protocol share between the Java Edition protocol and the
 * MCProtocolLib releases from 1.20.5 on, which keep them unchanged: hands, block faces, vectors and the registry keys
 * of entity types. The translations of this segment and of the segments extending it use it instead of
 * translating these values on their own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValueTranslator {

	/**
	 * Translates a hand to the library's.
	 *
	 * @param hand the protocol's hand
	 * @return the library's hand
	 */
	public static @NotNull Hand hand(@NotNull InteractionHand hand) {
		return switch (hand) {
			case MAIN_HAND -> Hand.MAIN_HAND;
			case OFF_HAND -> Hand.OFF_HAND;
		};
	}

	/**
	 * Translates a block face to the library's direction.
	 *
	 * @param face the protocol's direction
	 * @return the library's direction
	 */
	public static @NotNull Direction direction(@NotNull me.whereareiam.anvil.edition.java.protocol.type.interaction.Direction face) {
		return switch (face) {
			case DOWN -> Direction.DOWN;
			case UP -> Direction.UP;
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
			case EAST -> Direction.EAST;
		};
	}

	/**
	 * Translates the library's vector.
	 *
	 * @param vector the library's vector
	 * @return the protocol's vector
	 */
	public static @NotNull Vec3d vec3d(@NotNull Vector3d vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}

	/**
	 * Returns the registry key of the library's entity type, which the library names as the game's registry does.
	 *
	 * @param type the library's entity type
	 * @return the type's key in the {@code minecraft} namespace
	 */
	public static @NotNull EntityTypeKey entityType(@NotNull EntityType type) {
		return EntityTypeKey.minecraft(type.name().toLowerCase(Locale.ROOT));
	}
}
