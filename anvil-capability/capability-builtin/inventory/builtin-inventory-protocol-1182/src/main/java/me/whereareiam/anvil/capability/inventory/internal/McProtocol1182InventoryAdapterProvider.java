package me.whereareiam.anvil.capability.inventory.internal;

import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Loads the native adapter only after its protocol family has been selected.
 */
public final class McProtocol1182InventoryAdapterProvider implements ProtocolCapabilityAdapterProvider {
	static boolean supportsProtocol(int protocolNumber) {
		return protocolNumber == 758 || protocolNumber == 762;
	}

	@Override
	public boolean supports(int protocolNumber) {
		return supportsProtocol(protocolNumber);
	}

	@Override
	public @NotNull ProtocolCapabilityAdapter create() {
		return new McProtocol1182InventoryAdapter();
	}
}
