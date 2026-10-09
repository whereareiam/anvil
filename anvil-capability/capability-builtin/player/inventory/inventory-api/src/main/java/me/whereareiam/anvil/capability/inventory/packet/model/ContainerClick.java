package me.whereareiam.anvil.capability.inventory.packet.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One click on a container slot as the inventory binding hands it to a release's packets. It carries every value
 * a release's click packet may need; each release sends the ones its protocol has: releases from Minecraft 1.17
 * send the state ID, older releases the action ID and the clicked item instead.
 *
 * <pre>{@code
 * ContainerClick click = ContainerClick.builder()
 *         .containerId(1).stateId(7).actionId(3)
 *         .slot(4).click(InventoryClick.LEFT).button(0)
 *         .clickedItem(InventoryItem.builder().slot(4).protocolId(1).amount(3).build())
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class ContainerClick {
	/**
	 * Button of a {@link InventoryClick#HOTBAR_SWAP} that swaps the slot with the off hand instead of a hotbar slot.
	 */
	public static final int OFF_HAND_BUTTON = 40;

	/**
	 * ID of the container the slot belongs to, as last received.
	 */
	int containerId;
	/**
	 * State ID of the container as last received, or {@code 0} for releases without state IDs.
	 */
	int stateId;
	/**
	 * Positive ID of this click, which a server before Minecraft 1.17 confirms or rejects.
	 */
	int actionId;
	/**
	 * Clicked container slot.
	 */
	int slot;
	/**
	 * Click mode.
	 */
	@NotNull InventoryClick click;
	/**
	 * Hotbar slot from zero through eight, or {@link #OFF_HAND_BUTTON}, for {@link InventoryClick#HOTBAR_SWAP},
	 * otherwise zero. A release whose library cannot send the off-hand swap refuses it with an
	 * {@link IllegalArgumentException}.
	 */
	int button;
	/**
	 * Item the click picks up as last received in the slot, which a server before Minecraft 1.17 compares with
	 * its own result, or {@code null} when the slot is empty or the click mode picks nothing up.
	 */
	@Nullable InventoryItem clickedItem;
}
