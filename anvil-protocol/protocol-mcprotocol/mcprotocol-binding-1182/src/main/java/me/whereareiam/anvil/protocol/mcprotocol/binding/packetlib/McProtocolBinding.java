package me.whereareiam.anvil.protocol.mcprotocol.binding.packetlib;

import com.github.steveice10.mc.protocol.codec.MinecraftCodec;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerBinding;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerContext;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerSession;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the shared PacketLib session API used by the 1.18.2 and 1.19.4 artifacts.
 */
public final class McProtocolBinding implements ProtocolWorkerBinding {
	@Override
	public @NotNull String family() {
		return "mcprotocol-packetlib";
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
