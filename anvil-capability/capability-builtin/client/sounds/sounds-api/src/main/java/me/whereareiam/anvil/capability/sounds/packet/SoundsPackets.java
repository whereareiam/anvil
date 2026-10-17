package me.whereareiam.anvil.capability.sounds.packet;

import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Reports the sound packets of one protocol-library release as library-neutral {@link HeardSound} values. This is
 * the port a library segment implements and declares in
 * {@code META-INF/services/me.whereareiam.anvil.capability.sounds.packet.SoundsPackets}: the worker selects the
 * segment for the player's release and hands its implementation to the sounds wiring, and the library-neutral sounds
 * binding owns everything else, such as which native connection generation a sound belongs to.
 *
 * <p>Implementations are stateless. They report the sound's namespaced key, resolving a registry ID through the
 * release's sound registry, and add the {@code minecraft} namespace to a name the server sent without one.</p>
 *
 * <pre>{@code
 * public final class McProtocolSoundsPackets implements SoundsPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public Runnable listen(Session session, Consumer<HeardSound> heard) {
 *         SessionListener listener = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (session.isConnected() && packet instanceof ClientboundSoundPacket sound)
 *                     heard.accept(HeardSound.builder().key(key(sound.getSound())) // ...
 *                             .build());
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
public interface SoundsPackets<S> {
	/**
	 * Returns the native session type the packets are received through. The binding casts the player's native
	 * session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Reports every sound the session is told to play while it is connected, at a position or following an entity,
	 * until the returned handle runs. Sounds arrive on the library's network thread in the order the session
	 * receives them.
	 *
	 * @param session the native session to observe
	 * @param heard receives each sound
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listen(@NotNull S session, @NotNull Consumer<HeardSound> heard);
}
