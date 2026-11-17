package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import io.netty.channel.ChannelHandlerContext;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.netty.ReceivedFrames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A packetlib 2.0 TCP client session that hands each packet it receives to its listener through {@link ReceivedFrames},
 * translated and, while the listener wants frames, as its frame. Packetlib 2.0 keeps the session's channel to itself,
 * so the session puts the handlers into its pipeline when the channel becomes active, before any packet arrives.
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

	private ReceivedFrames<Session, Packet> frames(
			MinecraftProtocol protocol,
			ClientListener<? super Session> listener,
			PacketTable<Packet> packets
	) {
		return new ReceivedFrames<>(this, Packet.class, packets, listener, () -> state(protocol));
	}

	/**
	 * The state the release reads the next packet in; releases before Minecraft 1.20.2 have no configuration.
	 */
	private static @Nullable ConnectionState state(MinecraftProtocol protocol) {
		return switch (protocol.getSubProtocol()) {
			case LOGIN -> ConnectionState.LOGIN;
			case GAME -> ConnectionState.PLAY;
			default -> null;
		};
	}
}
