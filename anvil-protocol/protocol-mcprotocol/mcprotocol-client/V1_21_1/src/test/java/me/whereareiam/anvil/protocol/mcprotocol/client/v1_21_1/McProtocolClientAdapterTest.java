package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.geysermc.mcprotocollib.network.Session;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
			Session session = client.open(ClientLogin.builder()
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

	private static final class IgnoringListener implements ClientListener<Session> {
		@Override
		public void loggedIn(@NotNull Session session) {
		}

		@Override
		public void teleported(@NotNull Session session, float yaw, float pitch) {
		}

		@Override
		public void disconnected(@NotNull Session session, @NotNull DisconnectCause cause, @NotNull String reason) {
		}
	}
}
