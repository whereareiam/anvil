package me.whereareiam.anvil.capability.interaction.mcprotocol;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.interaction.InteractionOperations;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McProtocolInteractionExtensionTest {
	@Test
	void isDiscoveredAsTheMcProtocolInteractionExtension() {
		WorkerExtension<?> extension = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(McProtocolInteractionExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.interaction", extension.id());
		assertEquals(Optional.of("mcprotocol"), extension.libraryId());
		assertEquals(Object.class, extension.nativeSessionType());
	}

	@Test
	void bindsInteractionThroughTheAdapterTheWorkerSelectedForThePlayersRelease() {
		RecordingPackets packets = new RecordingPackets();
		Player player = new Player(packets);
		Operations operations = new Operations();

		new McProtocolInteractionExtension().bind(player, operations);
		assertNull(operations.execute(InteractionOperations.ITEM, new ItemUse(Hand.MAIN)));

		assertEquals(List.of(InteractionPackets.class), player.requestedPorts);
		assertEquals(List.of("interaction.item", "interaction.block", "interaction.entity"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of("swing " + Player.SESSION, "useItem " + Player.SESSION + " 1"), packets.sent);
	}

	@Test
	void leavesTheCapabilityToTheWorkerWhenNoAdapterServesTheRelease() {
		AdapterUnavailableException unavailable = new AdapterUnavailableException("no mcprotocol segment selected for Minecraft 1.15.2 "
				+ "provides an adapter for " + InteractionPackets.class.getName());
		Player player = new Player(null) {
			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				throw unavailable;
			}
		};
		Operations operations = new Operations();

		assertSame(unavailable, assertThrows(AdapterUnavailableException.class, () -> new McProtocolInteractionExtension().bind(player, operations)));
		assertEquals(Map.of(), operations.handlers);
	}

	private static final class RecordingPackets implements InteractionPackets<String> {
		private final List<String> sent = new ArrayList<>();

		@Override
		public @NotNull Class<String> sessionType() {
			return String.class;
		}

		@Override
		public void swing(@NotNull String session, @NotNull Hand hand) {
			sent.add("swing " + session);
		}

		@Override
		public void useItem(@NotNull String session, @NotNull Hand hand, int sequence, float yaw, float pitch) {
			sent.add("useItem " + session + " " + sequence);
		}

		@Override
		public void useItemOn(@NotNull String session, @NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand, int sequence) {
			sent.add("useItemOn " + session + " " + sequence);
		}

		@Override
		public void entity(@NotNull String session, int entityId, @NotNull EntityInteraction kind, @NotNull Hand hand) {
			sent.add("entity " + session);
		}
	}

	private static class Player implements PlayerBindingContext<Object> {
		private static final String SESSION = "native session";

		private final @Nullable InteractionPackets<?> packets;
		private final List<Class<?>> requestedPorts = new ArrayList<>();

		private Player(@Nullable InteractionPackets<?> packets) {
			this.packets = packets;
		}

		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull Object nativeSession() { return SESSION; }
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) { return nativeSession == SESSION; }
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) { throw new AssertionError(); }
		public @NotNull ViewRotation viewRotation() { return new ViewRotation(0, 0); }
		public void viewRotation(@NotNull ViewRotation rotation) { throw new AssertionError(); }
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) { throw new AssertionError(); }

		@Override
		public <P> @NotNull P adapter(@NotNull Class<P> port) {
			requestedPorts.add(port);
			return port.cast(packets);
		}
	}

	private static final class Operations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), request -> handler.apply((Q) request));
		}

		private <Q, R> @Nullable Object execute(ChannelOperation<Q, R> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}
}
