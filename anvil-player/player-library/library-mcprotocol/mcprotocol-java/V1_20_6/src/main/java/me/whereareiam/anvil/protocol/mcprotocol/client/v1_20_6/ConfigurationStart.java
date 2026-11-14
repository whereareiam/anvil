package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientBrand;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Starts the configuration as a vanilla client of Minecraft 1.20.2 and later does: once its login is acknowledged, it
 * sends its {@link ClientBrand} and then its client information, and it sends neither again, not when the server
 * starts another configuration nor when the game starts.
 *
 * <p>The release's {@code ClientProtocol} adds this listener right after MCProtocolLib's own, which acknowledges the
 * login and switches the session's outbound packets to the configuration phase, so what this listener sends follows
 * the acknowledgement in that phase. Each release says which packet marks that moment, because MCProtocolLib changed
 * when it switches the phase, and builds the client information in its own shape.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ConfigurationStart extends SessionAdapter {
	/**
	 * Whether the client shows chat colors, as a vanilla client does by default.
	 */
	protected static final boolean CHAT_COLORS = true;
	/**
	 * Whether the client asks the server to filter text, which a vanilla client does not by default.
	 */
	protected static final boolean TEXT_FILTERING = false;
	/**
	 * Whether the player may be listed in the server's status, as a vanilla client allows by default.
	 */
	protected static final boolean SERVER_LISTING = true;

	private static final Key BRAND = Key.key(ClientBrand.CHANNEL);

	private final @NotNull ClientSettings settings;

	@Override
	public final void packetSent(Session session, Packet packet) {
		if (startsAfterSending(packet)) start(session);
	}

	@Override
	public final void packetReceived(Session session, Packet packet) {
		if (startsAfterReceiving(packet)) start(session);
	}

	/**
	 * Tells whether the configuration starts once a packet the client sent was written.
	 *
	 * @param packet the packet the client sent
	 * @return whether it starts the configuration; none does by default
	 */
	protected boolean startsAfterSending(@NotNull Packet packet) {
		return false;
	}

	/**
	 * Tells whether the configuration starts once the client received a packet.
	 *
	 * @param packet the packet the client received
	 * @return whether it starts the configuration; none does by default
	 */
	protected boolean startsAfterReceiving(@NotNull Packet packet) {
		return false;
	}

	/**
	 * Builds the client information a vanilla client of the release sends.
	 *
	 * @param settings the player's settings
	 * @return the release's client information packet
	 */
	protected abstract @NotNull Packet information(@NotNull ClientSettings settings);

	private void start(Session session) {
		session.send(new ServerboundCustomPayloadPacket(BRAND, ClientBrand.payload()));
		session.send(information(settings));
	}
}
