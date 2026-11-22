package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ItemTranslator;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reads the item components of the MCProtocolLib releases from 1.21.11 on, which keep a stack's components in its
 * {@code getDataComponentsPatch()} and name their types in {@code DataComponentTypes}.
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
		DataComponents components = stack.getDataComponentsPatch();
		return components == null ? null : components.get(DataComponentTypes.CUSTOM_NAME);
	}

	@Override
	protected @Nullable Component itemName(@NotNull ItemStack stack) {
		DataComponents components = stack.getDataComponentsPatch();
		return components == null ? null : components.get(DataComponentTypes.ITEM_NAME);
	}

	@Override
	protected @NotNull List<Component> lore(@NotNull ItemStack stack) {
		DataComponents components = stack.getDataComponentsPatch();
		List<Component> lore = components == null ? null : components.get(DataComponentTypes.LORE);
		return lore == null ? List.of() : lore;
	}
}
