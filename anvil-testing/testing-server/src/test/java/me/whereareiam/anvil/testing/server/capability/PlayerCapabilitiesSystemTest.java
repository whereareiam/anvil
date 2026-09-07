package me.whereareiam.anvil.testing.server.capability;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
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
import me.whereareiam.anvil.junit.AnvilTest;
import me.whereareiam.anvil.testing.server.extension.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testing.server.scenario.Paper12111SystemScenario;
import me.whereareiam.anvil.testing.server.scenario.Paper2612SystemScenario;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCapabilitiesSystemTest {
	@Test
	@AnvilTest(Paper12111SystemScenario.class)
	void controlsAProtocolPlayerOnPaper12111(ScenarioContext anvil) {
		verify(anvil);
	}

	@Test
	@AnvilTest(Paper2612SystemScenario.class)
	void controlsAProtocolPlayerOnPaper2612(ScenarioContext anvil) {
		verify(anvil);
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
		assertEquals("server", identity.getServer());
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
