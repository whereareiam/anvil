package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Client of the MCProtocolLib releases from 1.21 on, which every adapter of this segment and of the segments extending
 * it completes with its release's {@link ClientSessions} and packet table. Each session decodes through a table of its
 * own, since a translation may remember what earlier packets of the session said, such as the types of its entities;
 * sending and the packets the release carries do not depend on a session.
 *
 * @param <S> the release's session type
 */
public class JavaClient<S extends Session> implements McProtocolClient<S> {
	private final ClientSessions<S> sessions;
	private final Supplier<PacketTable<Packet>> tables;
	private final PacketTable<Packet> packets;

	/**
	 * Creates a client of one release.
	 *
	 * @param sessions how the release opens and connects sessions
	 * @param tables creates a new packet table of the release
	 */
	protected JavaClient(@NotNull ClientSessions<S> sessions, @NotNull Supplier<PacketTable<Packet>> tables) {
		this.sessions = sessions;
		this.tables = tables;
		this.packets = tables.get();
	}

	@Override
	public int protocolNumber() {
		return MinecraftCodec.CODEC.getProtocolVersion();
	}

	@Override
	public @NotNull Class<S> sessionType() {
		return sessions.type();
	}

	@Override
	public @NotNull S open(@NotNull ClientLogin login, @NotNull ClientListener<? super S> listener) {
		return sessions.open(login, listener, tables.get());
	}

	@Override
	public void connect(@NotNull S session) {
		sessions.connect(session);
	}

	@Override
	public boolean connected(@NotNull S session) {
		return session.isConnected();
	}

	@Override
	public void disconnect(@NotNull S session, @NotNull String reason) {
		session.disconnect(reason);
	}

	@Override
	public void send(@NotNull S session, @NotNull ServerboundPacket packet) {
		session.send(packets.encode(packet));
	}

	@Override
	public boolean supports(@NotNull Class<? extends JavaPacket> type) {
		return packets.supports(type);
	}
}
