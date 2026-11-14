package me.whereareiam.anvil.capability.vitals.java;

import me.whereareiam.anvil.capability.vitals.channel.VitalsChannel;
import me.whereareiam.anvil.capability.vitals.model.Death;
import me.whereareiam.anvil.capability.vitals.model.VitalStatus;
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
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaVitalsExtensionTest {
	private static final int PLAYER = 7;
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String KILLED = "death.attack.genericKill Alice";
	private static final ServerboundClientCommand RESPAWN =
			new ServerboundClientCommand(ClientCommandAction.PERFORM_RESPAWN);

	private final RecordingProtocol protocol = new RecordingProtocol();
	private final RecordingPlayer player = new RecordingPlayer(protocol);
	private final RecordingOperations operations = new RecordingOperations();
	private final JavaVitalsExtension extension = new JavaVitalsExtension();

	@Test
	void isDiscoveredAsTheEditionWideVitalsExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaVitalsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.vitals", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void startsEachConnectionWithTheInitialValuesAndEmitsEveryHealthUpdate() {
		connect(true);
		protocol.deliver(new ClientboundSetHealth(12.5F, 18, 3F));
		player.startConnection();

		assertEquals(List.of(VitalStatus.INITIAL, new VitalStatus(12.5F, 18, 3F), VitalStatus.INITIAL),
				player.emitted(VitalsChannel.STATUS));
	}

	@Test
	void emitsTheDeathsOfThePlayersOwnEntityWithTheirMessageAndStaysDead() {
		connect(true);

		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER + 1, "death.attack.genericKill Bob"));
		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER, KILLED));

		assertEquals(List.of(new Death(KILLED, false)), player.emitted(VitalsChannel.DIED));
		assertTrue(protocol.sent.isEmpty());
	}

	@Test
	void respawnsByItselfWhenTheLoginTurnedTheDeathScreenOff() {
		connect(false);

		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER, KILLED));

		assertEquals(List.of(new Death(KILLED, true)), player.emitted(VitalsChannel.DIED));
		assertEquals(List.of(RESPAWN), protocol.sent);
	}

	@Test
	void followsTheImmediateRespawnGameEventUntilTheNextConnection() {
		connect(true);
		protocol.deliver(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 1F));
		protocol.deliver(new ClientboundGameEvent(GameEventType.START_RAINING, 0F));
		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER, KILLED));
		protocol.deliver(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 0F));
		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER, KILLED));

		assertEquals(List.of(new Death(KILLED, true), new Death(KILLED, false)), player.emitted(VitalsChannel.DIED));
		assertEquals(List.of(RESPAWN), protocol.sent);

		protocol.deliver(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 1F));
		player.startConnection();
		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER, KILLED));
		protocol.deliver(login(true));
		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER, KILLED));

		assertEquals(List.of(new Death(KILLED, true), new Death(KILLED, false), new Death(KILLED, false)),
				player.emitted(VitalsChannel.DIED));
		assertEquals(List.of(RESPAWN), protocol.sent);
	}

	@Test
	void respawnsOnRequestAsTheDeathScreensButtonDoes() {
		connect(true);

		operations.call(VitalsChannel.RESPAWN);

		assertEquals(List.of(RESPAWN), protocol.sent);
	}

	@Test
	void emitsNothingOnceTheBindingCloses() {
		WorkerBinding binding = extension.bind(player, operations);
		player.startConnection();

		binding.close();
		protocol.deliver(new ClientboundSetHealth(1F, 1, 0F));
		protocol.deliver(new ClientboundPlayerCombatKill(PLAYER, KILLED));

		assertEquals(List.of(VitalStatus.INITIAL), player.emitted(VitalsChannel.STATUS));
		assertTrue(player.emitted(VitalsChannel.DIED).isEmpty());
		assertNull(player.connections);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer silent = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		assertNull(silent.connections);
	}

	private void connect(boolean deathScreen) {
		extension.bind(player, operations);
		player.startConnection();
		protocol.deliver(login(deathScreen));
	}

	private static ClientboundLogin login(boolean deathScreen) {
		return ClientboundLogin.builder()
				.entityId(PLAYER)
				.level(OVERWORLD)
				.dimension(OVERWORLD)
				.dimensionType(DimensionType.withEffects(OVERWORLD, OVERWORLD))
				.showDeathScreen(deathScreen)
				.build();
	}

	/**
	 * Hands delivered packets to the listener of their class and records the packets sent.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();
		private final List<ServerboundPacket> sent = new ArrayList<>();

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
			sent.add(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The vitals binding observes no sent packets");
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
		private final Map<String, Function<Object, Object>> registered = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			registered.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private void call(OperationDescriptor<?, ?> operation) {
			registered.get(operation.getId()).apply(null);
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
			throw new AssertionError("The vitals do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("The vitals do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("The vitals do not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("The vitals use the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("The vitals use the protocol, not the native session");
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
			throw new AssertionError("The vitals do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("The vitals do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			emitted.add(new Emitted(event.getId(), payload));
		}

		private record Emitted(String id, Object payload) {
		}
	}
}
