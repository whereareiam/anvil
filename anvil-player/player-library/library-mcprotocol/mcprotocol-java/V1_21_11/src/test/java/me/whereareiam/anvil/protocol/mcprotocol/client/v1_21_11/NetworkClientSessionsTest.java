package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import io.netty.channel.EventLoop;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;
import org.jetbrains.annotations.NotNull;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
			NetworkClientSessions sessions = new NetworkClientSessions(new TextTranslator(), TeleportListener::new);
			ClientSession session = sessions.open(login(server.getLocalPort()), new DisconnectListener(disconnect),
					McProtocolClientAdapter.packets());
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
	void startsTheConfigurationAfterTheLibrarysOwnListener() {
		ClientSession session = new NetworkClientSessions(new TextTranslator(), TeleportListener::new)
				.open(login(25565), new DisconnectListener(new CompletableFuture<>()), McProtocolClientAdapter.packets());

		session.getPacketProtocol().newClientSession(session);

		// MCProtocolLib's own listener shares its simple name with Anvil's client listener.
		List<Class<?>> listeners = session.getListeners().stream()
				.<Class<?>>map(SessionListener::getClass)
				.toList();
		assertEquals(List.of(TeleportListener.class, ClientPacketListener.class,
				org.geysermc.mcprotocollib.protocol.ClientListener.class, ClientConfiguration.class), listeners);
	}

	private static ClientLogin login(int port) {
		return ClientLogin.builder()
				.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
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

	private record DisconnectListener(CompletableFuture<String> disconnect) implements ClientListener<ClientSession> {
		@Override
		public void granted(@NotNull ClientSession session, @NotNull ClientProfile granted) {
		}

		@Override
		public void loggedIn(@NotNull ClientSession session, @NotNull ClientProfile granted) {
		}

		@Override
		public void teleported(@NotNull ClientSession session, float yaw, float pitch) {
		}

		@Override
		public void disconnected(@NotNull ClientSession session, @NotNull DisconnectCause cause, @NotNull String reason) {
			// The server's own disconnect is reported as SERVER; a close the client could not explain is not.
			if (cause == DisconnectCause.SERVER || reason.equals("disconnect.endOfStream")) disconnect.complete(cause + " " + reason);
		}

		@Override
		public void received(@NotNull ClientSession session, @NotNull ClientboundPacket packet) {
		}
	}
}
