package me.whereareiam.anvil.capability.interaction.packet;

import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

/**
 * Sends the interaction packets of one protocol-library release. This is the port a library segment implements and
 * declares in {@code META-INF/services/me.whereareiam.anvil.capability.interaction.packet.InteractionPackets}; the
 * worker gives each player's binding the implementation of the segment it selected for the loaded release. The
 * library-neutral interaction binding owns everything else: the sequence numbers that acknowledge block changes,
 * the view rotation sent with an item use, and the order of the arm swing relative to each use.
 *
 * <p>Implementations are stateless, send exactly one packet per call and are called only on the player's current
 * native session. A release whose packet does not carry a value, such as the sequence before Minecraft 1.19 or the
 * view rotation of an item use before 1.21, leaves it out.</p>
 *
 * <pre>{@code
 * public final class McProtocolInteractionPackets implements InteractionPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public void swing(Session session, Hand hand) {
 *         session.send(new ServerboundSwingPacket(hand == Hand.OFF ? OFF_HAND : MAIN_HAND));
 *     }
 *     // useItem, useItemOn and entity build the release's other interaction packets the same way
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface InteractionPackets<S> {
	/**
	 * Returns the native session type the packets are sent through. The binding casts the player's native session
	 * with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Swings the player's arm.
	 *
	 * @param session the player's current native session
	 * @param hand hand whose arm swings
	 */
	void swing(@NotNull S session, @NotNull Hand hand);

	/**
	 * Uses the item held in a hand without targeting a block or an entity.
	 *
	 * @param session the player's current native session
	 * @param hand hand holding the item
	 * @param sequence the player's next block-change sequence number, ignored by releases without one
	 * @param yaw view yaw in degrees, ignored by releases that do not send the view with the use
	 * @param pitch view pitch in degrees, ignored by releases that do not send the view with the use
	 */
	void useItem(@NotNull S session, @NotNull Hand hand, int sequence, float yaw, float pitch);

	/**
	 * Uses the item held in a hand on the center of a block face.
	 *
	 * @param session the player's current native session
	 * @param position targeted block
	 * @param face targeted face of the block
	 * @param hand hand holding the item
	 * @param sequence the player's next block-change sequence number, ignored by releases without one
	 */
	void useItemOn(@NotNull S session, @NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand, int sequence);

	/**
	 * Interacts with or attacks an entity, without the arm swing that follows either.
	 *
	 * @param session the player's current native session
	 * @param entityId protocol identifier of the targeted entity
	 * @param kind whether the player interacts with or attacks the entity
	 * @param hand hand used to interact, ignored by releases whose attack carries no hand
	 */
	void entity(@NotNull S session, int entityId, @NotNull EntityInteraction kind, @NotNull Hand hand);
}
