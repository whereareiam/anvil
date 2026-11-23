package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reads the item components of the MCProtocolLib releases 1.20.6 and 1.21.1, which keep a stack's components in its
 * {@code getDataComponents()} and name their types in {@code DataComponentType}.
 */
public final class ComponentItemTranslator extends ItemTranslator {
	/**
	 * Creates the item translation of the release.
	 *
	 * @param text translates the release's text components
	 */
	public ComponentItemTranslator(@NotNull ComponentTranslator text) {
		super(text);
	}

	@Override
	protected @Nullable Component customName(@NotNull ItemStack stack) {
		DataComponents components = stack.getDataComponents();
		return components == null ? null : components.get(DataComponentType.CUSTOM_NAME);
	}

	@Override
	protected @Nullable Component itemName(@NotNull ItemStack stack) {
		DataComponents components = stack.getDataComponents();
		return components == null ? null : components.get(DataComponentType.ITEM_NAME);
	}

	@Override
	protected @NotNull List<Component> lore(@NotNull ItemStack stack) {
		DataComponents components = stack.getDataComponents();
		List<Component> lore = components == null ? null : components.get(DataComponentType.LORE);
		return lore == null ? List.of() : lore;
	}
}
