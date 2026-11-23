package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.player.api.channel.ProtocolChannel;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Locator over the screen states a supplier gives: the screen the player sees, or one screen a watch found, which
 * stops acting once another screen replaced it. Matching runs on the host against the latest snapshot, and a click
 * goes to the inventory with the slot it found.
 */
final class ChannelItemLocator implements ItemLocator {
	private final ChannelInventory inventory;
	private final ProtocolChannel channel;
	private final Supplier<InventorySnapshot> screen;
	private final BooleanSupplier open;
	private final String where;
	private final List<Condition> conditions;
	private final @Nullable Integer pick;

	/**
	 * Creates a locator of every item of a screen.
	 *
	 * @param inventory the inventory that clicks
	 * @param channel the player's channel, which waits
	 * @param screen supplies the screen's latest state
	 * @param open tells whether the screen is still the one the player sees
	 * @param where names the screen in failures, such as {@code in the screen 'Shop'}
	 */
	ChannelItemLocator(
			@NotNull ChannelInventory inventory,
			@NotNull ProtocolChannel channel,
			@NotNull Supplier<InventorySnapshot> screen,
			@NotNull BooleanSupplier open,
			@NotNull String where
	) {
		this(inventory, channel, screen, open, where, List.of(), null);
	}

	private ChannelItemLocator(
			ChannelInventory inventory,
			ProtocolChannel channel,
			Supplier<InventorySnapshot> screen,
			BooleanSupplier open,
			String where,
			List<Condition> conditions,
			@Nullable Integer pick
	) {
		this.inventory = inventory;
		this.channel = channel;
		this.screen = screen;
		this.open = open;
		this.where = where;
		this.conditions = conditions;
		this.pick = pick;
	}

	@Override
	public @NotNull ItemLocator type(@NotNull String identifier) {
		return with("of type " + identifier, item -> {
			String key = item.type().identifier();
			if (key == null)
				throw new IllegalStateException("Cannot find an item of type " + identifier + ": the player's items have no "
						+ "type, because Anvil ships no item data for its protocol");

			return identifier.equals(key);
		});
	}

	@Override
	public @NotNull ItemLocator named(@NotNull String name) {
		return named("named '" + name + "'", text -> name.equals(text.plain()));
	}

	@Override
	public @NotNull ItemLocator nameContaining(@NotNull String text) {
		return named("with a name containing '" + text + "'", name -> name.plain().contains(text));
	}

	@Override
	public @NotNull ItemLocator named(@NotNull String description, @NotNull Predicate<Text> name) {
		return with(description, item -> item.display().name() != null && name.test(item.display().name()));
	}

	@Override
	public @NotNull ItemLocator lore(@NotNull String line) {
		return lore("with the lore line '" + line + "'", text -> line.equals(text.plain()));
	}

	@Override
	public @NotNull ItemLocator loreContaining(@NotNull String text) {
		return lore("with lore containing '" + text + "'", line -> line.plain().contains(text));
	}

	@Override
	public @NotNull ItemLocator lore(@NotNull String description, @NotNull Predicate<Text> line) {
		return with(description, item -> item.display().lore().stream().anyMatch(line));
	}

	@Override
	public @NotNull ItemLocator matching(@NotNull String description, @NotNull Predicate<InventoryItem> item) {
		return with(description, item);
	}

	@Override
	public @NotNull ItemLocator nth(int index) {
		if (index < 0) throw new IllegalArgumentException("A locator picks no item at index " + index);
		if (pick != null) throw new IllegalArgumentException("The locator already picks the item at index " + pick);

		return new ChannelItemLocator(inventory, channel, screen, open, where, conditions, index);
	}

	@Override
	public @NotNull List<InventoryItem> all() {
		List<InventoryItem> matches = screen.get().items().stream()
				.filter(item -> conditions.stream().allMatch(condition -> condition.item().test(item)))
				.toList();
		if (pick == null) return matches;

		return matches.size() > pick ? List.of(matches.get(pick)) : List.of();
	}

	@Override
	public @NotNull InventoryItem item(@NotNull Duration timeout) {
		String described = describe();
		try {
			channel.await(() -> !open.getAsBoolean() || all().size() == 1, "find exactly one " + described, timeout);
		} catch (IllegalStateException failure) {
			List<InventoryItem> matches = all();
			if (matches.size() < 2) throw failure;

			throw new IllegalStateException("Player found " + matches.size() + " items" + conditions() + " " + where
					+ ", in the slots " + slots(matches) + "; narrow the locator, or pick one with first() or nth(int)",
					failure);
		}
		if (!open.getAsBoolean())
			throw new IllegalStateException("Player could not find " + described + ": the screen is no longer open");

		return all().getFirst();
	}

	@Override
	public void click(@NotNull InventoryClick click, int button) {
		InventoryItem item = item(SimulatedPlayer.DEFAULT_TIMEOUT);
		inventory.click(item.slot(), click, button);
	}

	@Override
	public @NotNull String toString() {
		return describe();
	}

	private ItemLocator with(String description, Predicate<InventoryItem> item) {
		if (pick != null) throw new IllegalArgumentException("Add conditions before picking one of the matching items");

		List<Condition> narrowed = new ArrayList<>(conditions);
		narrowed.add(new Condition(description, item));
		return new ChannelItemLocator(inventory, channel, screen, open, where, List.copyOf(narrowed), null);
	}

	/**
	 * Describes the located item, such as {@code item of type minecraft:emerald, named 'Buy' in the screen 'Shop'}.
	 */
	private String describe() {
		String picked = pick == null ? "" : " (match " + (pick + 1) + " in slot order)";
		return "item" + conditions() + picked + " " + where;
	}

	/**
	 * Lists the conditions after a leading space, or nothing for a locator without conditions.
	 */
	private String conditions() {
		if (conditions.isEmpty()) return "";

		return " " + conditions.stream().map(Condition::description).collect(Collectors.joining(", "));
	}

	private static String slots(List<InventoryItem> items) {
		return items.stream().map(item -> String.valueOf(item.slot())).collect(Collectors.joining(", "));
	}

	/**
	 * One condition of the locator and how failures name it.
	 */
	private record Condition(String description, Predicate<InventoryItem> item) {
	}
}
