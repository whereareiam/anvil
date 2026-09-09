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
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
			public @NotNull String backendId() {
				return "external";
			}

			@Override
			public @NotNull Class<String> backendType() {
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
				var subscription = player.bindBackend(nativeSession -> {
					assertSame(player.backend(), nativeSession);
					assertTrue(player.isCurrentBackend(nativeSession));
					return player::disconnect;
				});
				return subscription::close;
			}
		};
		CapabilityWorkerExtension<String> bridge = new CapabilityWorkerExtension<>(
				new WorkerCapabilities<>("external", String.class, 1, List.of(extension)));
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

	@Value
	private static class Message {
		String text;
	}

	private static final class StubNativePlayer implements NativePlayer<String> {
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
		public @NotNull String backend() {
			return session;
		}

		@Override
		public boolean isCurrentBackend(@NotNull String backend) {
			return session == backend;
		}

		@Override
		public @NotNull ProtocolSubscription bindBackend(@NotNull Function<String, ProtocolSubscription> listener) {
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
	}
}
