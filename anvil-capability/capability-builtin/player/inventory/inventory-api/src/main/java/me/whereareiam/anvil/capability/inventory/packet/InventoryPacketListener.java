package me.whereareiam.anvil.capability.inventory.packet;

import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Receives the inventory state that one release's packets carry, translated by {@link InventoryPackets} into
 * library-neutral values. Container IDs, slots and state IDs are passed on as received: the binding decides which
 * of them change its observed container, for example by ignoring the cursor slot {@code -1}. Releases before
 * Minecraft 1.17 have no state IDs and report {@code 0}.
 *
 * <pre>{@code
 * Runnable detach = packets.listen(session, new InventoryPacketListener() {
 *     public void opened(int containerId) { state.open(containerId); }
 *     public void contents(int containerId, int stateId, List<InventoryItem> items) { state.replace(containerId, stateId, items); }
 *     public void slot(int containerId, int stateId, int slot, InventoryItem item) { state.set(containerId, stateId, slot, item); }
 *     public void playerSlot(int slot, InventoryItem item) { state.setPlayerSlot(slot, item); }
 * });
 * }</pre>
 */
public interface InventoryPacketListener {
	/**
	 * Reports that the server opened a container. Its contents arrive in a later packet.
	 *
	 * @param containerId ID of the opened container
	 */
	void opened(int containerId);

	/**
	 * Reports the complete contents of a container.
	 *
	 * @param containerId ID of the container
	 * @param stateId state ID of the contents, or {@code 0} for releases without state IDs
	 * @param items the occupied slots only, each carrying its container slot
	 */
	void contents(int containerId, int stateId, @NotNull List<InventoryItem> items);

	/**
	 * Reports the new content of one container slot.
	 *
	 * @param containerId ID of the container, or a negative ID for the cursor
	 * @param stateId state ID after the change, or {@code 0} for releases without state IDs
	 * @param slot container slot, or {@code -1} for the cursor
	 * @param item new content carrying the same slot, or {@code null} when the slot is empty
	 */
	void slot(int containerId, int stateId, int slot, @Nullable InventoryItem item);

	/**
	 * Reports the new content of one slot of the player's own inventory, addressed by inventory index instead of a
	 * container slot: hotbar {@code 0..8}, main inventory {@code 9..35}, armor {@code 36..39} from feet to head and
	 * the off hand {@code 40}.
	 *
	 * @param slot inventory index
	 * @param item new content carrying the same index as its slot, or {@code null} when the slot is empty
	 */
	void playerSlot(int slot, @Nullable InventoryItem item);
}
