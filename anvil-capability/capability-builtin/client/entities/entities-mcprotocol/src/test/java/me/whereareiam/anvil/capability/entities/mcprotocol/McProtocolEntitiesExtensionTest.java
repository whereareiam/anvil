package me.whereareiam.anvil.capability.entities.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.entities.EntitiesOperations;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.packet.EntitiesPackets;
import me.whereareiam.anvil.capability.entities.packet.model.EntityPacket;
import me.whereareiam.anvil.capability.entities.packet.model.EntitySpawned;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McProtocolEntitiesExtensionTest {
	@Test
	void isDiscoveredAsTheMcProtocolEntitiesExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolEntitiesExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.entities", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsEntitiesThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);

		Operations operations = new Operations();
		new McProtocolEntitiesExtension().bind(player, operations);
		packets.listener.accept(EntitySpawned.builder().entityId(7).type("minecraft:armor_stand").build());

		assertEquals(List.of(EntitiesPackets.class), player.requestedPorts);
		assertEquals(List.of("entities.known"), operations.registered);
		assertSame(Player.SESSION, packets.listened);
		assertEquals(List.of(EntityChangeKind.APPEARED), player.emitted.stream().map(EntityChange::getKind).toList());
		assertEquals(7, player.emitted.getFirst().getEntity().getId());
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		AdapterUnavailableException unavailable = new AdapterUnavailableException("no mcprotocol segment selected for Minecraft 1.16.5 "
				+ "provides an adapter for " + EntitiesPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};

		assertSame(unavailable, assertThrows(AdapterUnavailableException.class,
				() -> new McProtocolEntitiesExtension().bind(player, new Operations())));
	}

	private static final class RecordingPackets implements EntitiesPackets<String> {
		private @Nullable String listened;
		private Consumer<EntityPacket> listener = packet -> {
			throw new AssertionError("No session is observed");
		};

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public @NotNull Runnable listen(@NotNull String session, @NotNull Consumer<EntityPacket> packets) {
			listened = session;
			listener = packets;
			return () -> listened = null;
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable EntitiesPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();
		private final List<EntityChange> emitted = new ArrayList<>();

		private Player(@Nullable EntitiesPackets<?> packets) {
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
			assertSame(EntitiesOperations.CHANGED, eventDescriptor);
			emitted.add((EntityChange) payload);
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
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
			registered.add(operation.getId());
		}
	}
}
