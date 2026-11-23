package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.InventoryWatch;
import me.whereareiam.anvil.capability.inventory.ItemLocator;
import me.whereareiam.anvil.capability.inventory.Screen;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.inventory.type.ItemNameSource;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.player.api.model.text.TextColor;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Sees containers the server opens, finds their items by what the player sees of them and clicks them.
 */
@Release
class InventoryReleaseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Test
	void clicksASlotOfAnOpenedContainer(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Inventory inventory = player.capability(Inventory.class);
		Messages messages = player.capability(Messages.class);

		messages.command("anvil-fixture gui");
		inventory.matching(snapshot -> snapshot.containerId() != 0, TIMEOUT);
		inventory.click(4, InventoryClick.LEFT, 0);

		messages.received("anvil:click:4", TIMEOUT);
	}

	@Test
	void findsAndClicksNamedItemsThroughTheScreensOfAMenu(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Inventory inventory = player.capability(Inventory.class);
		Messages messages = player.capability(Messages.class);
		InventoryWatch watch = inventory.watch();

		messages.command("anvil-fixture shop");
		Screen shop = watch.screen("Anvil Shop", TIMEOUT);
		ItemLocator buy = shop.items().named("Buy").lore("Costs 64 coins");
		InventoryItem item = buy.item(TIMEOUT);

		assertEquals("minecraft:generic_9x3", shop.menuType());
		assertEquals(TextColor.GREEN, item.display().name().span("Buy").orElseThrow().style().color());
		assertEquals(ItemNameSource.CUSTOM, item.display().nameSource());
		assertEquals(List.of("Costs 64 coins", "Click to buy"), item.display().lore().stream().map(Text::plain).toList());
		assertEquals(11, item.slot());
		assertItemType("minecraft:emerald", item);
		assertEquals(25, shop.items()
				.matching("in the shop's own slots", own -> own.slot() < 27)
				.matching("without a name", unnamed -> unnamed.display().name() == null)
				.count());

		buy.click(InventoryClick.LEFT);
		Screen confirm = watch.screen("Anvil Confirm", TIMEOUT);
		confirm.items().named("Yes").click(InventoryClick.LEFT);
		watch.closed(TIMEOUT);

		messages.received("anvil:shop:bought", TIMEOUT);
		assertFalse(shop.open());
		assertFalse(confirm.open());
	}

	/**
	 * Checks an item's type where Anvil ships item data for the release's protocol; minecraft-data has none for
	 * Minecraft 26.2 and 26.3 yet, whose items therefore carry no type.
	 */
	private static void assertItemType(String identifier, InventoryItem item) {
		if (TestedRelease.isAtLeast("26.2")) {
			assertNull(item.type().identifier());
			return;
		}

		assertEquals(identifier, item.type().identifier());
	}
}
