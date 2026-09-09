package me.whereareiam.anvil.capability.inventory.binding;

import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.internal.McProtocolInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for inventory snapshots, selection, and clicks.
 */
public final class McProtocolInventoryProvider implements ProtocolPlayerCapabilityProvider<Inventory> {
	static final String ID = "me.whereareiam.anvil.inventory";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Inventory> capability() {
		return Inventory.class;
	}

	@Override
	public @NotNull Inventory create(@NotNull ProtocolPlayerCapabilityContext context) {
		CapabilityChannel connection = context.channel();
		if (!connection.installedCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolInventory(new ChannelInventoryConnection(context));
	}
}
