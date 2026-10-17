package me.whereareiam.anvil.capability.movement.packet;

import me.whereareiam.anvil.capability.movement.model.Position;
import org.jetbrains.annotations.NotNull;

/**
 * Sends the movement packets of one protocol-library release. This is the port a library segment implements and
 * registers in {@code META-INF/services/me.whereareiam.anvil.capability.movement.packet.MovementPackets}. While
 * binding a player, the movement extension of a library side obtains the implementation of the segment the
 * worker selected for the player's release through {@code PlayerBindingContext.adapter(MovementPackets.class)};
 * the library-neutral movement binding owns everything else, such as the shared view rotation.
 *
 * <p>Implementations are stateless and are called only on the player's current native session.</p>
 *
 * <pre>{@code
 * public final class McProtocolMovementPackets implements MovementPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public void move(Session session, Position position) {
 *         session.send(new ServerboundMovePlayerPosRotPacket(position.isOnGround(), false,
 *                 position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch()));
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface MovementPackets<S> {
	/**
	 * Returns the native session type the packets are sent through. The binding casts the player's native
	 * session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Sends an absolute position and view update in one packet.
	 *
	 * @param session the player's current native session
	 * @param position new position, view direction and ground state
	 */
	void move(@NotNull S session, @NotNull Position position);
}
