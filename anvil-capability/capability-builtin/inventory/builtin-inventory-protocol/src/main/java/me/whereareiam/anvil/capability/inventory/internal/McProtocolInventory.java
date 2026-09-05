package me.whereareiam.anvil.capability.inventory.internal;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Inventory capability backed by MCProtocol operations and packet snapshots.
 */
final class McProtocolInventory implements Inventory {
	private final ProtocolPlayerConnection connection;
	private final AtomicReference<InventorySnapshot> snapshot = new AtomicReference<>(
			InventorySnapshot.builder().containerId(0).stateId(0).build()
	);

	McProtocolInventory(ProtocolPlayerConnection connection) {
		this.connection = connection;
		connection.subscribe("inventory.changed", this::update);
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
		connection.execute("inventory.select", arguments -> arguments.put("slot", slot));
	}

	@Override
	public void click(int slot, @NotNull InventoryClick click, int button) {
		connection.execute("inventory.click", arguments -> {
			arguments.put("slot", slot);
			arguments.put("click", click.name());
			arguments.put("button", button);
		});
	}

	private void update(JsonNode payload) {
		InventorySnapshot.InventorySnapshotBuilder builder = InventorySnapshot.builder()
				.containerId(payload.path("containerId").asInt())
				.stateId(payload.path("stateId").asInt());
		for (JsonNode item : payload.path("items"))
			builder.item(InventoryItem.builder()
					.slot(item.path("slot").asInt())
					.protocolId(item.path("protocolId").asInt())
					.amount(item.path("amount").asInt())
					.identifier(item.hasNonNull("identifier") ? item.path("identifier").asText() : null)
					.build());
		snapshot.set(builder.build());
	}
}
