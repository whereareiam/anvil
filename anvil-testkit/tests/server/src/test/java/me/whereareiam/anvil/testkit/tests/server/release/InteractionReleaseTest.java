package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Uses the held item, a block and an entity, which the server sees as the player's interactions.
 */
@Release
class InteractionReleaseTest {
	@Test
	void usesTheItemInTheSelectedSlot(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Messages messages = player.capability(Messages.class);
		messages.command("anvil-fixture item");
		messages.received("anvil:item:diamond", Duration.ofSeconds(10));

		player.capability(Inventory.class).selectSlot(0);
		player.capability(Interaction.class).useItem(Hand.MAIN);

		messages.received("anvil:item-use:minecraft:diamond", Duration.ofSeconds(10));
	}

	@Test
	void usesABlock(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		player.capability(Messages.class).command("anvil-fixture block");
		String[] block = server.reply(player, "anvil:block-position:").split(":");

		player.capability(Interaction.class).block(
				BlockPosition.builder()
						.x(Integer.parseInt(block[0]))
						.y(Integer.parseInt(block[1]))
						.z(Integer.parseInt(block[2]))
						.build(),
				BlockFace.UP,
				Hand.MAIN
		);

		player.capability(Messages.class).received("anvil:block:minecraft:diamond_block", Duration.ofSeconds(10));
	}

	@Test
	void interactsWithAnEntity(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		player.capability(Messages.class).command("anvil-fixture entity");
		int entity = Integer.parseInt(server.reply(player, "anvil:entity-id:"));

		player.capability(Interaction.class).entity(entity, EntityInteraction.INTERACT, Hand.MAIN);

		player.capability(Messages.class).received("anvil:entity:" + entity, Duration.ofSeconds(10));
	}
}
