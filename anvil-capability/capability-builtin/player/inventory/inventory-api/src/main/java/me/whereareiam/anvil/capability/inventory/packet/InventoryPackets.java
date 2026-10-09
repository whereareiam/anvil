package me.whereareiam.anvil.capability.inventory.packet;

import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import org.jetbrains.annotations.NotNull;

/**
 * Sends and receives the inventory packets of one protocol-library release. This is the port a library segment
 * implements: the worker hands the library-neutral inventory binding the implementation of the segment it selected
 * for the player's release, and the binding owns everything that does not differ between releases, such as the
 * observed container state, the state and action IDs each click carries and the snapshots it publishes.
 *
 * <p>Implementations are stateless and are called only with the player's current native session.</p>
 *
 * <pre>{@code
 * public final class McProtocolInventoryPackets implements InventoryPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public void selectHotbar(Session session, int slot) {
 *         session.send(new ServerboundSetCarriedItemPacket(slot));
 *     }
 *     public void click(Session session, ContainerClick click) {
 *         session.send(new ServerboundContainerClickPacket(click.getContainerId(), click.getStateId(),
 *                 click.getSlot(), type(click), action(click), null, Map.of()));
 *     }
 *     public Runnable listen(Session session, InventoryPacketListener listener) {
 *         SessionListener packets = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (session.isConnected() && packet instanceof ClientboundOpenScreenPacket open)
 *                     listener.opened(open.getContainerId());
 *             }
 *         };
 *         session.addListener(packets);
 *         return () -> session.removeListener(packets);
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface InventoryPackets<S> {
	/**
	 * Returns the native session type the packets are sent and received through. The binding casts the player's
	 * native session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Selects the held hotbar slot.
	 *
	 * @param session the player's current native session
	 * @param slot hotbar slot from zero through eight
	 */
	void selectHotbar(@NotNull S session, int slot);

	/**
	 * Sends one click on a container slot.
	 *
	 * @param session the player's current native session
	 * @param click container, slot, click mode and the IDs the release's click packet carries
	 */
	void click(@NotNull S session, @NotNull ContainerClick click);

	/**
	 * Delivers the inventory packets the session receives to a listener until the returned action detaches it.
	 * Packets arriving after the session disconnected are not delivered. A release whose server waits for the
	 * client to acknowledge a rejected click, as Minecraft 1.16.5 does, also sends that acknowledgement here.
	 *
	 * @param session the native session of one connection generation
	 * @param listener receiver of the translated inventory state
	 * @return action that removes the listener from the session
	 */
	@NotNull Runnable listen(@NotNull S session, @NotNull InventoryPacketListener listener);
}
