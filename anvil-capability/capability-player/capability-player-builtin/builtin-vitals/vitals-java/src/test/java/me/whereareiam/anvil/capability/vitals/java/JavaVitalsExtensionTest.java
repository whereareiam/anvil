package me.whereareiam.anvil.capability.vitals.java;

import me.whereareiam.anvil.capability.vitals.channel.VitalsChannel;
import me.whereareiam.anvil.capability.vitals.model.Death;
import me.whereareiam.anvil.capability.vitals.model.VitalStatus;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.type.player.ClientCommandAction;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

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

	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();
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
		player.deliver(new ClientboundSetHealth(12.5F, 18, 3F));
		player.rejoin();

		assertEquals(List.of(VitalStatus.INITIAL, new VitalStatus(12.5F, 18, 3F), VitalStatus.INITIAL),
				player.emitted(VitalsChannel.STATUS));
	}

	@Test
	void emitsTheDeathsOfThePlayersOwnEntityWithTheirMessageAndStaysDead() {
		connect(true);

		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER + 1), "death.attack.genericKill Bob"));
		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER), KILLED));

		assertEquals(List.of(new Death(KILLED, false)), player.emitted(VitalsChannel.DIED));
		assertTrue(player.sent().isEmpty());
	}

	@Test
	void respawnsByItselfWhenTheLoginTurnedTheDeathScreenOff() {
		connect(false);

		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER), KILLED));

		assertEquals(List.of(new Death(KILLED, true)), player.emitted(VitalsChannel.DIED));
		assertEquals(List.of(RESPAWN), player.sent());
	}

	@Test
	void followsTheImmediateRespawnGameEventUntilTheNextConnection() {
		connect(true);
		player.deliver(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 1F));
		player.deliver(new ClientboundGameEvent(GameEventType.START_RAINING, 0F));
		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER), KILLED));
		player.deliver(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 0F));
		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER), KILLED));

		assertEquals(List.of(new Death(KILLED, true), new Death(KILLED, false)), player.emitted(VitalsChannel.DIED));
		assertEquals(List.of(RESPAWN), player.sent());

		player.deliver(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 1F));
		player.rejoin();
		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER), KILLED));
		player.deliver(login(true));
		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER), KILLED));

		assertEquals(List.of(new Death(KILLED, true), new Death(KILLED, false), new Death(KILLED, false)),
				player.emitted(VitalsChannel.DIED));
		assertEquals(List.of(RESPAWN), player.sent());
	}

	@Test
	void respawnsOnRequestAsTheDeathScreensButtonDoes() {
		connect(true);

		assertNull(operations.call(VitalsChannel.RESPAWN, null));

		assertEquals(List.of(RESPAWN), player.sent());
	}

	@Test
	void emitsNothingOnceTheBindingCloses() {
		WorkerBinding binding = extension.bind(player, operations);
		player.connect();

		binding.close();
		player.deliver(new ClientboundSetHealth(1F, 1, 0F));
		player.deliver(new ClientboundPlayerCombatKill(new EntityId(PLAYER), KILLED));
		player.rejoin();

		assertEquals(List.of(VitalStatus.INITIAL), player.emitted(VitalsChannel.STATUS));
		assertTrue(player.emitted(VitalsChannel.DIED).isEmpty());
		assertEquals(List.of(VitalsChannel.STATUS.id()), player.emittedEvents());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		silent.connect();

		assertEquals(List.of(), operations.registered());
		assertEquals(List.of(), silent.emittedEvents());
	}

	private void connect(boolean deathScreen) {
		extension.bind(player, operations);
		player.connect();
		player.deliver(login(deathScreen));
	}

	private static ClientboundLogin login(boolean deathScreen) {
		return ClientboundLogin.builder()
				.entityId(new EntityId(PLAYER))
				.level(OVERWORLD)
				.dimension(OVERWORLD)
				.dimensionType(DimensionType.known(OVERWORLD))
				.showDeathScreen(deathScreen)
				.build();
	}
}
