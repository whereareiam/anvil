package me.whereareiam.anvil.launcher.assembly.player;

import me.whereareiam.anvil.capability.protocol.api.player.ProtocolCapabilityPlayer;

import lombok.Value;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.binding.JsonCapabilityCodec;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolPlayerAdapterTest {
	@Test
	void adaptsTypedMessagesAndSubscriptionLifetime() {
		StubPlayer protocol = new StubPlayer();
		ProtocolCapabilityPlayer player = new ProtocolPlayerAdapter(protocol);
		var channel = player.channel().orElseThrow();
		ChannelOperation<Message, Message> echo = new ChannelOperation<>("external.echo", Message.class, Message.class);
		Message request = new Message("hello");

		assertEquals(request, channel.request(echo, request));
		assertEquals("external.echo", protocol.transport.operation);

		AtomicReference<Message> received = new AtomicReference<>();
		var subscription = channel.subscribe(new EventDescriptor<>("external.changed", Message.class), received::set);
		protocol.transport.listener.accept(new JsonCapabilityCodec().encode(request));
		assertEquals(request, received.get());
		subscription.close();
		assertTrue(protocol.transport.unsubscribed);
		player.destroy();
		assertTrue(player.destroyed());
	}

	@Test
	void hidesProtocolContractsWhilePreservingExternalServicesOnTheSamePlayer() {
		StubPlayer protocol = new StubPlayer();
		ProtocolCapabilityPlayer player = new ProtocolPlayerAdapter(protocol);

		assertSame(protocol.external, player.findService(ExternalService.class).orElseThrow());
		assertSame(protocol, player.findService(ExternalPlayerService.class).orElseThrow());
		assertTrue(player.findService(ProtocolPlayer.class).isEmpty());
		assertTrue(player.findService(StubPlayer.class).isEmpty());
		assertTrue(player.findService(ProtocolChannel.class).isEmpty());
		assertTrue(player.findService(StubChannel.class).isEmpty());
	}

	@Test
	void preservesTransportFailuresAndOptionalChannelAbsence() {
		StubPlayer protocol = new StubPlayer();
		IllegalStateException failure = new IllegalStateException("player-specific transport failure");
		protocol.transport.failure = failure;
		var player = new ProtocolPlayerAdapter(protocol);

		assertSame(failure, assertThrows(IllegalStateException.class, () -> player.channel().orElseThrow()
				.request(new ChannelOperation<>("external.echo", Void.class, Void.class), null)));

		protocol.hasChannel = false;
		assertTrue(new ProtocolPlayerAdapter(protocol).channel().isEmpty());
	}

	@Value
	private static class Message {
		String text;
	}

	private static final class ExternalService {
	}

	private interface ExternalPlayerService {
	}

	private static final class StubPlayer implements ProtocolPlayer, ExternalPlayerService {
		private final ExternalService external = new ExternalService();
		private final StubChannel transport = new StubChannel();
		private boolean destroyed;
		private boolean hasChannel = true;

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "1.21.11";
		}

		@Override
		public @NotNull PlayerIdentity identity() {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			if (type.isInstance(external)) return Optional.of(type.cast(external));
			if (type.isInstance(transport)) return Optional.of(type.cast(transport));
			return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
		}

		@Override
		public @NotNull Optional<ProtocolChannel> channel() {
			return hasChannel ? Optional.of(transport) : Optional.empty();
		}

		@Override
		public boolean destroyed() {
			return destroyed;
		}

		@Override
		public void destroy() {
			destroyed = true;
		}
	}

	private static final class StubChannel implements ProtocolChannel {
		private String operation;
		private Consumer<byte[]> listener;
		private RuntimeException failure;
		private boolean unsubscribed;

		@Override
		public byte @NotNull [] request(@NotNull String operation, byte @NotNull [] request) {
			if (failure != null) throw failure;

			this.operation = operation;
			return request;
		}

		@Override
		public @NotNull ProtocolSubscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener) {
			this.listener = listener;
			return () -> unsubscribed = true;
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (failure != null) throw failure;
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of("external.echo");
		}
	}
}
