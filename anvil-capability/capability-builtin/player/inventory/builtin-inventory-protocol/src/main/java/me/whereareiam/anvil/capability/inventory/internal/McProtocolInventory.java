package me.whereareiam.anvil.capability.inventory.internal;

import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.InventoryConnection;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Inventory capability backed by MCProtocol operations and packet snapshots.
 */
public final class McProtocolInventory implements Inventory {
	private final InventoryConnection connection;
	private final AtomicReference<InventorySnapshot> snapshot = new AtomicReference<>(
			InventorySnapshot.builder().containerId(0).stateId(0).build()
	);

	public McProtocolInventory(InventoryConnection connection) {
		this.connection = connection;
		connection.observe(snapshot::set);
	}

	@Override
	public @NotNull InventorySnapshot snapshot() {
		return snapshot.get();
	}

	@Override
	public @NotNull InventorySnapshot matching(
			@NotNull Predicate<InventorySnapshot> predicate,
			@NotNull Duration timeout
	) {
		connection.await(() -> predicate.test(snapshot.get()), "receive matching inventory", timeout);
		return snapshot.get();
	}

	@Override
	public void selectSlot(int slot) {
		if (slot < 0 || slot > 8)
			throw new IllegalArgumentException("Hotbar slot must be in range 0..8");
		connection.select(slot);
	}

	@Override
	public void click(int slot, @NotNull InventoryClick click, int button) {
		connection.click(slot, click, button);
	}
}
