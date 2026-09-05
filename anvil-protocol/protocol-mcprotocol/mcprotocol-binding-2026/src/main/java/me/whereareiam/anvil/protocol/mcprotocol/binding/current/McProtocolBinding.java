package me.whereareiam.anvil.protocol.mcprotocol.binding.current;

import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerBinding;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerContext;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerSession;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the client-network factory API shared by the verified 2026 protocol artifacts.
 */
public final class McProtocolBinding implements ProtocolWorkerBinding {
	@Override
	public @NotNull String family() {
		return "mcprotocol-2026";
	}

	@Override
	public int protocolNumber() {
		return MinecraftCodec.CODEC.getProtocolVersion();
	}

	@Override
	public @NotNull ProtocolWorkerSession create(@NotNull ProtocolWorkerContext context) {
		return new McProtocolSession(context);
	}
}
