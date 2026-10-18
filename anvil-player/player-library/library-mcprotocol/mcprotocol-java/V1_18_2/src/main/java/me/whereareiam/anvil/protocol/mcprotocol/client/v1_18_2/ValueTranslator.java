package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Translates the values that several areas of the protocol share between the Java Edition protocol and the
 * MCProtocolLib releases from 1.18.2 until 1.19.4, which keep them unchanged: hands, block faces, item stacks and the
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
	public static @NotNull Direction direction(@NotNull me.whereareiam.anvil.protocol.java.api.type.Direction face) {
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
	 * Translates the library's item stack.
	 *
	 * @param stack the library's stack, or {@code null} for an empty slot
	 * @return the protocol's stack, or {@code null} for an empty slot or a stack of no items
	 */
	public static @Nullable ItemStack item(
			com.github.steveice10.mc.protocol.data.game.entity.metadata.@Nullable ItemStack stack
	) {
		if (stack == null || stack.getAmount() <= 0) return null;

		return new ItemStack(stack.getId(), stack.getAmount());
	}

	/**
	 * Translates the library's item stacks, keeping their order and their empty slots.
	 *
	 * @param stacks the library's stacks
	 * @return the protocol's stacks, {@code null} for each empty slot
	 */
	public static @NotNull List<@Nullable ItemStack> items(
			com.github.steveice10.mc.protocol.data.game.entity.metadata.@Nullable ItemStack @NotNull [] stacks
	) {
		List<ItemStack> items = new ArrayList<>(stacks.length);
		for (var stack : stacks) items.add(item(stack));
		return Collections.unmodifiableList(items);
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
