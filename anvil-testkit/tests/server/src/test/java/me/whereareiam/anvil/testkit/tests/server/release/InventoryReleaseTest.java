package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Sees a container the server opens and clicks one of its slots.
 */
@Release
class InventoryReleaseTest {
	@Test
	void clicksASlotOfAnOpenedContainer(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Inventory inventory = player.capability(Inventory.class);
		Messages messages = player.capability(Messages.class);

		messages.command("anvil-fixture gui");
		inventory.matching(snapshot -> snapshot.getContainerId() != 0, Duration.ofSeconds(10));
		inventory.click(4, InventoryClick.LEFT, 0);

		messages.received("anvil:click:4", Duration.ofSeconds(10));
	}
}
