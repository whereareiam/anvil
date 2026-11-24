package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_1;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.PacketErrorEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps a NeoForge client of the MCProtocolLib 1.21.1 release in the game when a packet carries a value its server's
 * mods define: an entity data serializer, a particle, an item's data component or a command argument of a mod. The
 * client runs no mod code and cannot read such a value, nor the rest of the packet after it; a NeoForge client with the
 * mods can. The library skips the packet's remaining bytes before it reports the error, so the packet is left out and
 * the next one reads as it should; each kind of failure is reported once, as a warning the host keeps, and the
 * capabilities that would have read the packet miss it. Failures outside the game, while the client logs in or is
 * configured, still end the connection: the client is in the game from the server's login packet on, until the server
 * starts another configuration.
 */
final class UnreadableModValues extends SessionAdapter {
	private final Set<String> reported = new HashSet<>();
	private volatile boolean inGame;

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (packet instanceof ClientboundLoginPacket) inGame = true;
		if (packet instanceof ClientboundStartConfigurationPacket) inGame = false;
	}

	@Override
	public void packetError(PacketErrorEvent event) {
		if (!inGame) return;

		event.setSuppress(true);
		Throwable cause = event.getCause();
		String kind = cause.getClass().getSimpleName() + ": " + cause.getMessage();
		if (reported.add(kind))
			System.err.println("[Anvil] Warning: a NeoForge client left out a packet carrying a value of its server's mods it "
					+ "cannot read (" + kind + "); its capabilities miss what that packet said");
	}
}
