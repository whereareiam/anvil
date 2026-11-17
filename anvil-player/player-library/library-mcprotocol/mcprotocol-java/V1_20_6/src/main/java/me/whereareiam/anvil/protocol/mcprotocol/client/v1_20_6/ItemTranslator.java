package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Translates the item stacks of the MCProtocolLib releases from 1.20.5 on, which keep a stack's custom name, item name
 * and lore as data components the library reads into Adventure components. Each chain's releases reach the components
 * differently, so the segments' {@link ComponentItemTranslator} reads them and this class translates them; the
 * adapter of each segment hands its one item translator to every translation that reads stacks.
 */
public abstract class ItemTranslator {
	private final ComponentTranslator text;

	/**
	 * Creates the item translation of a release.
	 *
	 * @param text translates the release's text components
	 */
	protected ItemTranslator(@NotNull ComponentTranslator text) {
		this.text = text;
	}

	/**
	 * Translates the library's item stack.
	 *
	 * @param stack the library's stack, or {@code null} for an empty slot
	 * @return the protocol's stack, or {@code null} for an empty slot or a stack of no items
	 */
	public final @Nullable ItemStack item(org.geysermc.mcprotocollib.protocol.data.game.item.@Nullable ItemStack stack) {
		if (stack == null || stack.getAmount() <= 0) return null;

		ItemStack.ItemStackBuilder item = ItemStack.builder().id(stack.getId()).count(stack.getAmount());
		Component customName = customName(stack);
		if (customName != null) item.customName(text.translate(customName));
		Component itemName = itemName(stack);
		if (itemName != null) item.itemName(text.translate(itemName));
		for (Component line : lore(stack))
			item.lore(text.translate(line));
		return item.build();
	}

	/**
	 * Translates the library's item stacks, keeping their order and their empty slots.
	 *
	 * @param stacks the library's stacks
	 * @return the protocol's stacks, {@code null} for each empty slot
	 */
	public final @NotNull List<@Nullable ItemStack> items(
			org.geysermc.mcprotocollib.protocol.data.game.item.@Nullable ItemStack @NotNull [] stacks
	) {
		List<ItemStack> items = new ArrayList<>(stacks.length);
		for (var stack : stacks) items.add(item(stack));
		return Collections.unmodifiableList(items);
	}

	/**
	 * Reads a stack's custom name.
	 *
	 * @param stack the library's stack
	 * @return the name, or {@code null} when the stack has none
	 */
	protected abstract @Nullable Component customName(org.geysermc.mcprotocollib.protocol.data.game.item.@NotNull ItemStack stack);

	/**
	 * Reads a stack's item name.
	 *
	 * @param stack the library's stack
	 * @return the name, or {@code null} when the stack has none
	 */
	protected abstract @Nullable Component itemName(org.geysermc.mcprotocollib.protocol.data.game.item.@NotNull ItemStack stack);

	/**
	 * Reads a stack's lore.
	 *
	 * @param stack the library's stack
	 * @return its lines in order, empty when the stack has none
	 */
	protected abstract @NotNull List<Component> lore(org.geysermc.mcprotocollib.protocol.data.game.item.@NotNull ItemStack stack);
}
