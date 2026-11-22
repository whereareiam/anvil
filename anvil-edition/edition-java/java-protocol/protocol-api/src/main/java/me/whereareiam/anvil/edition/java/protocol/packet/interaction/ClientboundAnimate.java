package me.whereareiam.anvil.edition.java.protocol.packet.interaction;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import org.jetbrains.annotations.NotNull;

/**
 * The server animates an entity: swings one of its arms, wakes a player up or shows a critical hit. When the server
 * swings an arm of the player's own entity, the vanilla client before Minecraft 26.3 swings it back
 * ({@code ClientPacketListener.handleAnimate} calls {@code LocalPlayer.swing}), which tells the server in a
 * {@link ServerboundSwing}, on its own thread, between its ticks and in the order its packets arrived; the worker kit's
 * client answers it so itself. From 26.3 the server sends swings in a packet of their own, which the client does not
 * answer.
 */
@Value
public class ClientboundAnimate implements ClientboundPacket {
	/**
	 * The protocol ID of the animated entity.
	 */
	@NotNull EntityId entityId;
	/**
	 * What the entity does.
	 */
	@NotNull AnimateAction action;
}
