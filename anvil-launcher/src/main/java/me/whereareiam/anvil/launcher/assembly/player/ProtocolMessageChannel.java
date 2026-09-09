package me.whereareiam.anvil.launcher.assembly.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.MessageChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Connects capability-owned codecs to a borrowed protocol-owned message channel.
 */
@RequiredArgsConstructor
final class ProtocolMessageChannel implements MessageChannel {
	private final @NotNull ProtocolChannel channel;

	@Override
	public byte @NotNull [] request(@NotNull String operation, byte @NotNull [] request) {
		return channel.request(operation, request);
	}

	@Override
	public @NotNull Subscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener) {
		return channel.subscribe(event, listener)::close;
	}

	@Override
	public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
		channel.await(condition, description, timeout);
	}

	@Override
	public @NotNull Set<String> installedCapabilities() {
		return channel.installedCapabilities();
	}
}
