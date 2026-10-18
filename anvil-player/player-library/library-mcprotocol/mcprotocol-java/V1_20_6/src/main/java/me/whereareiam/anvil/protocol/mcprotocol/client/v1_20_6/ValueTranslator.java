package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Translates the values that several areas of the protocol share between the Java Edition protocol and the
 * MCProtocolLib releases from 1.20.5 on, which keep them unchanged: hands, block faces, item stacks, vectors and the
 * registry keys of entity types. The translations of this segment and of the segments extending it use it instead of
 * translating these values on their own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValueTranslator {
	private static final AdventureText TEXT = new AdventureText(TranslatableComponent::arguments);

	/**
	 * Flattens a component of the chain's releases to plain text, reading a translation's arguments through
	 * {@code arguments()}, which Adventure 5 keeps.
	 *
	 * @param component component to flatten
	 * @return the component as plain text
	 */
	public static @NotNull String plain(@NotNull Component component) {
		return TEXT.plain(component);
	}

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
			org.geysermc.mcprotocollib.protocol.data.game.item.@Nullable ItemStack stack
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
			org.geysermc.mcprotocollib.protocol.data.game.item.@Nullable ItemStack @NotNull [] stacks
	) {
		List<ItemStack> items = new ArrayList<>(stacks.length);
		for (var stack : stacks) items.add(item(stack));
		return Collections.unmodifiableList(items);
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
