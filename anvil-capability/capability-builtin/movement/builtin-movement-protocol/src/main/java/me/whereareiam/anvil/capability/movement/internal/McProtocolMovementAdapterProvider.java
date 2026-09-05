package me.whereareiam.anvil.capability.movement.internal;

import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Loads the native adapter only after its protocol family has been selected.
 */
public final class McProtocolMovementAdapterProvider implements ProtocolCapabilityAdapterProvider {
	static boolean supportsProtocol(int protocolNumber) {
		return protocolNumber >= 774 && protocolNumber <= 776;
	}

	@Override
	public boolean supports(int protocolNumber) {
		return supportsProtocol(protocolNumber);
	}

	@Override
	public @NotNull ProtocolCapabilityAdapter create() {
		return new McProtocolMovementAdapter();
	}
}
