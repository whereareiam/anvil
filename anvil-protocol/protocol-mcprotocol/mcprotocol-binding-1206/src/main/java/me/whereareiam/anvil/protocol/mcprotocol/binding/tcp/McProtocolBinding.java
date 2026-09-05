package me.whereareiam.anvil.protocol.mcprotocol.binding.tcp;

import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerBinding;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerContext;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerSession;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the direct TCP-session API used by the 1.20.6 protocol artifact.
 */
public final class McProtocolBinding implements ProtocolWorkerBinding {
	@Override
	public @NotNull String family() {
		return "mcprotocol-tcp";
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
