package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.type.Direction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates the values that several areas of the protocol share between the Java Edition protocol and the
 * MCProtocolLib releases from 1.16.5 until 1.17.1, which keep them unchanged: hands, block faces, item stacks and the
 * registry keys of entity types. The translations of this segment and of the segments extending it use it instead of
 * translating these values on their own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValueTranslator {
	private static final AdventureText TEXT = new AdventureText(TranslatableComponent::args);

	/**
	 * Flattens a component of the chain's releases to plain text, reading a translation's arguments through
	 * {@code args()}, which every Adventure 4 release of the chain has.
	 *
	 * @param component component to flatten
	 * @return the component as plain text
	 */
	public static @NotNull String plain(@NotNull Component component) {
		return TEXT.plain(component);
	}

	/**
	 * The entity types whose constants these releases name differently from the game's registry: thrown items, primed
	 * TNT and the minecarts that carry something.
	 */
	private static final Map<EntityType, String> REGISTRY_NAMES = Map.ofEntries(
			Map.entry(EntityType.THROWN_EGG, "egg"),
			Map.entry(EntityType.THROWN_ENDERPEARL, "ender_pearl"),
			Map.entry(EntityType.THROWN_EXP_BOTTLE, "experience_bottle"),
			Map.entry(EntityType.THROWN_POTION, "potion"),
			Map.entry(EntityType.PRIMED_TNT, "tnt"),
			Map.entry(EntityType.MINECART_CHEST, "chest_minecart"),
			Map.entry(EntityType.MINECART_COMMAND_BLOCK, "command_block_minecart"),
			Map.entry(EntityType.MINECART_FURNACE, "furnace_minecart"),
			Map.entry(EntityType.MINECART_HOPPER, "hopper_minecart"),
			Map.entry(EntityType.MINECART_SPAWNER, "spawner_minecart"),
			Map.entry(EntityType.MINECART_TNT, "tnt_minecart")
	);

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
	 * Translates a block face to the library's.
	 *
	 * @param face the protocol's direction
	 * @return the library's block face
	 */
	public static @NotNull BlockFace face(@NotNull Direction face) {
		return switch (face) {
			case DOWN -> BlockFace.DOWN;
			case UP -> BlockFace.UP;
			case NORTH -> BlockFace.NORTH;
			case SOUTH -> BlockFace.SOUTH;
			case WEST -> BlockFace.WEST;
			case EAST -> BlockFace.EAST;
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
	 * Returns the registry key of the library's entity type, which the library names as the game's registry does but
	 * for the types it names after older names.
	 *
	 * @param type the library's entity type
	 * @return the type's key in the {@code minecraft} namespace
	 */
	public static @NotNull EntityTypeKey entityType(@NotNull EntityType type) {
		return EntityTypeKey.minecraft(REGISTRY_NAMES.getOrDefault(type, type.name().toLowerCase(Locale.ROOT)));
	}
}
