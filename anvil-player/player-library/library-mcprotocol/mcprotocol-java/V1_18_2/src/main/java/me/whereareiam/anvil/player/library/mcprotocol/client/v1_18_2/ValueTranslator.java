package me.whereareiam.anvil.player.library.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityTypeKey;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Translates the values that several areas of the protocol share between the Java Edition protocol and the
 * MCProtocolLib releases from 1.18.2 until 1.19.4, which keep them unchanged: hands, block faces, game modes and the
 * registry keys of entity types. The translations of this segment and of the segments extending it use it instead of
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
	 * Returns the registry key of the library's entity type, which the library names as the game's registry does.
	 *
	 * @param type the library's entity type
	 * @return the type's key in the {@code minecraft} namespace
	 */
	public static @NotNull EntityTypeKey entityType(@NotNull EntityType type) {
		return EntityTypeKey.minecraft(type.name().toLowerCase(Locale.ROOT));
	}

	/**
	 * Translates the library's game mode. MCProtocolLib 1.18.2 also has an {@code UNKNOWN} mode, which names only the
	 * absent previous game mode of a login or respawn and never the player's current one.
	 *
	 * @param mode the library's game mode of the player
	 * @return the protocol's game mode
	 * @throws IllegalArgumentException when the mode names no game mode
	 */
	public static me.whereareiam.anvil.edition.java.protocol.type.player.@NotNull GameMode gameMode(@NotNull GameMode mode) {
		return switch (mode) {
			case SURVIVAL -> me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SURVIVAL;
			case CREATIVE -> me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.CREATIVE;
			case ADVENTURE -> me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.ADVENTURE;
			case SPECTATOR -> me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SPECTATOR;
			default -> throw new IllegalArgumentException("The server named no game mode for the player");
		};
	}
}
