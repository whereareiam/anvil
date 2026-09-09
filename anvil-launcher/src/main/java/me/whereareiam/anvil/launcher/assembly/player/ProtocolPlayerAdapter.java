package me.whereareiam.anvil.launcher.assembly.player;

import me.whereareiam.anvil.capability.protocol.api.player.ProtocolCapabilityPlayer;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.binding.TypedCapabilityChannel;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Supplies typed capability transport and lifetime while preserving deliberate external backend services.
 */
final class ProtocolPlayerAdapter implements ProtocolCapabilityPlayer {
	private final @NotNull ProtocolPlayer player;
	private final @NotNull Optional<CapabilityChannel> channel;

	ProtocolPlayerAdapter(@NotNull ProtocolPlayer player) {
		this.player = player;
		channel = player.channel().map(protocol -> new TypedCapabilityChannel(new ProtocolMessageChannel(protocol)));
	}

	@Override
	public @NotNull String name() {
		return player.name();
	}

	@Override
	public @NotNull String clientVersion() {
		return player.clientVersion();
	}

	@Override
	public @NotNull Optional<CapabilityChannel> channel() {
		return channel;
	}

	@Override
	public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
		if (ProtocolPlayer.class.isAssignableFrom(type) || ProtocolChannel.class.isAssignableFrom(type))
			return Optional.empty();

		return player.findService(type);
	}

	@Override
	public boolean destroyed() {
		return player.destroyed();
	}

	@Override
	public void destroy() {
		player.destroy();
	}
}
