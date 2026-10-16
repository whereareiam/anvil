package me.whereareiam.anvil.testkit.tests.server.session;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.capability.console.Console;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.engine.config.EngineProperties;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Observes a Bedrock player's identity through the Lumi agent on the direct Lumi scenario of each Bedrock version in
 * the live matrix, and follows the player through a kick, a rejoin, a disconnect and a new connection with its
 * Session capability. The capabilities bound to the first connection keep working on each new one, and the old
 * connection's state does not leak into them. The {@code anvil.matrix.filter} system property, set from
 * {@code -PanvilMatrixFilter}, selects scenarios by name.
 */
@Tag("compatibility")
class BedrockPlayerSessionSystemTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@TestFactory
	Stream<DynamicTest> reconnectsABedrockPlayerOnEveryLumiVersion() {
		String filter = System.getProperty("anvil.matrix.filter", ".*");

		return CompatibilityScenarioFactory.lumiScenarios().stream()
				.filter(scenario -> scenario.getName().matches(filter))
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> run(scenario)));
	}

	private void run(AnvilScenario scenario) {
		try (ScenarioEngine engine = AnvilLauncher.create(EngineProperties.fromSystemProperties());
		     ScenarioContext anvil = engine.start(scenario)) {
			try {
				verify(anvil);
			} catch (Throwable failure) {
				finishFailed(anvil, failure);
				throw failure;
			}
			anvil.finish(true);
		}
	}

	/**
	 * Finishes a failed run so it keeps its workspaces, without letting a cleanup failure hide the test's failure.
	 */
	private static void finishFailed(ScenarioContext anvil, Throwable failure) {
		try {
			anvil.finish(false);
		} catch (RuntimeException cleanup) {
			failure.addSuppressed(cleanup);
		}
	}

	private void verify(ScenarioContext anvil) {
		Console console = anvil.processes().server("server").capability(Console.class);
		SimulatedPlayer alice = anvil.players().create("Alice");
		Session session = alice.capability(Session.class);
		Server server = alice.capability(Server.class);
		Messages messages = alice.capability(Messages.class);
		Inventory inventory = alice.capability(Inventory.class);

		session.connect();
		session.connected(Duration.ofSeconds(30));
		observesTheGrantedIdentity(server.joined("server", TIMEOUT));
		assertTrue(console.execute("give Alice diamond 1"));
		inventory.matching(snapshot -> holds(snapshot, "minecraft:diamond"), TIMEOUT);

		assertTrue(console.execute("kick Alice anvil:kicked"));
		assertTrue(session.kicked(TIMEOUT).contains("anvil:kicked"), session.state().toString());
		assertFalse(session.state().connected());
		assertNull(server.identity().getGranted());

		session.rejoin();
		session.connected(Duration.ofSeconds(30));
		observesTheGrantedIdentity(server.joined("server", TIMEOUT));
		keepsItsCapabilitiesOnTheNewConnection(console, messages, inventory, "rejoin");

		session.disconnect();
		session.disconnected(TIMEOUT);
		assertFalse(session.state().connected());
		assertNull(server.identity().getGranted());

		session.connect();
		session.connected(Duration.ofSeconds(30));
		observesTheGrantedIdentity(server.joined("server", TIMEOUT));
		keepsItsCapabilitiesOnTheNewConnection(console, messages, inventory, "connect");

		alice.destroy();
		assertTrue(alice.state().destroyed());
		assertTrue(anvil.players().all().isEmpty());
	}

	/**
	 * Lumi lists an offline player to clients under the UUID its login named, but keeps a UUID of its own derived
	 * from the name. The player is granted the listed UUID, and the Lumi agent finds it by that UUID and reports
	 * Lumi's own.
	 */
	private static void observesTheGrantedIdentity(PlayerIdentity identity) {
		PlayerProfile granted = identity.getGranted();
		PlayerProfile observed = identity.getObserved();
		assertNotNull(granted, identity.toString());
		assertNotNull(observed, identity.toString());
		assertEquals("server", identity.getRoute().getServer());
		assertEquals(identity.getRequested(), granted);
		assertEquals("Alice", observed.getUsername());
		assertEquals(UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8)), observed.getUniqueId());
		assertNotEquals(granted.getUniqueId(), observed.getUniqueId());
	}

	/**
	 * The messages and inventory bindings follow the new connection: a message the server sends to it arrives, and
	 * the inventory shows what the server sent the new connection rather than what the previous one held.
	 */
	private static void keepsItsCapabilitiesOnTheNewConnection(Console console, Messages messages, Inventory inventory, String label) {
		int checkpoint = messages.checkpoint();
		assertTrue(console.execute("tellraw Alice {\"rawtext\":[{\"text\":\"anvil:after-" + label + "\"}]}"));
		messages.received(text -> text.equals("anvil:after-" + label), checkpoint, TIMEOUT);

		messages.chat("chat-after-" + label);
		messages.received(text -> text.contains("chat-after-" + label), checkpoint, TIMEOUT);

		assertTrue(console.execute("clear Alice"));
		inventory.matching(snapshot -> snapshot.getItems().isEmpty(), TIMEOUT);
		assertTrue(console.execute("give Alice emerald 2"));
		inventory.matching(snapshot -> holds(snapshot, "minecraft:emerald"), TIMEOUT);
	}

	private static boolean holds(InventorySnapshot snapshot, String identifier) {
		return snapshot.getItems().stream().anyMatch(item -> identifier.equals(item.getIdentifier()));
	}
}
