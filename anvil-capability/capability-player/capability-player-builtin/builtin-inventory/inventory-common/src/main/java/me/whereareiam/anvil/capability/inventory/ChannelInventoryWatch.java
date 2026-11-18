package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.player.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A watch over the player's screen history, which starts after the screen the player saw when it was created.
 */
final class ChannelInventoryWatch implements InventoryWatch {
	private final ChannelInventory inventory;
	private final ProtocolChannel channel;
	private final ScreenHistory history;
	private final HistoryCursor<InventorySnapshot> cursor;

	/**
	 * Starts a watch at the end of the history.
	 *
	 * @param inventory the inventory that clicks
	 * @param channel the player's channel, which waits
	 * @param history the player's screens
	 */
	ChannelInventoryWatch(
			@NotNull ChannelInventory inventory,
			@NotNull ProtocolChannel channel,
			@NotNull ScreenHistory history
	) {
		this.inventory = inventory;
		this.channel = channel;
		this.history = history;
		this.cursor = new HistoryCursor<>(history::screens);
	}

	@Override
	public @NotNull Screen screen(@NotNull String title, @NotNull Duration timeout) {
		return screen("titled '" + title + "'", snapshot -> title.equals(Objects.requireNonNull(snapshot.getTitle()).plain()),
				timeout);
	}

	@Override
	public @NotNull Screen screen(
			@NotNull String description,
			@NotNull Predicate<InventorySnapshot> screen,
			@NotNull Duration timeout
	) {
		InventorySnapshot found = take("see a screen " + description, snapshot -> !snapshot.playerInventory()
				&& screen.test(snapshot), timeout);
		return new ChannelScreen(inventory, channel, history, found.getScreen());
	}

	@Override
	public void closed(@NotNull Duration timeout) {
		take("see its container closed", InventorySnapshot::playerInventory, timeout);
	}

	private InventorySnapshot take(String description, Predicate<InventorySnapshot> matcher, Duration timeout) {
		channel.await(() -> cursor.find(matcher) != null, description, timeout);
		return Objects.requireNonNull(cursor.take(matcher));
	}
}
