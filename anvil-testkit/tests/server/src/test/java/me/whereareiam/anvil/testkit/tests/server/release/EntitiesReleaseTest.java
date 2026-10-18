package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.entities.Entities;
import me.whereareiam.anvil.capability.entities.EntityQuery;
import me.whereareiam.anvil.capability.entities.EntityWatch;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sees text appear above the player and disappear again, as a chat bubble plugin shows it, and a dropped item with
 * its stack.
 */
@Release
class EntitiesReleaseTest {
	/**
	 * The release that added text displays.
	 */
	private static final String TEXT_DISPLAYS = "1.19.4";

	@Test
	void seesANamedArmorStandRidingThePlayer(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Entities entities = player.capability(Entities.class);
		EntityWatch shown = entities.watch();

		server.fixture("anvil-entity", player, "bubble anvil:bubble-text");
		int id = Integer.parseInt(server.reply(player, "anvil:bubble:"));

		SeenEntity bubble = shown.appeared(EntityQuery.labelled("anvil:bubble-text").and(EntityQuery.ridingPlayer()),
				Duration.ofSeconds(10));
		assertEquals(id, bubble.getId());
		assertEquals("minecraft:armor_stand", bubble.getType());
		assertEquals("anvil:bubble-text", bubble.getCustomName());
		assertTrue(bubble.isCustomNameVisible());
		assertTrue(bubble.isInvisible());
		assertTrue(entities.all().stream().anyMatch(bubble::sameEntity));
		server.fixture("anvil-entity", player, "remove " + id);
		shown.disappeared(bubble, Duration.ofSeconds(10));
	}

	@Test
	void seesAGlowingDroppedItemWithItsStack(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Entities entities = player.capability(Entities.class);
		EntityWatch shown = entities.watch();

		server.fixture("anvil-entity", player, "drop");
		int id = Integer.parseInt(server.reply(player, "anvil:dropped:"));

		SeenEntity dropped = shown.appeared(EntityQuery.ofType("item").and(SeenEntity::isGlowing)
				.and(entity -> entity.getItem() != null), Duration.ofSeconds(10));
		assertEquals(id, dropped.getId());
		assertEquals(3, dropped.getItem().getAmount());
		EntityQuery nearItem = EntityQuery.ofType("item").and(EntityQuery.nearPlayer(4));
		SeenEntity near = entities.present(nearItem, Duration.ofSeconds(10));
		assertEquals(id, near.getId());
		server.fixture("anvil-entity", player, "remove " + id);
		shown.disappeared(dropped, Duration.ofSeconds(10));
	}

	@Test
	void seesATextDisplay(ScenarioContext anvil) {
		assumeTrue(TestedRelease.isAtLeast(TEXT_DISPLAYS), "Text displays arrived in " + TEXT_DISPLAYS);
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		EntityWatch shown = player.capability(Entities.class).watch();

		server.fixture("anvil-entity", player, "display anvil:display-text");

		SeenEntity display = shown.appeared(EntityQuery.labelled("anvil:display-text").and(EntityQuery.nearPlayer(4)),
				Duration.ofSeconds(10));
		assertEquals("minecraft:text_display", display.getType());
		assertEquals("anvil:display-text", display.getText());
		server.fixture("anvil-entity", player, "remove " + display.getId());
		shown.disappeared(display, Duration.ofSeconds(10));
	}

	@Test
	void showsNoTextDisplayBeforeTheyExisted(ScenarioContext anvil) {
		assumeFalse(TestedRelease.isAtLeast(TEXT_DISPLAYS), "Text displays exist since " + TEXT_DISPLAYS);
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();

		server.fixture("anvil-entity", player, "display anvil:display-text");

		player.capability(Messages.class).received("anvil:display:unsupported", Duration.ofSeconds(10));
	}
}
