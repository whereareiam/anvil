package me.whereareiam.anvil.capability.inventory.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.inventory.InventoryConnection;
import me.whereareiam.anvil.capability.inventory.InventoryOperations;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Maps the scoped typed channel to the inventory implementation's own connection contract.
 */
@RequiredArgsConstructor
final class ChannelInventoryConnection implements InventoryConnection {
	private final @NotNull ProtocolPlayerCapabilityContext context;

	public void select(int slot) { context.channel().request(InventoryOperations.SELECT, new SlotSelection(slot)); }
	public void click(int slot, @NotNull InventoryClick click, int button) {
		context.channel().request(InventoryOperations.CLICK, new InventoryAction(slot, click, button));
	}
	public void observe(@NotNull Consumer<InventorySnapshot> observer) {
		context.channel().subscribe(InventoryOperations.CHANGED, observer);
	}

	public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
		context.channel().await(condition, description, timeout);
	}
}
