package me.whereareiam.anvil.capability.sounds.mcprotocol;

import me.whereareiam.anvil.capability.sounds.SoundsOperations;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolSoundsExtensionTest {
	private static final HeardSound LEVEL_UP = HeardSound.builder()
			.key("minecraft:entity.player.levelup")
			.category(SoundCategory.PLAYERS)
			.volume(1F)
			.pitch(1F)
			.entityId(7)
			.build();

	@Test
	void isDiscoveredAsTheMcProtocolSoundsExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolSoundsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.sounds", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsSoundsThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);
		Operations operations = new Operations();

		new McProtocolSoundsExtension().bind(player, operations);
		assertNotNull(packets.listener);
		packets.listener.accept(LEVEL_UP);

		assertEquals(List.of(SoundsPackets.class), player.requestedPorts);
		assertTrue(operations.registered.isEmpty());
		assertSame(Player.SESSION, packets.listened);
		assertEquals(List.of(LEVEL_UP), player.emitted);
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		ProtocolUnavailableException unavailable = new ProtocolUnavailableException("no mcprotocol segment selected for Minecraft 1.16.5 "
				+ "provides an adapter for " + SoundsPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};

		assertSame(unavailable, assertThrows(ProtocolUnavailableException.class,
				() -> new McProtocolSoundsExtension().bind(player, new Operations())));
	}

	private static final class RecordingPackets implements SoundsPackets<String> {
		private @Nullable String listened;
		private @Nullable Consumer<HeardSound> listener;

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public @NotNull Runnable listen(@NotNull String session, @NotNull Consumer<HeardSound> heard) {
			listened = session;
			listener = heard;
			return () -> listened = null;
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable SoundsPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();
		private final List<Object> emitted = new ArrayList<>();

		private Player(@Nullable SoundsPackets<?> packets) {
			this.packets = packets;
		}

		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull Object nativeSession() { return SESSION; }
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) { return nativeSession == SESSION; }
		public @NotNull ViewRotation viewRotation() { throw new AssertionError(); }
		public void viewRotation(@NotNull ViewRotation rotation) { throw new AssertionError(); }

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			return listener.apply(SESSION);
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			assertSame(SoundsOperations.HEARD, eventDescriptor);
			emitted.add(payload);
		}

		@Override
		public <P> @NotNull P adapter(@NotNull Class<P> port) {
			requestedPorts.add(port);
			return port.cast(packets);
		}
	}

	private static final class Operations implements OperationRegistry {
		private final List<String> registered = new ArrayList<>();

		@Override
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			registered.add(operation.getId());
		}
	}
}
