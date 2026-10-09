package me.whereareiam.anvil.testkit.tests.server.capability;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import me.whereareiam.anvil.testkit.tests.server.extension.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives every built-in player capability through the fixture plugin on the direct Paper scenario of each Minecraft
 * version in the live matrix, so the capability code of every protocol library release meets a real server. The
 * {@code anvil.matrix.filter} system property, set from {@code -PanvilMatrixFilter}, selects scenarios by name. Its
 * {@code capabilities} tag gives the journey across versions its own CI shard.
 */
@Tag("capabilities")
class PlayerCapabilitiesSystemTest {
	@TestFactory
	Stream<DynamicTest> controlsAProtocolPlayerOnEveryPaperRelease() {
		String filter = System.getProperty("anvil.matrix.filter", ".*");

		return CompatibilityScenarioFactory.paperReleaseScenarios().stream()
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
		SimulatedPlayer alice = anvil.players().create("Alice");
		assertEquals("external:hello", alice.capability(FixtureAgentProbeProvider.Probe.class).echo("server", "hello"));
		Session session = alice.capability(Session.class);
		Messages messages = alice.capability(Messages.class);
		Server server = alice.capability(Server.class);
		Movement movement = alice.capability(Movement.class);
		Inventory inventory = alice.capability(Inventory.class);
		Interaction interaction = alice.capability(Interaction.class);

		session.connect();
		session.connected(Duration.ofSeconds(20));
		messages.received("anvil:welcome", Duration.ofSeconds(20));

		messages.command("anvil-fixture ping");
		messages.received("anvil:pong", Duration.ofSeconds(10));

		messages.chat("hello-from-anvil");
		messages.received("hello-from-anvil", Duration.ofSeconds(10));

		PlayerIdentity identity = server.joined("server", Duration.ofSeconds(10));
		assertEquals("Alice", identity.getUsername());
		assertEquals("server", identity.getRoute().getServer());
		assertEquals("Alice", identity.getObservedUsername());
		assertNotNull(server.identity());

		messages.command("anvil-fixture position");
		String[] position = message(messages, "anvil:position:").split(":");
		movement.move(Position.builder()
				.x(Double.parseDouble(position[2]) + 0.15D)
				.y(Double.parseDouble(position[3]))
				.z(Double.parseDouble(position[4]))
				.yaw(Float.parseFloat(position[5]))
				.pitch(Float.parseFloat(position[6]))
				.onGround(true)
				.build());
		messages.received("anvil:move", Duration.ofSeconds(10));

		messages.command("anvil-fixture item");
		messages.received("anvil:item:diamond", Duration.ofSeconds(10));
		inventory.selectSlot(0);
		interaction.useItem(Hand.MAIN);

		messages.command("anvil-fixture block");
		String[] block = message(messages, "anvil:block-position:").split(":");
		interaction.block(
				BlockPosition.builder()
						.x(Integer.parseInt(block[2]))
						.y(Integer.parseInt(block[3]))
						.z(Integer.parseInt(block[4]))
						.build(),
				BlockFace.UP,
				Hand.MAIN
		);
		messages.received("anvil:block:minecraft:diamond_block", Duration.ofSeconds(10));

		messages.command("anvil-fixture entity");
		int entityId = Integer.parseInt(message(messages, "anvil:entity-id:")
				.substring("anvil:entity-id:".length()));
		interaction.entity(entityId, EntityInteraction.INTERACT, Hand.MAIN);
		messages.received("anvil:entity:" + entityId, Duration.ofSeconds(10));

		messages.command("anvil-fixture gui");
		inventory.matching(snapshot -> snapshot.getContainerId() != 0, Duration.ofSeconds(10));
		inventory.click(4, InventoryClick.LEFT, 0);
		messages.received("anvil:click:4", Duration.ofSeconds(10));

		messages.command("anvil-fixture kick");
		assertEquals("anvil:requested-kick", session.kicked(Duration.ofSeconds(10)));
		assertTrue(session.state().kickReason().contains("anvil:requested-kick"));
		session.rejoin();
		session.connected(Duration.ofSeconds(20));
		alice.destroy();
		assertTrue(alice.state().destroyed());
		assertTrue(anvil.players().all().isEmpty());
	}

	private String message(Messages messages, String prefix) {
		return messages.received(prefix, Duration.ofSeconds(10));
	}
}
