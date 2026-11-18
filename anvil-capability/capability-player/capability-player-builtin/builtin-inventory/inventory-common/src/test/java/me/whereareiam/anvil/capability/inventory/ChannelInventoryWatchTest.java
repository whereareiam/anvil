package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.ItemDisplay;
import me.whereareiam.anvil.capability.inventory.model.ItemType;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.inventory.type.ItemNameSource;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelInventoryWatchTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final InventoryItem BUY = InventoryItem.builder()
			.slot(11)
			.type(new ItemType("minecraft:emerald", 800))
			.amount(1)
			.display(ItemDisplay.builder().name(Text.literal("Buy")).nameSource(ItemNameSource.CUSTOM).build())
			.build();
	private static final InventoryItem YES = InventoryItem.builder()
			.slot(2)
			.type(new ItemType("minecraft:lime_wool", 900))
			.amount(1)
			.display(ItemDisplay.builder().name(Text.literal("Yes")).nameSource(ItemNameSource.CUSTOM).build())
			.build();

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(InventoryChannel.ID));
	private final ChannelInventory inventory = new ChannelInventory(channel);

	@Test
	void findsOnlyAScreenThatOpensAfterTheWatchStarted() {
		channel.publish(InventoryChannel.CHANGED, screen(1, "Shop", BUY));
		InventoryWatch watch = inventory.watch();

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> watch.screen("Shop", TIMEOUT));
		channel.meanwhile(() -> channel.publish(InventoryChannel.CHANGED, screen(2, "Shop", BUY)));
		Screen shop = watch.screen("Shop", TIMEOUT);

		assertEquals("Player did not see a screen titled 'Shop'", failure.getMessage());
		assertEquals(screen(2, "Shop", BUY), shop.snapshot());
		assertEquals("Shop", shop.title().plain());
		assertEquals("minecraft:generic_9x3", shop.menuType());
	}

	@Test
	void followsTheScreensInTheOrderTheyOpenAndTheReturnToTheInventory() {
		channel.answer(InventoryChannel.CLICK, action -> null);
		InventoryWatch watch = inventory.watch();
		channel.publish(InventoryChannel.CHANGED, screen(1, "Shop", BUY));
		channel.publish(InventoryChannel.CHANGED, screen(2, "Confirm purchase", YES));
		channel.publish(InventoryChannel.CHANGED, InventorySnapshot.builder().containerId(0).stateId(9).screen(3).build());

		watch.screen("Shop", TIMEOUT);
		Screen confirm = watch.screen("Confirm purchase", TIMEOUT);
		watch.closed(TIMEOUT);

		assertEquals(screen(2, "Confirm purchase", YES), confirm.snapshot());
		assertThrows(IllegalStateException.class, () -> watch.screen("Shop", TIMEOUT));
		assertThrows(IllegalStateException.class, () -> watch.closed(TIMEOUT));
	}

	@Test
	void findsAScreenByAConditionOnItsLatestState() {
		InventoryWatch watch = inventory.watch();
		channel.publish(InventoryChannel.CHANGED, screen(1, "Shop"));

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> watch.screen("with a Buy button",
				snapshot -> snapshot.getItems().contains(BUY), TIMEOUT));
		channel.meanwhile(() -> channel.publish(InventoryChannel.CHANGED, screen(1, "Shop", BUY)));

		assertEquals(1, watch.screen("with a Buy button", snapshot -> snapshot.getItems().contains(BUY), TIMEOUT)
				.snapshot().getScreen());
		assertEquals("Player did not see a screen with a Buy button", failure.getMessage());
	}

	@Test
	void clicksIntoItsScreenWhileItIsOpenAndRefusesOnceAnotherReplacedIt() {
		channel.answer(InventoryChannel.CLICK, action -> null);
		InventoryWatch watch = inventory.watch();
		channel.publish(InventoryChannel.CHANGED, screen(1, "Shop", BUY));
		Screen shop = watch.screen("Shop", TIMEOUT);

		shop.items().named("Buy").click(InventoryClick.LEFT);
		channel.publish(InventoryChannel.CHANGED, screen(2, "Confirm purchase", YES));
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> shop.items().named("Buy").click(InventoryClick.LEFT));

		assertTrue(failure.getMessage().contains("the screen is no longer open"), failure.getMessage());
		assertEquals("Player could not find item named 'Buy' in the screen 'Shop': the screen is no longer open",
				failure.getMessage());
		assertFalse(shop.open());
		assertEquals(screen(1, "Shop", BUY), shop.snapshot());
		assertEquals(List.of(new InventoryAction(11, InventoryClick.LEFT, 0)), channel.payloads());
	}

	@Test
	void keepsSeveralWatchesApart() {
		InventoryWatch early = inventory.watch();
		channel.publish(InventoryChannel.CHANGED, screen(1, "Shop", BUY));
		InventoryWatch late = inventory.watch();
		channel.publish(InventoryChannel.CHANGED, screen(2, "Shop", BUY));

		assertEquals(1, early.screen("Shop", TIMEOUT).snapshot().getScreen());
		assertEquals(2, late.screen("Shop", TIMEOUT).snapshot().getScreen());
		assertEquals(2, early.screen("Shop", TIMEOUT).snapshot().getScreen());
	}

	private static InventorySnapshot screen(int screen, String title, InventoryItem... items) {
		return InventorySnapshot.builder()
				.containerId(screen + 1)
				.stateId(1)
				.screen(screen)
				.menuType("minecraft:generic_9x3")
				.title(Text.literal(title))
				.items(List.of(items))
				.build();
	}
}
