package me.whereareiam.anvil.capability.world.java;

import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.capability.world.model.LevelInfo;
import me.whereareiam.anvil.capability.world.type.Environment;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaWorldExtensionTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String ARENA = "minecraft:arena";

	private final RecordingProtocol protocol = new RecordingProtocol();
	private final RecordingPlayer player = new RecordingPlayer(protocol);
	private final RecordingOperations operations = new RecordingOperations();
	private final JavaWorldExtension extension = new JavaWorldExtension();

	@Test
	void isDiscoveredAsTheEditionWideWorldExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaWorldExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.world", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void emitsTheLevelTheConnectionJoinsAndEachRespawnWithTheDimensionBefore() {
		connect();

		protocol.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		protocol.deliver(respawn(NETHER, vanilla(NETHER)));
		protocol.deliver(respawn(NETHER, vanilla(NETHER)));

		assertEquals(List.of(change(null, OVERWORLD, Environment.OVERWORLD), change(OVERWORLD, NETHER, Environment.NETHER),
				change(NETHER, NETHER, Environment.NETHER)), player.emitted(WorldChannel.DIMENSION));
		assertTrue(operations.registered.isEmpty());
	}

	@Test
	void describesTheLevelWithItsTypeEnvironmentFlagsAndTheServersLevels() {
		connect();

		protocol.deliver(ClientboundLogin.builder()
				.entityId(7)
				.level(OVERWORLD)
				.level(ARENA)
				.hardcore(true)
				.dimension(ARENA)
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end"))
				.debug(true)
				.flat(true)
				.build());
		protocol.deliver(respawn(ARENA, new DimensionType("example:modded", null, null)));

		List<LevelInfo> levels = player.emitted(WorldChannel.DIMENSION).stream().map(DimensionChange::getLevel).toList();
		assertEquals(LevelInfo.builder()
				.dimension(ARENA)
				.dimensionType("example:void")
				.environment(Environment.END)
				.hardcore(true)
				.debug(true)
				.flat(true)
				.level(OVERWORLD)
				.level(ARENA)
				.build(), levels.get(0));
		assertEquals(LevelInfo.builder()
				.dimension(ARENA)
				.dimensionType("example:modded")
				.environment(Environment.CUSTOM)
				.hardcore(true)
				.level(OVERWORLD)
				.level(ARENA)
				.build(), levels.get(1));
	}

	@Test
	void keepsTheDimensionWhenAProxyJoinsTheConnectionToAnotherServersLevel() {
		connect();

		protocol.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		protocol.deliver(login(8, ARENA, vanilla(OVERWORLD)));

		assertEquals(Arrays.asList(null, OVERWORLD), player.emitted(WorldChannel.DIMENSION).stream()
				.map(DimensionChange::getFrom).toList());
	}

	@Test
	void leavesTheDimensionWhenTheConnectionEndsAndStartsTheNextConnectionInNone() {
		connect();
		protocol.deliver(login(7, NETHER, vanilla(NETHER)));

		player.startConnection();
		protocol.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));
		player.endConnection();

		assertEquals(List.of(NETHER, OVERWORLD), player.emitted(WorldChannel.LEFT));
		assertEquals(List.of(change(null, NETHER, Environment.NETHER), change(null, OVERWORLD, Environment.OVERWORLD)),
				player.emitted(WorldChannel.DIMENSION));
	}

	@Test
	void leavesNothingForAConnectionThatEndsBeforeJoiningALevel() {
		connect();
		player.endConnection();

		assertTrue(player.emitted(WorldChannel.LEFT).isEmpty());
	}

	@Test
	void emitsNothingOnceTheBindingCloses() {
		WorkerBinding binding = extension.bind(player, operations);
		player.startConnection();
		protocol.deliver(login(7, OVERWORLD, vanilla(OVERWORLD)));

		binding.close();
		protocol.deliver(respawn(NETHER, vanilla(NETHER)));

		assertEquals(List.of(change(null, OVERWORLD, Environment.OVERWORLD)), player.emitted(WorldChannel.DIMENSION));
		assertTrue(player.emitted(WorldChannel.LEFT).isEmpty());
		assertNull(player.connections);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer silent = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		assertNull(silent.connections);
	}

	private void connect() {
		extension.bind(player, operations);
		player.startConnection();
	}

	private static ClientboundLogin login(int entityId, String dimension, DimensionType type) {
		return ClientboundLogin.builder()
				.entityId(entityId)
				.level(OVERWORLD)
				.level(NETHER)
				.dimension(dimension)
				.dimensionType(type)
				.build();
	}

	private static ClientboundRespawn respawn(String dimension, DimensionType type) {
		return ClientboundRespawn.builder().dimension(dimension).dimensionType(type).build();
	}

	private static DimensionType vanilla(String key) {
		return DimensionType.withEffects(key, key);
	}

	/**
	 * A change into a vanilla dimension of a server whose levels are the overworld and the nether.
	 */
	private static DimensionChange change(String from, String to, Environment environment) {
		return new DimensionChange(from, to, LevelInfo.builder()
				.dimension(to)
				.dimensionType(to)
				.environment(environment)
				.level(OVERWORLD)
				.level(NETHER)
				.build());
	}

	/**
	 * Hands delivered packets to the listener of their class; the world sends nothing.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			throw new AssertionError("The world sends no packets");
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The world binding observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final List<String> registered = new ArrayList<>();

		@Override
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			registered.add(operation.getId());
		}
	}

	/**
	 * A player that speaks the given protocol, or none, whose connections the test starts and ends: each
	 * {@link #startConnection()} closes the binding of the previous connection and binds the new one, as the worker
	 * does before a new session connects, and {@link #endConnection()} closes the current one, as the worker does when
	 * the connection ends.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<Emitted> emitted = new ArrayList<>();
		private @Nullable Function<Object, Subscription> connections;
		private @Nullable Subscription connection;

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		private void startConnection() {
			endConnection();
			if (connections != null) connection = connections.apply(new Object());
		}

		private void endConnection() {
			if (connection != null) connection.close();
			connection = null;
		}

		private <E> List<E> emitted(EventDescriptor<E> event) {
			return emitted.stream()
					.filter(emitted -> emitted.id().equals(event.getId()))
					.map(emitted -> event.getPayloadType().cast(emitted.payload()))
					.toList();
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (protocol == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(protocol);
		}

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes("Alice".getBytes());
		}

		@Override
		public void connect() {
			throw new AssertionError("The world does not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("The world does not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("The world does not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("The world uses the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("The world uses the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			connections = listener;
			return () -> {
				connections = null;
				endConnection();
			};
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("The world does not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("The world does not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			emitted.add(new Emitted(event.getId(), payload));
		}

		private record Emitted(String id, Object payload) {
		}
	}
}
