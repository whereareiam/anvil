package me.whereareiam.anvil.launcher.assembly.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.binding.JsonCapabilityCodec;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * Converts native lifecycle and messages to capability-owned values without implementing packet behavior.
 */
@RequiredArgsConstructor
final class NativePlayerBindingContext<B> implements PlayerBindingContext<B> {
	private final @NotNull NativePlayer<B> player;
	private final @NotNull JsonCapabilityCodec codec = new JsonCapabilityCodec();

	@Override
	public @NotNull String name() {
		return player.name();
	}

	@Override
	public @NotNull UUID uniqueId() {
		return player.uniqueId();
	}

	@Override
	public void connect() {
		player.connect();
	}

	@Override
	public void disconnect() {
		player.disconnect();
	}

	@Override
	public void rejoin() {
		player.rejoin();
	}

	@Override
	public @NotNull B backend() {
		return player.backend();
	}

	@Override
	public boolean isCurrentBackend(@NotNull B backend) {
		return player.isCurrentBackend(backend);
	}

	@Override
	public @NotNull Subscription bindBackend(@NotNull Function<B, Subscription> listener) {
		ProtocolSubscription registration = player.bindBackend(backend -> listener.apply(backend)::close);
		return registration::close;
	}

	@Override
	public @NotNull ViewRotation viewRotation() {
		return new ViewRotation(player.yaw(), player.pitch());
	}

	@Override
	public void viewRotation(@NotNull ViewRotation rotation) {
		player.view(rotation.getYaw(), rotation.getPitch());
	}

	@Override
	public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
		player.emit(eventDescriptor.getId(), codec.encode(payload));
	}
}
