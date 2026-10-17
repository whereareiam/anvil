package me.whereareiam.anvil.capability.inventory.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.inventory.InventoryBinding;
import me.whereareiam.anvil.capability.inventory.InventoryProvider;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs the inventory capability into MCProtocolLib workers through the segment selected for the worker's
 * release: each player's binding takes the {@link InventoryPackets} adapter from the worker, which selected the one
 * inventory segment for the loaded release and verified its linkage.
 */
public final class McProtocolInventoryExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return InventoryProvider.ID;
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	/**
	 * Installs the inventory operations and packet listener for one player.
	 *
	 * @throws AdapterUnavailableException when no inventory segment serves the worker's release or the segment does
	 * not link against it
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		InventoryPackets<?> packets = player.adapter(InventoryPackets.class);
		return new InventoryBinding<>(packets).bind(player, operations);
	}
}
