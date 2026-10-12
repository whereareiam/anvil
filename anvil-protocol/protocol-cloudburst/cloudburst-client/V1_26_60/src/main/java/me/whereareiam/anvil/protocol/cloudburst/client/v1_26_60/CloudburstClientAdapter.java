package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.codec.v924.Bedrock_v924;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.cloudburstmc.protocol.bedrock.codec.v975.Bedrock_v975;
import org.cloudburstmc.protocol.bedrock.codec.v1001.Bedrock_v1001;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.codec.v2169.Bedrock_v2169;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bedrock client of CloudburstMC Protocol for every codec of the release, over RakNet.
 *
 * <p>A session is the {@link ConnectionSession} of one RakNet connection: {@link #open(ClientLogin, ClientListener)}
 * registers the connection's channel with the codec its protocol selects and the login it performs, and
 * {@link #connect(ConnectionSession)} binds it to the source address and connects it. Capability bindings receive the
 * connection session; feature segments of this release send and subscribe to its packets through it.</p>
 */
public final class CloudburstClientAdapter implements CloudburstClient<ConnectionSession> {
	private static final List<BedrockCodec> CODECS = List.of(
			Bedrock_v589.CODEC,
			Bedrock_v594.CODEC,
			Bedrock_v618.CODEC,
			Bedrock_v622.CODEC,
			Bedrock_v630.CODEC,
			Bedrock_v649.CODEC,
			Bedrock_v662.CODEC,
			Bedrock_v671.CODEC,
			Bedrock_v685.CODEC,
			Bedrock_v686.CODEC,
			Bedrock_v712.CODEC,
			Bedrock_v729.CODEC,
			Bedrock_v748.CODEC,
			Bedrock_v766.CODEC,
			Bedrock_v776.CODEC,
			Bedrock_v786.CODEC,
			Bedrock_v800.CODEC,
			Bedrock_v818.CODEC,
			Bedrock_v819.CODEC,
			Bedrock_v827.CODEC,
			Bedrock_v844.CODEC,
			Bedrock_v859.CODEC,
			Bedrock_v860.CODEC,
			Bedrock_v898.CODEC,
			Bedrock_v924.CODEC,
			Bedrock_v944.CODEC,
			Bedrock_v975.CODEC,
			Bedrock_v1001.CODEC,
			Bedrock_v2168.CODEC,
			Bedrock_v2169.CODEC,
			Bedrock_v2193.CODEC,
			Bedrock_v2225.CODEC
	);
	private static final Map<Integer, BedrockCodec> BY_PROTOCOL = byProtocol();
	private static final AttributeKey<InetSocketAddress[]> ADDRESSES = AttributeKey.valueOf("anvil-cloudburst-addresses");
	private static final AttributeKey<Runnable> CONNECT_FAILED = AttributeKey.valueOf("anvil-cloudburst-connect-failed");
	private static final AttributeKey<ConnectionSession> SESSION = AttributeKey.valueOf("anvil-cloudburst-session");
	private static final EventLoopGroup EVENT_LOOPS = new NioEventLoopGroup(0, new DefaultThreadFactory("anvil-cloudburst", true));

	@Override
	public int protocolNumber() {
		return CODECS.getLast().getProtocolVersion();
	}

	@Override
	public @NotNull Class<ConnectionSession> sessionType() {
		return ConnectionSession.class;
	}

	@Override
	public @NotNull ConnectionSession open(@NotNull ClientLogin login, @NotNull ClientListener<? super ConnectionSession> listener) {
		ClientConnection connection = login.getConnection();
		BedrockCodec codec = BY_PROTOCOL.get(connection.getProtocol());
		if (codec == null)
			throw new IllegalArgumentException("This CloudburstMC Protocol release has no codec for Bedrock protocol "
					+ connection.getProtocol() + "; it speaks " + BY_PROTOCOL.keySet());

		Bootstrap bootstrap = new Bootstrap()
				.group(EVENT_LOOPS)
				.channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
				.option(RakChannelOption.RAK_PROTOCOL_VERSION, codec.getRaknetProtocolVersion())
				.option(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong())
				.handler(new Initializer(codec, login, listener));
		ChannelFuture registered = bootstrap.register().syncUninterruptibly();
		if (!registered.isSuccess())
			throw new IllegalStateException("Could not create a RakNet channel: " + registered.cause(), registered.cause());

		// The channel's own pipeline initializes once the registration reached it, after the registration completed.
		Channel channel = registered.channel();
		BedrockPeer peer = channel.eventLoop().submit(() -> channel.pipeline().get(BedrockPeer.class)).syncUninterruptibly().getNow();
		if (peer == null) throw new IllegalStateException("The RakNet channel did not initialize its Bedrock peer");

		ConnectionSession session = new ConnectionSession(peer, codec, login.getProfile());
		channel.attr(SESSION).set(session);
		channel.attr(ADDRESSES).set(new InetSocketAddress[]{
				new InetSocketAddress(connection.getHost(), connection.getPort()),
				local(connection.getSourceAddress())
		});
		channel.attr(CONNECT_FAILED).set(() -> {
			session.close();
			listener.disconnected(session, DisconnectCause.CONNECTION_LOST,
					"Could not connect to " + connection.getHost() + ":" + connection.getPort());
		});
		return session;
	}

	@Override
	public void connect(@NotNull ConnectionSession session) {
		Channel channel = session.peer().getChannel();
		InetSocketAddress[] addresses = channel.attr(ADDRESSES).get();
		ChannelFuture connecting = addresses[1] == null ? channel.connect(addresses[0]) : channel.connect(addresses[0], addresses[1]);
		connecting.addListener(future -> {
			if (future.isSuccess()) return;

			channel.close();
			channel.attr(CONNECT_FAILED).get().run();
		});
	}

	@Override
	public boolean connected(@NotNull ConnectionSession session) {
		return session.connected();
	}

	@Override
	public void disconnect(@NotNull ConnectionSession session, @NotNull String reason) {
		BedrockPeer peer = session.peer();
		if (peer.getChannel().isActive()) peer.close(reason);
		else peer.getChannel().close();
	}

	private static @Nullable InetSocketAddress local(@Nullable String sourceAddress) {
		if (sourceAddress == null) return null;

		try {
			return new InetSocketAddress(InetAddress.getByName(sourceAddress), 0);
		} catch (UnknownHostException exception) {
			throw new IllegalArgumentException("Source address '" + sourceAddress + "' is not an address literal", exception);
		}
	}

	private static Map<Integer, BedrockCodec> byProtocol() {
		Map<Integer, BedrockCodec> codecs = new LinkedHashMap<>();
		CODECS.forEach(codec -> codecs.put(codec.getProtocolVersion(), codec));
		return Map.copyOf(codecs);
	}

	/**
	 * Starts the login of a connection's session as soon as RakNet connected it.
	 */
	private static final class Initializer extends BedrockClientInitializer {
		private final BedrockCodec codec;
		private final ClientLogin login;
		private final ClientListener<? super ConnectionSession> listener;

		private Initializer(BedrockCodec codec, ClientLogin login, ClientListener<? super ConnectionSession> listener) {
			this.codec = codec;
			this.login = login;
			this.listener = listener;
		}

		@Override
		protected void initSession(BedrockClientSession session) {
			session.setCodec(codec);
			// A client receives the server's whole item, creative and recipe lists, which exceed the default limits.
			session.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.CLIENT);
			ConnectionSession connection = session.getPeer().getChannel().attr(SESSION).get();
			connection.attach(session);
			session.setPacketHandler(new ClientPacketHandler(session, connection, codec, login, listener));

			RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
			request.setProtocolVersion(codec.getProtocolVersion());
			session.sendPacketImmediately(request);
		}
	}
}
