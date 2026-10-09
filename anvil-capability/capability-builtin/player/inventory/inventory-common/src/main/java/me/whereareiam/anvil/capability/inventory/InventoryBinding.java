package me.whereareiam.anvil.capability.inventory;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Worker-side inventory behavior over the packets of one library release: it registers hotbar selection and
 * container clicks for a player, keeps the container the player's packets describe and publishes every change of
 * it as an {@link InventorySnapshot}. The observed container, its state ID and the action IDs of clicks belong to
 * the player and survive reconnects; the packet listener belongs to one native session.
 *
 * <p>The observed container is the one the server last opened or filled. Slot updates of other containers and of
 * the cursor are ignored, and changes addressed to the player's own inventory are applied only while the player's
 * inventory container is the observed one.</p>
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class InventoryBinding<S> {
	private final @NotNull InventoryPackets<S> packets;

	/**
	 * Installs the inventory operations for one player and listens to the inventory packets of each of its native
	 * sessions.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that detaches the packet listener of the current native session
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		ObservedContainer container = new ObservedContainer();
		operations.register(InventoryOperations.SELECT, selection -> {
			packets.selectHotbar(session(player), selection.getSlot());
			return null;
		});
		operations.register(InventoryOperations.CLICK, action -> {
			click(player, container, action);
			return null;
		});

		Subscription listeners = player.bindNativeSession(nativeSession -> {
			S session = packets.sessionType().cast(nativeSession);
			Runnable detach = packets.listen(session, new ContainerListener(player, nativeSession, container));
			return detach::run;
		});
		return listeners::close;
	}

	private void click(PlayerBindingContext<Object> player, ObservedContainer container, InventoryAction action) {
		if (action.getClick() == InventoryClick.HOTBAR_SWAP && !swapButton(action.getButton()))
			throw new IllegalArgumentException("Invalid hotbar button: " + action.getButton());

		S session = session(player);
		packets.click(session, container.click(action));
	}

	private static boolean swapButton(int button) {
		return button >= 0 && button <= 8 || button == ContainerClick.OFF_HAND_BUTTON;
	}

	private S session(PlayerBindingContext<Object> player) {
		return packets.sessionType().cast(player.nativeSession());
	}

	/**
	 * Applies the packets of one native session to the player's observed container and publishes each change while
	 * that session is the player's current one.
	 */
	@RequiredArgsConstructor
	private static final class ContainerListener implements InventoryPacketListener {
		private final PlayerBindingContext<Object> player;
		private final Object nativeSession;
		private final ObservedContainer container;

		@Override
		public void opened(int containerId) {
			if (!player.isCurrentNativeSession(nativeSession)) return;

			publish(container.open(containerId));
		}

		@Override
		public void contents(int containerId, int stateId, @NotNull List<InventoryItem> items) {
			if (!player.isCurrentNativeSession(nativeSession)) return;

			publish(container.replace(containerId, stateId, items));
		}

		@Override
		public void slot(int containerId, int stateId, int slot, @Nullable InventoryItem item) {
			if (!player.isCurrentNativeSession(nativeSession)) return;

			publish(container.set(containerId, stateId, slot, item));
		}

		@Override
		public void playerSlot(int slot, @Nullable InventoryItem item) {
			if (!player.isCurrentNativeSession(nativeSession)) return;

			publish(container.setPlayerSlot(slot, item));
		}

		private void publish(@Nullable InventorySnapshot snapshot) {
			if (snapshot != null) player.emit(InventoryOperations.CHANGED, snapshot);
		}
	}

	/**
	 * The container a player observes and the IDs its clicks carry. Packet listeners and operations run on
	 * different threads, so every access holds the monitor; each change returns the snapshot to publish, or
	 * {@code null} when the packet did not change the observed container.
	 */
	private static final class ObservedContainer {
		private static final int PLAYER_INVENTORY = 0;

		private final Map<Integer, InventoryItem> items = new TreeMap<>();
		private int containerId;
		private int stateId;
		private int actionId;

		private synchronized @NotNull InventorySnapshot open(int containerId) {
			this.containerId = containerId;
			this.stateId = 0;
			items.clear();
			return snapshot();
		}

		private synchronized @NotNull InventorySnapshot replace(int containerId, int stateId, List<InventoryItem> contents) {
			this.containerId = containerId;
			this.stateId = stateId;
			items.clear();
			contents.forEach(item -> items.put(item.getSlot(), item));
			return snapshot();
		}

		private synchronized @Nullable InventorySnapshot set(int containerId, int stateId, int slot, @Nullable InventoryItem item) {
			if (containerId != this.containerId || slot < 0) return null;

			this.stateId = stateId;
			put(slot, item);
			return snapshot();
		}

		private synchronized @Nullable InventorySnapshot setPlayerSlot(int index, @Nullable InventoryItem item) {
			int slot = playerInventorySlot(index);
			if (containerId != PLAYER_INVENTORY || slot < 0) return null;

			put(slot, item == null ? null : InventoryItem.builder()
					.slot(slot)
					.protocolId(item.getProtocolId())
					.amount(item.getAmount())
					.identifier(item.getIdentifier())
					.build());
			return snapshot();
		}

		private synchronized @NotNull ContainerClick click(InventoryAction action) {
			actionId = actionId == Short.MAX_VALUE ? 1 : actionId + 1;
			return ContainerClick.builder()
					.containerId(containerId)
					.stateId(stateId)
					.actionId(actionId)
					.slot(action.getSlot())
					.click(action.getClick())
					.button(action.getButton())
					.clickedItem(picksUp(action.getClick()) ? items.get(action.getSlot()) : null)
					.build();
		}

		private void put(int slot, @Nullable InventoryItem item) {
			if (item == null) {
				items.remove(slot);
				return;
			}

			items.put(slot, item);
		}

		private InventorySnapshot snapshot() {
			return InventorySnapshot.builder()
					.containerId(containerId)
					.stateId(stateId)
					.items(items.values())
					.build();
		}

		/**
		 * Tells whether a click picks the slot's item up, so that a client predicts that item as the click's result.
		 */
		private static boolean picksUp(InventoryClick click) {
			return click == InventoryClick.LEFT || click == InventoryClick.RIGHT || click == InventoryClick.SHIFT_LEFT;
		}

		/**
		 * Maps an index of the player's inventory to its slot in the player's inventory container, which lists the
		 * crafting slots, the armor from head to feet, the main inventory, the hotbar and the off hand.
		 *
		 * @return the container slot, or {@code -1} for an index the container does not show
		 */
		private static int playerInventorySlot(int index) {
			if (index < 0) return -1;
			if (index <= 8) return 36 + index;
			if (index <= 35) return index;
			if (index <= 39) return 44 - index;
			if (index == 40) return 45;

			return -1;
		}
	}
}
