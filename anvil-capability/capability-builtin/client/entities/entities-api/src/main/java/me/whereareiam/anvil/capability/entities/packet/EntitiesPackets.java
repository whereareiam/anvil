package me.whereareiam.anvil.capability.entities.packet;

import me.whereareiam.anvil.capability.entities.packet.model.EntityPacket;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Decodes the entity packets of one protocol-library release into library-neutral {@link EntityPacket} values. This
 * is the port a library segment implements and declares in
 * {@code META-INF/services/me.whereareiam.anvil.capability.entities.packet.EntitiesPackets}: the worker selects the
 * segment for the player's release and hands its implementation to the entities wiring, and the library-neutral
 * entities binding keeps the entities, the player's position and the connection they belong to.
 *
 * <p>Implementations are stateless: each packet becomes the value that says what it says, without the entity it
 * refers to. Entity data is release-specific, because the index of each value depends on the entity's type and the
 * release, so the segment decodes the values a test reads and leaves out the others.</p>
 *
 * <pre>{@code
 * public final class McProtocolEntitiesPackets implements EntitiesPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public Runnable listen(Session session, Consumer<EntityPacket> packets) {
 *         SessionListener listener = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (packet instanceof ClientboundRemoveEntitiesPacket removed)
 *                     packets.accept(new EntitiesRemoved(Arrays.stream(removed.getEntityIds()).boxed().toList()));
 *             }
 *         };
 *         session.addListener(listener);
 *         return () -> session.removeListener(listener);
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface EntitiesPackets<S> {
	/**
	 * Returns the native session type the packets are received through. The binding casts the player's native session
	 * with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Reports what the session's packets say about entities until the returned handle runs: the packets it receives
	 * that spawn, move, describe, mount and remove entities, place the player or reset its world, the movement
	 * packets it sends for the player, and the end of the connection. Values arrive on the library's network thread
	 * in the order of the packets.
	 *
	 * @param session the native session to observe
	 * @param packets receives each decoded packet
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listen(@NotNull S session, @NotNull Consumer<EntityPacket> packets);
}
