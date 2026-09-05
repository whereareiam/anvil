package me.whereareiam.anvil.capability.inventory.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

/**
 * Host-side provider for inventory snapshots, selection, and clicks.
 */
public final class McProtocolInventoryProvider implements PlayerCapabilityProvider<Inventory> {
	static final String ID = "me.whereareiam.anvil.inventory";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.supportedProtocolId("mcprotocol")
				.requiredCapability(Session.class)
				.build();
	}

	@Override
	public @NotNull Class<Inventory> capability() {
		return Inventory.class;
	}

	@Override
	public @NotNull Inventory create(@NotNull PlayerCapabilityContext context) {
		context.requireCapability(Session.class);
		ProtocolPlayerConnection connection = context.requireService(ProtocolPlayerConnection.class);
		if (!connection.workerCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolInventory(connection);
	}
}
