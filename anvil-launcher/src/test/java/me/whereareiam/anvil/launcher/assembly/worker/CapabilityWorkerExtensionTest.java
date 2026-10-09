package me.whereareiam.anvil.launcher.assembly.worker;

import lombok.Value;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.binding.JsonCapabilityCodec;
import me.whereareiam.anvil.capability.binding.WorkerCapabilities;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.exception.NativeAdapterUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityWorkerExtensionTest {
	@Test
	void bindsIndependentNativeContextsAndTransfersSubscriptionCleanup() {
		WorkerExtension<String> extension = new WorkerExtension<>() {
			@Override
			public @NotNull String id() {
				return "external.context";
			}

			@Override
			public @NotNull Optional<String> libraryId() {
				return Optional.of("external");
			}

			@Override
			public @NotNull Class<String> nativeSessionType() {
				return String.class;
			}

			@Override
			public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<String> player, @NotNull OperationRegistry operations) {
				assertEquals(new ViewRotation(12, 34), player.viewRotation());
				player.viewRotation(new ViewRotation(56, 78));
				operations.register(new ChannelOperation<>("external.echo", Message.class, Message.class), request -> {
					Message result = new Message(player.name() + ":" + request.getText());
					player.emit(new EventDescriptor<>("external.changed", Message.class), result);
					return result;
				});
				var subscription = player.bindNativeSession(nativeSession -> {
					assertSame(player.nativeSession(), nativeSession);
					assertTrue(player.isCurrentNativeSession(nativeSession));
					return player::disconnect;
				});
				return subscription::close;
			}
		};
		CapabilityWorkerExtension bridge = new CapabilityWorkerExtension(
				new WorkerCapabilities("external", String.class, List.of(extension)));
		StubNativePlayer alice = new StubNativePlayer("Alice");
		StubNativePlayer bob = new StubNativePlayer("Bob");
		Map<String, Function<byte[], byte[]>> first = new HashMap<>();
		Map<String, Function<byte[], byte[]>> second = new HashMap<>();
		JsonCapabilityCodec codec = new JsonCapabilityCodec();
		try (var aliceBinding = bridge.bind(alice, first::put);
			 var bobBinding = bridge.bind(bob, second::put)) {
			assertEquals(new Message("Alice:hello"), codec.decode(first.get("external.echo").apply(codec.encode(new Message("hello"))), Message.class));
			assertEquals(new Message("Bob:hello"), codec.decode(second.get("external.echo").apply(codec.encode(new Message("hello"))), Message.class));
			assertEquals(new Message("Alice:hello"), codec.decode(alice.payload, Message.class));
			assertEquals("external.changed", alice.event);
			assertEquals(56, alice.yaw);
			assertEquals(78, alice.pitch);
			assertEquals(0, alice.disconnections);
		}
		assertEquals(1, alice.disconnections);
		assertEquals(1, bob.disconnections);
	}

	@Test
	void passesTheWorkersAdaptersToBindingsAndReportsAnUnavailableAdapterAsAnUnavailableCapability() {
		List<Runnable> received = new ArrayList<>();
		CapabilityWorkerExtension bridge = new CapabilityWorkerExtension(new WorkerCapabilities("external", String.class, List.of(
				extension("external.adapted", (player, operations) -> {
					received.add(player.adapter(Runnable.class));
					return () -> { };
				}),
				extension("external.missing", (player, operations) -> {
					player.adapter(Comparable.class);
					return () -> { };
				})
		)));
		StubNativePlayer alice = new StubNativePlayer("Alice");

		try (var ignored = bridge.bind(alice, (operation, handler) -> { })) {
			assertEquals(List.of(StubNativePlayer.ADAPTER), received);
			assertEquals(Set.of("external.adapted"), bridge.capabilities());
			assertEquals(Map.of("external.missing", "no external segment provides java.lang.Comparable"), bridge.unavailable());
		}
	}

	private static WorkerExtension<String> extension(
			String id,
			BiFunction<PlayerBindingContext<String>, OperationRegistry, WorkerBinding> binding
	) {
		return new WorkerExtension<>() {
			@Override
			public @NotNull String id() {
				return id;
			}

			@Override
			public @NotNull Optional<String> libraryId() {
				return Optional.of("external");
			}

			@Override
			public @NotNull Class<String> nativeSessionType() {
				return String.class;
			}

			@Override
			public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<String> player, @NotNull OperationRegistry operations) {
				return binding.apply(player, operations);
			}
		};
	}

	@Value
	private static class Message {
		String text;
	}

	private static final class StubNativePlayer implements NativePlayer<Object> {
		private static final Runnable ADAPTER = () -> { };

		private final String name;
		private final String session;
		private final UUID uniqueId = UUID.randomUUID();
		private float yaw = 12;
		private float pitch = 34;
		private int disconnections;
		private String event;
		private byte[] payload;

		private StubNativePlayer(String name) {
			this.name = name;
			this.session = name + "-native-session";
		}

		@Override
		public @NotNull String name() {
			return name;
		}

		@Override
		public @NotNull UUID uniqueId() {
			return uniqueId;
		}

		@Override
		public void connect() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void disconnect() {
			disconnections++;
		}

		@Override
		public void rejoin() {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull Object nativeSession() {
			return session;
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			return session == nativeSession;
		}

		@Override
		public @NotNull ProtocolSubscription bindNativeSession(@NotNull Function<Object, ProtocolSubscription> listener) {
			return listener.apply(session);
		}

		@Override
		public float yaw() {
			return yaw;
		}

		@Override
		public float pitch() {
			return pitch;
		}

		@Override
		public void view(float yaw, float pitch) {
			this.yaw = yaw;
			this.pitch = pitch;
		}

		@Override
		public void emit(@NotNull String event, byte @NotNull [] payload) {
			this.event = event;
			this.payload = payload;
		}

		@Override
		public <P> @NotNull P adapter(@NotNull Class<P> port) {
			if (port == Runnable.class) return port.cast(ADAPTER);
			throw new NativeAdapterUnavailableException("no external segment provides " + port.getName());
		}
	}
}
