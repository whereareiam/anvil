package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for inventory snapshots, hotbar selection and container clicks over the player's typed
 * channel, for the libraries the inventory family has a worker side for. Composition creates it only for players
 * whose worker installed the inventory binding.
 */
public final class InventoryProvider implements ProtocolPlayerCapabilityProvider<Inventory> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.inventory";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedLibraries() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Inventory> capability() {
		return Inventory.class;
	}

	@Override
	public @NotNull Inventory create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelInventory(context.channel());
	}
}
