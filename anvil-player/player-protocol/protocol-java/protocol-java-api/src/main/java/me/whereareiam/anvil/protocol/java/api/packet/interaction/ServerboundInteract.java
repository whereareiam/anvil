package me.whereareiam.anvil.protocol.java.api.packet.interaction;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.interaction.InteractionPoint;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.InteractAction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player interacts with an entity, or attacks it before Minecraft 26.1. Minecraft 26.1 and later send an attack
 * as {@link ServerboundAttack} and refuse {@link InteractAction#ATTACK} here; they send every interaction with its
 * point.
 */
@Value
@Builder
public class ServerboundInteract implements ServerboundPacket {
	/**
	 * The protocol ID of the targeted entity.
	 */
	int entityId;
	/**
	 * What the player does to the entity; Minecraft 26.1 and later tell only an interaction.
	 */
	@NotNull InteractAction action;
	/**
	 * The hand the player interacts with; an attack carries none.
	 */
	@NotNull InteractionHand hand;
	/**
	 * The point of the entity the player interacts with, sent with {@link InteractAction#INTERACT_AT} before
	 * Minecraft 26.1 and with every interaction from 26.1 on; absent means the entity's position.
	 */
	@Nullable InteractionPoint location;
	/**
	 * Whether the player sneaks.
	 */
	boolean sneaking;
}
