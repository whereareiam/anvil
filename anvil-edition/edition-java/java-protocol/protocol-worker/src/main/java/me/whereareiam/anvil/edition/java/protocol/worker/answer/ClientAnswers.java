package me.whereareiam.anvil.edition.java.protocol.worker.answer;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundPing;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ServerboundPong;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ClientboundAnimate;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerAck;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerAck;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The answers one worker player's client gives on its own, between its ticks, to packets that do not concern where its
 * player is: each ping with its pong ({@code handlePing}), in the game and in the configuration, before Minecraft 1.17
 * each rejected transaction of the player's inventory or of the container it has open with its acknowledgement
 * ({@code handleContainerAck}), and before 26.3 each swing the server animates of the player's own entity with the
 * same swing ({@code handleAnimate}, which swings the {@code LocalPlayer}, whose swing tells the server). The vanilla
 * client gives them on its own thread, in the order the packets arrived and never within a tick, so an answer tells the
 * server which of its packets the client had handled; the dispatcher hands this class every packet it delivers, under
 * the client's lock, which keeps that order among the client's other packets, the acceptance of a teleport among them.
 *
 * <p>Not thread-safe; its owner serializes packets and ticks.</p>
 */
public final class ClientAnswers {
	private static final boolean ACKNOWLEDGED = true;
	/**
	 * The first release whose server plays the swings of a player's uses itself and sends them in a packet of their
	 * own, so its client swings back nothing the server animates.
	 */
	private static final MinecraftVersion SERVER_SWINGS = MinecraftVersion.parse("26.3");

	private final @NotNull JavaProtocol protocol;
	private final boolean swingsBack;
	private final @NotNull OpenContainer container = new OpenContainer();

	/**
	 * Creates the answers of a player, whose next login shows its inventory.
	 *
	 * @param protocol the player's protocol, which carries the answers
	 * @param version the Minecraft version of the player's client
	 */
	public ClientAnswers(@NotNull JavaProtocol protocol, @NotNull MinecraftVersion version) {
		this.protocol = protocol;
		this.swingsBack = !version.isAtLeast(SERVER_SWINGS);
	}

	/**
	 * Follows a packet the player received and answers it as the vanilla client does.
	 *
	 * @param packet the received packet
	 */
	public void apply(@NotNull ClientboundPacket packet) {
		container.apply(packet);
		switch (packet) {
			case ClientboundPing ping -> protocol.send(new ServerboundPong(ping.getId()));
			case ClientboundContainerAck ack -> acknowledge(ack);
			case ClientboundAnimate animate -> swingBack(animate);
			default -> {
			}
		}
	}

	/**
	 * Swings back an arm the server swings of the player's own entity, as the client's player swings it; the client
	 * leaves every other entity and animation alone.
	 */
	private void swingBack(@NotNull ClientboundAnimate animate) {
		if (!swingsBack || !protocol.player().entityId().equals(Optional.of(animate.getEntityId()))) return;

		switch (animate.getAction()) {
			case SWING_MAIN_HAND -> protocol.send(new ServerboundSwing(InteractionHand.MAIN_HAND));
			case SWING_OFF_HAND -> protocol.send(new ServerboundSwing(InteractionHand.OFF_HAND));
			default -> {
			}
		}
	}

	/**
	 * Acknowledges a rejection of an action in the inventory or the open container; the client ignores a confirmation
	 * and a rejection in any other container.
	 */
	private void acknowledge(@NotNull ClientboundContainerAck ack) {
		if (ack.isAccepted() || !container.shows(ack.getContainerId())) return;

		protocol.send(new ServerboundContainerAck(ack.getContainerId(), ack.getUid(), ACKNOWLEDGED));
	}
}
