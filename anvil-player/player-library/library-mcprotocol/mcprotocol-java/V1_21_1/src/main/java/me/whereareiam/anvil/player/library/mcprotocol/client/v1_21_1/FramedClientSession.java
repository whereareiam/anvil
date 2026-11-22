package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_1;

import io.netty.channel.ChannelHandlerContext;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.player.library.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.player.library.mcprotocol.netty.ReceivedFrames;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A TCP client session of the MCProtocolLib 1.21.1 release that hands each packet it receives to its listener through
 * {@link ReceivedFrames}, translated and, while the listener wants frames, as its frame. The session puts the handlers
 * into its pipeline when its channel becomes active, before any packet arrives, and reads packets in the release's
 * inbound state.
 *
 * <p>The release hands most packets to its session listeners on an event loop of its own, after later packets were
 * delivered and answered; the session calls them on the channel's thread instead, right after the delivery, so what
 * they and MCProtocolLib answer leaves in the order the packets arrived, as the vanilla client answers.</p>
 */
final class FramedClientSession extends TcpClientSession {
	private static final int ANY_PORT = 0;

	private final ReceivedFrames<Session, Packet> frames;

	/**
	 * Creates a session that binds any local address.
	 *
	 * @param host the server's host
	 * @param port the server's port
	 * @param protocol the session's protocol
	 * @param listener the session's listener
	 * @param packets the session's translations
	 */
	FramedClientSession(
			@NotNull String host,
			int port,
			@NotNull MinecraftProtocol protocol,
			@NotNull ClientListener<? super Session> listener,
			@NotNull PacketTable<Packet> packets
	) {
		super(host, port, protocol);
		this.frames = frames(protocol, listener, packets);
	}

	/**
	 * Creates a session that binds a local address on an ephemeral port.
	 *
	 * @param host the server's host
	 * @param port the server's port
	 * @param source the local address to bind
	 * @param protocol the session's protocol
	 * @param listener the session's listener
	 * @param packets the session's translations
	 */
	FramedClientSession(
			@NotNull String host,
			int port,
			@NotNull String source,
			@NotNull MinecraftProtocol protocol,
			@NotNull ClientListener<? super Session> listener,
			@NotNull PacketTable<Packet> packets
	) {
		super(host, port, source, ANY_PORT, protocol);
		this.frames = frames(protocol, listener, packets);
	}

	@Override
	public void channelActive(ChannelHandlerContext context) throws Exception {
		frames.install(context.pipeline());
		super.channelActive(context);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext context, Packet packet) {
		callPacketReceived(packet);
	}

	private ReceivedFrames<Session, Packet> frames(
			MinecraftProtocol protocol,
			ClientListener<? super Session> listener,
			PacketTable<Packet> packets
	) {
		return new ReceivedFrames<>(this, Packet.class, packets, listener, () -> state(protocol));
	}

	/**
	 * The state the release reads the next packet in.
	 */
	private static @Nullable ConnectionState state(MinecraftProtocol protocol) {
		return switch (protocol.getInboundState()) {
			case LOGIN -> ConnectionState.LOGIN;
			case CONFIGURATION -> ConnectionState.CONFIGURATION;
			case GAME -> ConnectionState.PLAY;
			default -> null;
		};
	}
}
