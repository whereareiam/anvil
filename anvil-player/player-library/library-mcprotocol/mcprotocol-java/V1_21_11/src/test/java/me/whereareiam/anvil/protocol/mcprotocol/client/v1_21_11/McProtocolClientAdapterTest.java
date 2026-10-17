package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class McProtocolClientAdapterTest {
	private static final int TIMEOUT_MILLIS = 10_000;

	@Test
	void connectsFromTheSourceAddressAndAnnouncesTheVirtualHost() throws Exception {
		InetAddress source = InetAddress.getByAddress(new byte[]{127, 0, 0, 2});
		assumeTrue(bindable(source), "127.0.0.2 is not configured on this machine");

		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			server.setSoTimeout(TIMEOUT_MILLIS);
			McProtocolClientAdapter client = new McProtocolClientAdapter();
			ClientSession session = client.open(ClientLogin.builder()
					.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
					.settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
					.connection(ClientConnection.builder()
							.host(server.getInetAddress().getHostAddress())
							.port(server.getLocalPort())
							.virtualHost("lobby.example.test")
							.sourceAddress(source.getHostAddress())
							.build())
					.build(), new IgnoringListener());
			client.connect(session);

			try (Socket connection = server.accept()) {
				connection.setSoTimeout(TIMEOUT_MILLIS);
				assertEquals(source, connection.getInetAddress());

				DataInputStream handshake = new DataInputStream(connection.getInputStream());
				varInt(handshake);
				assertEquals(0, varInt(handshake));
				assertEquals(client.protocolNumber(), varInt(handshake));
				byte[] host = new byte[varInt(handshake)];
				handshake.readFully(host);
				assertEquals("lobby.example.test", new String(host, StandardCharsets.UTF_8));
				assertEquals(server.getLocalPort(), handshake.readUnsignedShort());
			} finally {
				client.disconnect(session, "Finished");
			}
		}
	}

	@Test
	void sendsAPacketEncodedThroughTheReleaseTable() {
		List<Packet> sent = new ArrayList<>();

		new McProtocolClientAdapter().send(recording(sent), new ServerboundChat("hello there"));

		assertEquals(1, sent.size());
		assertEquals("hello there", assertInstanceOf(ServerboundChatPacket.class, sent.getFirst()).getMessage());
	}

	@Test
	void refusesToSendAPacketTheReleaseDoesNotCarry() {
		List<Packet> sent = new ArrayList<>();
		McProtocolClientAdapter client = new McProtocolClientAdapter();
		ClientSession session = recording(sent);

		assertThrows(ProtocolUnavailableException.class, () -> client.send(session, new UnknownPacket()));
		assertEquals(List.of(), sent);
	}

	@Test
	void supportsExactlyThePacketsOfTheReleaseTable() {
		McProtocolClientAdapter client = new McProtocolClientAdapter();

		assertTrue(client.supports(ClientboundSystemChat.class));
		assertTrue(client.supports(ServerboundChat.class));
		assertTrue(client.supports(ServerboundChatCommand.class));
		assertTrue(client.supports(ClientboundDisguisedChat.class));
		assertFalse(client.supports(UnknownPacket.class));
	}

	private static boolean bindable(InetAddress address) {
		try (Socket socket = new Socket()) {
			socket.bind(new InetSocketAddress(address, 0));
			return true;
		} catch (IOException unavailable) {
			return false;
		}
	}

	private static int varInt(DataInputStream input) throws IOException {
		int value = 0;
		for (int position = 0; position < 35; position += 7) {
			byte next = input.readByte();
			value |= (next & 0x7F) << position;
			if ((next & 0x80) == 0) return value;
		}

		throw new IOException("VarInt is too long");
	}

	/**
	 * A session that records the packets sent on it and supports nothing else.
	 */
	private static ClientSession recording(List<Packet> sent) {
		return (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(), new Class<?>[]{ClientSession.class}, (proxy, method, arguments) -> {
			if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

			sent.add((Packet) arguments[0]);
			return null;
		});
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}

	private static final class IgnoringListener implements ClientListener<ClientSession> {
		@Override
		public void loggedIn(@NotNull ClientSession session, @NotNull ClientProfile granted) {
		}

		@Override
		public void teleported(@NotNull ClientSession session, float yaw, float pitch) {
		}

		@Override
		public void disconnected(@NotNull ClientSession session, @NotNull DisconnectCause cause, @NotNull String reason) {
		}

		@Override
		public void received(@NotNull ClientSession session, @NotNull ClientboundPacket packet) {
		}
	}
}
