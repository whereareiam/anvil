package me.whereareiam.anvil.launcher.assembly.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.binding.JsonCapabilityCodec;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.exception.NativeAdapterUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * Converts native lifecycle, messages and adapter lookups to capability-owned values without implementing packet
 * behavior. A native adapter failure becomes the capability's {@link AdapterUnavailableException} with the same
 * reason.
 */
@RequiredArgsConstructor
final class NativePlayerBindingContext implements PlayerBindingContext<Object> {
	private final @NotNull NativePlayer<Object> player;
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
	public @NotNull Object nativeSession() {
		return player.nativeSession();
	}

	@Override
	public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
		return player.isCurrentNativeSession(nativeSession);
	}

	@Override
	public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
		ProtocolSubscription registration = player.bindNativeSession(nativeSession -> listener.apply(nativeSession)::close);
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

	@Override
	public <P> @NotNull P adapter(@NotNull Class<P> port) {
		try {
			return player.adapter(port);
		} catch (NativeAdapterUnavailableException unavailable) {
			throw new AdapterUnavailableException(unavailable.getMessage(), unavailable);
		}
	}
}
