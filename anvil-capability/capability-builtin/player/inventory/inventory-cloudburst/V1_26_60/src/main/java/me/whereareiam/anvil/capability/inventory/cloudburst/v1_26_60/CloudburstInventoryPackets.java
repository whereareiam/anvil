package me.whereareiam.anvil.capability.inventory.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory packets of CloudburstMC Protocol releases from the one that serves Bedrock 1.26.60, for every codec the
 * release has. Bedrock has no state IDs, so every state ID is {@code 0}.
 *
 * <p>Bedrock keeps the player's own inventory in three containers: the inventory (container {@code 0}, hotbar
 * {@code 0..8} and main inventory {@code 9..35}), the off hand (container {@code 119}) and the armor (container
 * {@code 120}, head to feet). Their slots are reported as inventory indexes. Containers the server opens use the
 * IDs {@code 1..100} and are reported with the slots they arrive with; the cursor and the other fixed user-interface
 * containers are not reported.</p>
 *
 * <p>Selecting a hotbar slot goes through the session, which sends the mob equipment packet with the slot's item as
 * a client does. A click is computed from the item stacks the session tracks, the clicked slot's and the cursor's
 * (container {@code 124}, slot {@code 0}), and sent the way the game start told the client to move items: as an item
 * stack request of take, place, swap or drop actions that name each stack by its network ID when the server is
 * authoritative over the inventory, otherwise as a normal inventory transaction that lists the old and new item of
 * each slot it changes. A click on the player's inventory container uses the slots the inventory binding shows,
 * hotbar {@code 36..44}, main inventory {@code 9..35}, armor {@code 5..8} from head to feet and the off hand
 * {@code 45}; a click on an opened container uses its own slots.</p>
 *
 * <ul>
 *     <li>{@link InventoryClick#LEFT} takes the whole stack to an empty cursor, places the whole cursor into an empty
 *     slot, and swaps cursor and slot when they hold different items.</li>
 *     <li>{@link InventoryClick#RIGHT} takes half of the stack, rounded up, to an empty cursor, places one item of the
 *     cursor into an empty slot, and swaps cursor and slot when they hold different items.</li>
 *     <li>{@link InventoryClick#DROP_ONE} and {@link InventoryClick#DROP_STACK} drop one item or the whole stack of the
 *     slot.</li>
 *     <li>{@link InventoryClick#HOTBAR_SWAP} swaps the slot with the hotbar slot or off hand the button names, or
 *     moves the stack when one of them is empty.</li>
 * </ul>
 *
 * <p>A click that would change nothing, such as a left click on an empty slot with an empty cursor, sends nothing,
 * as a client sends nothing for it. The click is refused with an {@link UnsupportedOperationException} when it would
 * stack the cursor onto a slot of the same item, because the session does not know an item's maximum stack size, for
 * {@link InventoryClick#SHIFT_LEFT}, whose destination a Bedrock client chooses from the screen it shows, and for a
 * slot of an opened container when the server is authoritative over the inventory, because an item stack request
 * names a container by a slot type that the open packet's container type does not tell apart for chests, barrels and
 * shulker boxes. A crafting slot of the player's inventory container is refused with an
 * {@link IllegalArgumentException}, as Bedrock has none there. The click's state ID, action ID and clicked item are
 * Java Edition values that Bedrock clicks ignore.</p>
 */
public final class CloudburstInventoryPackets implements InventoryPackets<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> {
	private static final int INVENTORY_SIZE = 36;
	private static final int OFF_HAND_INDEX = 40;
	private static final int HEAD_INDEX = 39;
	private static final int ARMOR_SIZE = 4;
	private static final int HOTBAR_SIZE = 9;
	private static final int FIRST_ARMOR_SLOT = 5;
	private static final int FIRST_HOTBAR_SLOT = 36;
	private static final int OFF_HAND_SLOT = 45;
	private static final Slot CURSOR = new Slot(ContainerId.UI, 0);

	@Override
	@SuppressWarnings("unchecked")
	public @NotNull Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> sessionType() {
		return (Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>>) (Class<?>) CloudburstSession.class;
	}

	/**
	 * Selects a hotbar slot through the session, which sends the mob equipment packet of the player's own entity
	 * with the slot's item.
	 */
	@Override
	public void selectHotbar(@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, int slot) {
		session.select(slot);
	}

	/**
	 * Sends one click as an item stack request or an inventory transaction, as the game start told the client.
	 *
	 * @throws UnsupportedOperationException for a shift click, a click that would stack the cursor onto the same item
	 * and, under server inventory authority, a click on an opened container
	 * @throws IllegalArgumentException for a slot that the player's inventory container has no Bedrock slot for
	 */
	@Override
	public void click(@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, @NotNull ContainerClick click) {
		Move move = move(session, click);
		if (move == null) return;

		if (!session.serverAuthoritativeInventory()) {
			session.send(transaction(move));
			return;
		}

		ItemStackRequestPacket request = new ItemStackRequestPacket();
		request.getRequests().add(new ItemStackRequest(session.nextItemStackRequestId(), new ItemStackRequestAction[]{action(move)}, new String[0]));
		session.send(request);
	}

	@Override
	public @NotNull Runnable listen(
			@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session,
			@NotNull InventoryPacketListener listener
	) {
		List<PacketSubscription> subscriptions = List.of(
				session.subscribe(ContainerOpenPacket.class, open -> opened(open, listener)),
				session.subscribe(InventoryContentPacket.class, content -> contents(content, listener)),
				session.subscribe(InventorySlotPacket.class, slot -> slot(slot, listener))
		);
		return () -> subscriptions.forEach(PacketSubscription::close);
	}

	/**
	 * Computes what a click moves from the stacks the session tracks.
	 *
	 * @return the move, or {@code null} when the click changes nothing
	 */
	private static @Nullable Move move(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, ContainerClick click) {
		if (click.getClick() == InventoryClick.SHIFT_LEFT)
			throw new UnsupportedOperationException("A Bedrock client moves a shift-clicked stack to a destination it chooses from the screen it shows, "
					+ "which a click on one slot does not name");

		Slot slot = target(click);
		if (session.serverAuthoritativeInventory() && slotType(slot) == null)
			throw new UnsupportedOperationException("An item stack request names an opened container by a slot type that the container's open packet "
					+ "does not tell apart for chests, barrels and shulker boxes, so the player cannot click container " + slot.containerId());

		ItemData stack = stack(session, slot);
		ItemData cursor = stack(session, CURSOR);
		return switch (click.getClick()) {
			case LEFT -> cursorMove(slot, stack, cursor, stack.getCount(), cursor.getCount());
			case RIGHT -> cursorMove(slot, stack, cursor, (stack.getCount() + 1) / 2, 1);
			case DROP_ONE -> stack.isValid() ? new Move(Kind.DROP, slot, slot, 1, stack, ItemData.AIR) : null;
			case DROP_STACK -> stack.isValid() ? new Move(Kind.DROP, slot, slot, stack.getCount(), stack, ItemData.AIR) : null;
			case HOTBAR_SWAP -> swap(session, slot, stack, click.getButton());
			case SHIFT_LEFT -> throw new IllegalStateException("A shift click was refused above");
		};
	}

	/**
	 * Moves stacks between a slot and the cursor: takes {@code take} items of the slot to an empty cursor, places
	 * {@code place} items of the cursor into an empty slot, and swaps different items.
	 */
	private static @Nullable Move cursorMove(Slot slot, ItemData stack, ItemData cursor, int take, int place) {
		if (!cursor.isValid()) return stack.isValid() ? new Move(Kind.TAKE, slot, CURSOR, take, stack, cursor) : null;
		if (!stack.isValid()) return new Move(Kind.PLACE, CURSOR, slot, place, cursor, stack);
		if (cursor.equals(stack, false, true, true))
			throw new UnsupportedOperationException("Stacking the cursor onto the same item needs the item's maximum stack size, "
					+ "which the Bedrock session does not know");

		return new Move(Kind.SWAP, CURSOR, slot, cursor.getCount(), cursor, stack);
	}

	private static @Nullable Move swap(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, Slot slot, ItemData stack, int button) {
		Slot other = button == ContainerClick.OFF_HAND_BUTTON ? new Slot(ContainerId.OFFHAND, 0) : new Slot(ContainerId.INVENTORY, button);
		ItemData held = stack(session, other);
		if (slot.equals(other) || !stack.isValid() && !held.isValid()) return null;
		if (!held.isValid()) return new Move(Kind.PLACE, slot, other, stack.getCount(), stack, held);
		if (!stack.isValid()) return new Move(Kind.PLACE, other, slot, held.getCount(), held, stack);

		return new Move(Kind.SWAP, slot, other, stack.getCount(), stack, held);
	}

	/**
	 * Maps the clicked slot to the Bedrock container and slot that hold it.
	 */
	private static Slot target(ContainerClick click) {
		int containerId = click.getContainerId();
		int slot = click.getSlot();
		if (serverContainer(containerId)) return new Slot(containerId, slot);
		if (containerId != ContainerId.INVENTORY) throw new IllegalArgumentException("Bedrock has no container " + containerId + " to click");
		if (slot >= FIRST_ARMOR_SLOT && slot < FIRST_ARMOR_SLOT + ARMOR_SIZE) return new Slot(ContainerId.ARMOR, slot - FIRST_ARMOR_SLOT);
		if (slot >= HOTBAR_SIZE && slot < INVENTORY_SIZE) return new Slot(ContainerId.INVENTORY, slot);
		if (slot >= FIRST_HOTBAR_SLOT && slot < FIRST_HOTBAR_SLOT + HOTBAR_SIZE) return new Slot(ContainerId.INVENTORY, slot - FIRST_HOTBAR_SLOT);
		if (slot == OFF_HAND_SLOT) return new Slot(ContainerId.OFFHAND, 0);

		throw new IllegalArgumentException("Slot " + slot + " of the player's inventory has no Bedrock slot: a Bedrock player's inventory has no crafting slots");
	}

	private static ItemData stack(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, Slot slot) {
		return session.item(slot.containerId(), slot.slot()).orElse(ItemData.AIR);
	}

	/**
	 * Lists the old and new item of each slot a move changes, and the dropped item as a world interaction.
	 */
	private static InventoryTransactionPacket transaction(Move move) {
		InventoryTransactionPacket packet = new InventoryTransactionPacket();
		packet.setTransactionType(InventoryTransactionType.NORMAL);
		List<InventoryActionData> actions = packet.getActions();
		ItemData moved = move.from().toBuilder().count(move.count()).build();
		switch (move.kind()) {
			case TAKE, PLACE -> {
				actions.add(change(move.source(), move.from(), remainder(move.from(), move.count())));
				actions.add(change(move.destination(), move.to(), moved));
			}
			case SWAP -> {
				actions.add(change(move.source(), move.from(), move.to()));
				actions.add(change(move.destination(), move.to(), move.from()));
			}
			case DROP -> {
				actions.add(change(move.source(), move.from(), remainder(move.from(), move.count())));
				actions.add(new InventoryActionData(InventorySource.fromWorldInteraction(InventorySource.Flag.DROP_ITEM), 0, ItemData.AIR, moved));
			}
		}
		return packet;
	}

	private static InventoryActionData change(Slot slot, ItemData from, ItemData to) {
		return new InventoryActionData(InventorySource.fromContainerWindowId(slot.containerId()), slot.slot(), from, to);
	}

	private static ItemData remainder(ItemData stack, int removed) {
		int count = stack.getCount() - removed;
		return count > 0 ? stack.toBuilder().count(count).build() : ItemData.AIR;
	}

	private static ItemStackRequestAction action(Move move) {
		ItemStackRequestSlotData source = slotData(move.source(), move.from());
		ItemStackRequestSlotData destination = slotData(move.destination(), move.to());
		return switch (move.kind()) {
			case TAKE -> new TakeAction(move.count(), source, destination);
			case PLACE -> new PlaceAction(move.count(), source, destination);
			case SWAP -> new SwapAction(source, destination);
			case DROP -> new DropAction(move.count(), source, false);
		};
	}

	@SuppressWarnings("deprecation")
	private static ItemStackRequestSlotData slotData(Slot slot, ItemData stack) {
		ContainerSlotType type = slotType(slot);
		return new ItemStackRequestSlotData(type, slot.slot(), stack.isValid() ? stack.getNetId() : 0, new FullContainerName(type, null));
	}

	/**
	 * Returns the slot type an item stack request names a slot of the player's own containers or the cursor by.
	 *
	 * @return the slot type, or {@code null} for a slot of an opened container
	 */
	private static @Nullable ContainerSlotType slotType(Slot slot) {
		return switch (slot.containerId()) {
			case ContainerId.INVENTORY -> slot.slot() < HOTBAR_SIZE ? ContainerSlotType.HOTBAR : ContainerSlotType.INVENTORY;
			case ContainerId.OFFHAND -> ContainerSlotType.OFFHAND;
			case ContainerId.ARMOR -> ContainerSlotType.ARMOR;
			case ContainerId.UI -> ContainerSlotType.CURSOR;
			default -> null;
		};
	}

	private static void opened(ContainerOpenPacket open, InventoryPacketListener listener) {
		// The player's own inventory is always observed; opening it brings no contents of its own.
		if (serverContainer(open.getId())) listener.opened(open.getId());
	}

	private static void contents(InventoryContentPacket content, InventoryPacketListener listener) {
		int containerId = content.getContainerId();
		List<ItemData> stacks = content.getContents();
		if (playerContainer(containerId)) {
			for (int slot = 0; slot < stacks.size(); slot++)
				playerSlot(containerId, slot, stacks.get(slot), listener);
			return;
		}
		if (!serverContainer(containerId)) return;

		List<InventoryItem> items = new ArrayList<>();
		for (int slot = 0; slot < stacks.size(); slot++) {
			InventoryItem item = item(slot, stacks.get(slot));
			if (item != null) items.add(item);
		}
		listener.contents(containerId, 0, items);
	}

	private static void slot(InventorySlotPacket packet, InventoryPacketListener listener) {
		int containerId = packet.getContainerId();
		if (playerContainer(containerId)) {
			playerSlot(containerId, packet.getSlot(), packet.getItem(), listener);
			return;
		}
		if (!serverContainer(containerId)) return;

		listener.slot(containerId, 0, packet.getSlot(), item(packet.getSlot(), packet.getItem()));
	}

	private static void playerSlot(int containerId, int slot, @Nullable ItemData stack, InventoryPacketListener listener) {
		int index = inventoryIndex(containerId, slot);
		if (index < 0) return;

		listener.playerSlot(index, item(index, stack));
	}

	/**
	 * Maps a slot of one of the player's own containers to its inventory index.
	 *
	 * @return the inventory index, or {@code -1} for a slot the container does not have
	 */
	private static int inventoryIndex(int containerId, int slot) {
		if (slot < 0) return -1;

		return switch (containerId) {
			case ContainerId.INVENTORY -> slot < INVENTORY_SIZE ? slot : -1;
			case ContainerId.OFFHAND -> slot == 0 ? OFF_HAND_INDEX : -1;
			case ContainerId.ARMOR -> slot < ARMOR_SIZE ? HEAD_INDEX - slot : -1;
			default -> -1;
		};
	}

	private static boolean playerContainer(int containerId) {
		return containerId == ContainerId.INVENTORY || containerId == ContainerId.OFFHAND || containerId == ContainerId.ARMOR;
	}

	private static boolean serverContainer(int containerId) {
		return containerId >= ContainerId.FIRST && containerId <= ContainerId.LAST;
	}

	private static @Nullable InventoryItem item(int slot, @Nullable ItemData stack) {
		if (stack == null || !stack.isValid()) return null;

		ItemDefinition definition = stack.getDefinition();
		return InventoryItem.builder()
				.slot(slot)
				.protocolId(definition.getRuntimeId())
				.amount(stack.getCount())
				.identifier(definition.getIdentifier())
				.build();
	}

	/**
	 * What a click does with the stacks.
	 */
	private enum Kind {
		/**
		 * Moves items of a slot to the cursor.
		 */
		TAKE,
		/**
		 * Moves items into an empty slot.
		 */
		PLACE,
		/**
		 * Exchanges two stacks.
		 */
		SWAP,
		/**
		 * Drops items of a slot.
		 */
		DROP
	}

	/**
	 * One container slot.
	 */
	private record Slot(int containerId, int slot) {
	}

	/**
	 * One move of a click: {@code count} items of the {@code from} stack in the source go to the destination, which
	 * held {@code to}; a swap exchanges both stacks and a drop has no destination of its own.
	 */
	private record Move(Kind kind, Slot source, Slot destination, int count, ItemData from, ItemData to) {
	}
}
