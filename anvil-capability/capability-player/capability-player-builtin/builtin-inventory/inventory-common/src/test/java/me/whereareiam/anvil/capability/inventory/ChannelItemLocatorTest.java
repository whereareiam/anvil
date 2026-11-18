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
import me.whereareiam.anvil.player.api.model.text.TextColor;
import me.whereareiam.anvil.player.api.model.text.TextStyle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelItemLocatorTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final InventoryItem BUY = InventoryItem.builder()
			.slot(11)
			.type(new ItemType("minecraft:emerald", 800))
			.amount(1)
			.display(ItemDisplay.builder()
					.name(Text.literal("Buy").toBuilder().style(TextStyle.builder().color(TextColor.GREEN).build()).build())
					.nameSource(ItemNameSource.CUSTOM)
					.lore(Text.literal("Costs 64 coins"))
					.lore(Text.literal("Click to buy"))
					.build())
			.build();
	private static final InventoryItem SELL = InventoryItem.builder()
			.slot(15)
			.type(new ItemType("minecraft:emerald", 800))
			.amount(1)
			.display(ItemDisplay.builder()
					.name(Text.literal("Sell"))
					.nameSource(ItemNameSource.ITEM)
					.lore(Text.literal("Pays 32 coins"))
					.build())
			.build();
	private static final InventoryItem FILLER = InventoryItem.builder()
			.slot(0)
			.type(new ItemType("minecraft:gray_stained_glass_pane", 5))
			.amount(1)
			.build();
	private static final InventorySnapshot SHOP = InventorySnapshot.builder()
			.containerId(3)
			.stateId(4)
			.screen(1)
			.menuType("minecraft:generic_9x3")
			.title(Text.literal("Shop"))
			.items(List.of(FILLER, BUY, SELL))
			.build();

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(InventoryChannel.ID));
	private final ChannelInventory inventory = new ChannelInventory(channel);

	@Test
	void findsTheOneItemOfATypeWithANameAndLoreAndClicksItsSlot() {
		channel.answer(InventoryChannel.CLICK, action -> null);
		channel.publish(InventoryChannel.CHANGED, SHOP);

		inventory.items().type("minecraft:emerald").named("Buy").loreContaining("64 coins").click(InventoryClick.LEFT);

		assertEquals(List.of(new InventoryAction(11, InventoryClick.LEFT, 0)), channel.payloads());
	}

	@Test
	void matchesTheItemsOwnNameByItsPlainTextOrItsStyle() {
		channel.publish(InventoryChannel.CHANGED, SHOP);

		assertEquals(List.of(BUY), inventory.items().named("Buy").all());
		assertEquals(List.of(SELL), inventory.items().named("Sell").all());
		assertEquals(List.of(BUY), inventory.items().nameContaining("u").all());
		assertEquals(List.of(BUY), inventory.items()
				.named("green", name -> name.getStyle().getColor() == TextColor.GREEN)
				.all());
		assertEquals(List.of(), inventory.items().named("Bu").all());
	}

	@Test
	void matchesALineOfLoreExactlyOrInPart() {
		channel.publish(InventoryChannel.CHANGED, SHOP);

		assertEquals(List.of(BUY), inventory.items().lore("Click to buy").all());
		assertEquals(List.of(SELL), inventory.items().loreContaining("32").all());
		assertEquals(List.of(BUY, SELL), inventory.items().loreContaining("coins").all());
		assertEquals(List.of(), inventory.items().lore("Click").all());
	}

	@Test
	void findsAnItemWithoutANameOfItsOwnOnlyByItsType() {
		channel.publish(InventoryChannel.CHANGED, SHOP);

		assertEquals(List.of(FILLER), inventory.items().type("minecraft:gray_stained_glass_pane").all());
		assertEquals(List.of(FILLER), inventory.items().matching("in the first slot", item -> item.getSlot() == 0).all());
		assertEquals(3, inventory.items().count());
	}

	@Test
	void refusesToFindATypeWhenTheItemsHaveNone() {
		channel.publish(InventoryChannel.CHANGED, SHOP.toBuilder()
				.clearItems()
				.item(BUY.toBuilder().type(new ItemType(null, 800)).build())
				.build());

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> inventory.items().type("minecraft:emerald").all());

		assertEquals("Cannot find an item of type minecraft:emerald: the player's items have no type, because Anvil ships"
				+ " no item data for its protocol", failure.getMessage());
		assertEquals(List.of(BUY.toBuilder().type(new ItemType(null, 800)).build()), inventory.items().named("Buy").all());
	}

	@Test
	void waitsUntilTheItemAppears() {
		channel.meanwhile(() -> channel.publish(InventoryChannel.CHANGED, SHOP));

		assertEquals(BUY, inventory.items().named("Buy").item(TIMEOUT));
		assertEquals(List.of("find exactly one item named 'Buy' on the player's screen"), channel.waits());
	}

	@Test
	void failsWhenNoItemMatches() {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> inventory.items().named("Buy").item(TIMEOUT));

		assertEquals("Player did not find exactly one item named 'Buy' on the player's screen", failure.getMessage());
	}

	@Test
	void refusesSeveralMatchesNamingTheirSlotsUntilOneIsPicked() {
		channel.answer(InventoryChannel.CLICK, action -> null);
		channel.publish(InventoryChannel.CHANGED, SHOP);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> inventory.items().type("minecraft:emerald").item(TIMEOUT));
		inventory.items().type("minecraft:emerald").nth(1).click(InventoryClick.SHIFT_LEFT);

		assertEquals("Player found 2 items of type minecraft:emerald on the player's screen, in the slots 11, 15; narrow"
				+ " the locator, or pick one with first() or nth(int)", failure.getMessage());
		assertEquals(BUY, inventory.items().type("minecraft:emerald").first().item(TIMEOUT));
		assertEquals(List.of(new InventoryAction(15, InventoryClick.SHIFT_LEFT, 0)), channel.payloads());
		assertTrue(inventory.items().type("minecraft:emerald").nth(2).all().isEmpty());
	}

	@Test
	void picksOnceAndOnlyAfterItsConditions() {
		ItemLocator first = inventory.items().first();

		assertThrows(IllegalArgumentException.class, () -> first.nth(1));
		assertThrows(IllegalArgumentException.class, () -> first.named("Buy"));
		assertThrows(IllegalArgumentException.class, () -> inventory.items().nth(-1));
	}

	@Test
	void namesItsConditionsAndPickInFailures() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> inventory.items()
				.type("minecraft:emerald")
				.lore("Click to buy")
				.nth(1)
				.item(TIMEOUT));

		assertEquals("Player did not find exactly one item of type minecraft:emerald, with the lore line 'Click to buy'"
				+ " (match 2 in slot order) on the player's screen", failure.getMessage());
	}
}
