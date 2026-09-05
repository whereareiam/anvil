package me.whereareiam.anvil.capability.interaction.internal;

import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Loads the native adapter only after its protocol family has been selected.
 */
public final class McProtocol1206InteractionAdapterProvider implements ProtocolCapabilityAdapterProvider {
	static boolean supportsProtocol(int protocolNumber) {
		return protocolNumber == 766;
	}

	@Override
	public boolean supports(int protocolNumber) {
		return supportsProtocol(protocolNumber);
	}

	@Override
	public @NotNull ProtocolCapabilityAdapter create() {
		return new McProtocol1206InteractionAdapter();
	}
}
