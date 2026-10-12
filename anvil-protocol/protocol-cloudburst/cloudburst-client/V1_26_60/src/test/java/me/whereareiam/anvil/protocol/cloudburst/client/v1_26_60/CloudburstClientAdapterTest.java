package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CloudburstClientAdapterTest {
	private static final int TIMEOUT_MILLIS = 10_000;

	private final CloudburstClientAdapter client = new CloudburstClientAdapter();

	@Test
	void speaksTheNewestCodecAsTheReleaseKeyAndHandsOutPeers() {
		assertEquals(2225, client.protocolNumber());
		assertEquals(BedrockPeer.class, client.sessionType());
	}

	@Test
	void refusesAProtocolItHasNoCodecFor() throws Exception {
		ClientLogin login = login("127.0.0.1", 19132, null, 2224);

		var failure = assertThrows(IllegalArgumentException.class, () -> client.open(login, new IgnoringListener()));

		assertTrue(failure.getMessage().startsWith("This CloudburstMC Protocol release has no codec for Bedrock protocol 2224"),
				failure.getMessage());
	}

	@Test
	void connectsFromTheSourceAddress() throws Exception {
		InetAddress source = InetAddress.getByAddress(new byte[]{127, 0, 0, 2});
		assumeTrue(bindable(source), "127.0.0.2 is not configured on this machine");

		try (DatagramSocket server = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			server.setSoTimeout(TIMEOUT_MILLIS);
			BedrockPeer session = client.open(login("127.0.0.1", server.getLocalPort(), source.getHostAddress(), 2193), new IgnoringListener());
			assertTrue(client.connected(session));
			client.connect(session);

			try {
				DatagramPacket request = new DatagramPacket(new byte[1500], 1500);
				server.receive(request);
				assertEquals(source, request.getAddress());
			} finally {
				client.disconnect(session, "Finished");
			}
		}
	}

	private static ClientLogin login(String host, int port, String sourceAddress, int protocol) throws Exception {
		return ClientLogin.builder()
				.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
				.identity(new TestIdentity(null))
				.connection(ClientConnection.builder().host(host).port(port).sourceAddress(sourceAddress).protocol(protocol).build())
				.build();
	}

	private static boolean bindable(InetAddress address) {
		try (DatagramSocket socket = new DatagramSocket(null)) {
			socket.bind(new InetSocketAddress(address, 0));
			return true;
		} catch (IOException unavailable) {
			return false;
		}
	}

	/**
	 * Ignores every callback.
	 */
	private static final class IgnoringListener implements ClientListener<BedrockPeer> {
		@Override
		public void loggedIn(@NotNull BedrockPeer session, @NotNull ClientProfile granted) {
		}

		@Override
		public void teleported(@NotNull BedrockPeer session, float yaw, float pitch) {
		}

		@Override
		public void disconnected(@NotNull BedrockPeer session, @NotNull DisconnectCause cause, @NotNull String reason) {
		}
	}
}
