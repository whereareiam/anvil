package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A container a watch found, read from the player's screen history by its number.
 */
final class ChannelScreen implements Screen {
	private final ChannelInventory inventory;
	private final ProtocolChannel channel;
	private final ScreenHistory history;
	private final int screen;

	/**
	 * Follows one screen of the history.
	 *
	 * @param inventory the inventory that clicks
	 * @param channel the player's channel, which waits
	 * @param history the player's screens
	 * @param screen the screen's number, which the history lists
	 */
	ChannelScreen(
			@NotNull ChannelInventory inventory,
			@NotNull ProtocolChannel channel,
			@NotNull ScreenHistory history,
			int screen
	) {
		this.inventory = inventory;
		this.channel = channel;
		this.history = history;
		this.screen = screen;
	}

	@Override
	public @NotNull Text title() {
		return Objects.requireNonNull(snapshot().getTitle());
	}

	@Override
	public @NotNull String menuType() {
		return Objects.requireNonNull(snapshot().getMenuType());
	}

	@Override
	public @NotNull InventorySnapshot snapshot() {
		return Objects.requireNonNull(history.screen(screen));
	}

	@Override
	public boolean open() {
		return history.latest().getScreen() == screen;
	}

	@Override
	public @NotNull ItemLocator items() {
		return new ChannelItemLocator(inventory, channel, this::snapshot, this::open,
				"in the screen '" + title().plain() + "'");
	}

	@Override
	public @NotNull String toString() {
		return "screen '" + title().plain() + "' (" + menuType() + ")";
	}
}
