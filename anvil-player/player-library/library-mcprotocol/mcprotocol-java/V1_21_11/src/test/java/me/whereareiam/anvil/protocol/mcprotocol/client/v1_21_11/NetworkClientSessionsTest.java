package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.mcprotocol.client.TestClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.KnownPacksAnswer;
import me.whereareiam.anvil.protocol.mcprotocol.netty.ReceivedFrames;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.ClientListener;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkClientSessionsTest {
	private static final int LOGIN_DISCONNECT = 0x00;

	/**
	 * A server closes the connection right after its disconnect, and a client that answers something just before
	 * (a teleport after a dimension change, for one) writes into a closed connection: the first write passes, the
	 * second fails. The disconnect waiting to be read must still decide the reason.
	 */
	@Test
	void reportsTheServersReasonWhenAnAnswerFailsAfterTheServerClosed() throws Exception {
		CompletableFuture<String> disconnect = new CompletableFuture<>();
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			NetworkClientSessions sessions = new NetworkClientSessions(new TextTranslator(), ReleaseAnswers::new);
			ClientSession session = sessions.open(login(server.getLocalPort()), disconnects(disconnect),
					McProtocolClientAdapter.packets(PacketDemand.EVERY));
			sessions.connect(session);
			try (Socket connection = server.accept()) {
				InputStream input = connection.getInputStream();
				readFrame(input);
				readFrame(input);

				// Hold the client's event loop, so it answers before it reads what the server sent meanwhile.
				CountDownLatch closed = new CountDownLatch(1);
				EventLoop loop = session.getChannel().eventLoop();
				Future<?> answers = loop.submit(() -> {
					closed.await();
					session.send(new ServerboundCustomQueryAnswerPacket(1));
					Thread.sleep(50);
					session.send(new ServerboundCustomQueryAnswerPacket(2));
					return null;
				});

				writeFrame(connection.getOutputStream(), loginDisconnect("{\"text\":\"anvil:kicked\"}"));
				connection.close();
				Thread.sleep(50);
				closed.countDown();
				answers.get(5, TimeUnit.SECONDS);
			}

			assertEquals("SERVER anvil:kicked", disconnect.get(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void startsTheConfigurationAfterTheLibrarysOwnListenerAndAnswersKnownPacks() {
		ClientSession session = new NetworkClientSessions(new TextTranslator(), ReleaseAnswers::new)
				.open(login(25565), new TestClientListener<>(), McProtocolClientAdapter.packets(PacketDemand.EVERY));

		session.getPacketProtocol().newClientSession(session);

		List<Class<?>> listeners = session.getListeners().stream()
				.<Class<?>>map(SessionListener::getClass)
				.toList();
		assertEquals(List.of(ReleaseAnswers.class, ClientPacketListener.class,
				ClientListener.class, ClientConfiguration.class,
				KnownPacksAnswer.class),
				listeners);
		assertFalse(session.getFlag(MinecraftConstants.SEND_BLANK_KNOWN_PACKS_RESPONSE, true));
	}

	@Test
	void handsEveryPacketOverOnceConnectedAndAsItsFrameWhileTheListenerWantsFrames() {
		TestClientListener<ClientSession> listener = new TestClientListener<>();
		ClientSession session = new NetworkClientSessions(new TextTranslator(), ReleaseAnswers::new)
				.open(login(25565), listener, McProtocolClientAdapter.packets(PacketDemand.EVERY));
		Deque<Packet> decoded = new ArrayDeque<>();
		EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<>() {
			@Override
			protected void initChannel(Channel channel) {
				channel.pipeline().addLast(ReceivedFrames.CODEC, new ChannelInboundHandlerAdapter() {
					@Override
					public void channelRead(ChannelHandlerContext context, Object message) {
						ReferenceCountUtil.release(message);
						context.fireChannelRead(decoded.remove());
					}
				});
				channel.pipeline().addLast("manager", (ChannelHandler) session);
			}
		});
		ClientboundSystemChat welcome = new ClientboundSystemChat(Text.literal("anvil:welcome"), false);

		decoded.add(new ClientboundSystemChatPacket(Component.text("anvil:welcome"), false));
		channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x77}));
		session.getPacketProtocol().setInboundState(ProtocolState.GAME);
		listener.wantFrames(true);
		decoded.add(new ClientboundKeepAlivePacket(7));
		channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x2B, 7}));

		assertEquals(List.of(welcome), listener.received());
		assertEquals(List.of(new TestClientListener.Frame(ConnectionState.PLAY, 0x2B, new byte[]{7}, null)),
				listener.frames());
	}

	private static ClientLogin login(int port) {
		return ClientLogin.builder()
				.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
				.minecraftVersion("1.21.11")
				.settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
				.connection(ClientConnection.builder().host("127.0.0.1").port(port).build())
				.build();
	}

	private static byte[] loginDisconnect(String reason) {
		ByteArrayOutputStream packet = new ByteArrayOutputStream();
		writeVarInt(packet, LOGIN_DISCONNECT);
		byte[] text = reason.getBytes(StandardCharsets.UTF_8);
		writeVarInt(packet, text.length);
		packet.writeBytes(text);
		return packet.toByteArray();
	}

	private static void readFrame(InputStream input) throws IOException {
		int length = 0;
		for (int shift = 0; ; shift += 7) {
			int next = input.read();
			if (next < 0) throw new IOException("The client closed the connection");
			length |= (next & 0x7F) << shift;
			if ((next & 0x80) == 0) break;
		}
		new DataInputStream(input).readFully(new byte[length]);
	}

	private static void writeFrame(OutputStream output, byte[] packet) throws IOException {
		ByteArrayOutputStream frame = new ByteArrayOutputStream();
		writeVarInt(frame, packet.length);
		frame.writeBytes(packet);
		output.write(frame.toByteArray());
		output.flush();
	}

	private static void writeVarInt(ByteArrayOutputStream output, int value) {
		while ((value & ~0x7F) != 0) {
			output.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		output.write(value);
	}

	/**
	 * A listener that completes the future with the first disconnect the server decided: its own disconnect, reported
	 * as SERVER, or the end of the stream; a close the client could not explain does not complete it.
	 */
	private static TestClientListener<ClientSession> disconnects(CompletableFuture<String> disconnect) {
		TestClientListener<ClientSession> listener = new TestClientListener<>();
		listener.onDisconnect((cause, reason) -> {
			if (cause == DisconnectCause.SERVER || reason.equals("disconnect.endOfStream")) disconnect.complete(cause + " " + reason);
		});
		return listener;
	}
}
