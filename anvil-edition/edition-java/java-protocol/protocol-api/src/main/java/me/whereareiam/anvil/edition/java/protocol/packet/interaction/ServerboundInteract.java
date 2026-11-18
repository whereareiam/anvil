package me.whereareiam.anvil.edition.java.protocol.packet.interaction;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractAction;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player interacts with an entity using a hand; an attack is a {@link ServerboundAttack}. Minecraft 26.1 and later
 * send every interaction with its point and no longer tell {@link InteractAction#INTERACT_AT} from
 * {@link InteractAction#INTERACT}.
 */
@Value
@Builder
public class ServerboundInteract implements ServerboundPacket {
	/**
	 * The protocol ID of the targeted entity.
	 */
	@NotNull EntityId entityId;
	/**
	 * Whether the player interacts with the entity or with a point of it.
	 */
	@NotNull InteractAction action;
	/**
	 * The hand the player interacts with.
	 */
	@NotNull InteractionHand hand;
	/**
	 * The point of the entity the player interacts with, relative to the entity's position, sent with
	 * {@link InteractAction#INTERACT_AT} before Minecraft 26.1 and with every interaction from 26.1 on; absent means
	 * {@link Vec3d#ZERO}, the entity's position.
	 */
	@Nullable Vec3d point;
	/**
	 * Whether the player sneaks.
	 */
	boolean sneaking;
}
