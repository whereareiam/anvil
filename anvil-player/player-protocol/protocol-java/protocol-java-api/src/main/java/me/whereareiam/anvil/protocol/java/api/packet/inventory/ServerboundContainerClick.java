package me.whereareiam.anvil.protocol.java.api.packet.inventory;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.ClickType;
import org.jetbrains.annotations.NotNull;

/**
 * One click on a container slot. The player sends it without predicting the slots the click changes or the stack it
 * carries afterwards, so the server answers with the container's actual contents.
 */
@Value
@Builder
public class ServerboundContainerClick implements ServerboundPacket {
	/**
	 * ID of the container the slot belongs to.
	 */
	int containerId;
	/**
	 * State ID of the container as the player last received it.
	 */
	int stateId;
	/**
	 * The clicked container slot, or {@code -999} for a click outside the container.
	 */
	int slot;
	/**
	 * The variant of the click type, as {@link ClickType} describes for each type.
	 */
	int button;
	/**
	 * The way the player clicks.
	 */
	@NotNull ClickType clickType;
}
