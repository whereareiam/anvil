package me.whereareiam.anvil.protocol.cloudburst;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.binding.TypedCapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.MessageChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.session.SessionOperations;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.GameConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.worker.catalog.ReleaseCatalog;
import me.whereareiam.anvil.protocol.worker.host.ProtocolWorkerProcess;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a real worker JVM for the built-in CloudburstMC Protocol release on the locked closure of its release data.
 */
class CloudburstWorkerContractTest {
	private static final String SESSION = "me.whereareiam.anvil.session";

	@Test
	void startsTheReleaseWithItsClientSegmentAndTheSessionCapability() {
		ProtocolRelease release = release();
		assertEquals(MinecraftVersion.parse("1.26.60"), release.version());

		try (ProtocolWorkerProcess worker = start(release)) {
			assertTrue(worker.diagnosticTail().contains("cloudburst-client=1.26.60"), worker.diagnosticTail());

			ProtocolPlayer player = worker.create(request(release, "1.21.50", null), null);
			try {
				ProtocolChannel channel = player.channel().orElseThrow();
				assertTrue(channel.installedCapabilities().contains(SESSION), channel.installedCapabilities().toString());
				assertEquals("cloudburst", player.libraryId());
			} finally {
				player.destroy();
			}
		}
	}

	@Test
	void speaksEveryProtocolOfTheReleaseData() {
		ProtocolRelease release = release();

		try (ProtocolWorkerProcess worker = start(release)) {
			List<MinecraftVersion> versions = release.getMinecraftVersions();
			for (MinecraftVersion version : versions) {
				ProtocolPlayer player = worker.create(request(release, version.toString(), null), null);
				try {
					// A codec exists for the version's protocol once the connection fails only because nobody listens.
					typed(player.channel().orElseThrow()).request(SessionOperations.CONNECT, null);
				} finally {
					player.destroy();
				}
			}
		}
	}

	@Test
	void refusesToConnectAPlayerThatAnnouncesAVirtualHost() {
		ProtocolRelease release = release();

		try (ProtocolWorkerProcess worker = start(release)) {
			ProtocolPlayer player = worker.create(request(release, "1.26.50", "lobby.example.test"), null);
			try {
				CapabilityChannel channel = typed(player.channel().orElseThrow());

				var failure = assertThrows(IllegalStateException.class, () -> channel.request(SessionOperations.CONNECT, null));
				assertTrue(causes(failure).contains("cannot announce the virtual host 'lobby.example.test'"), causes(failure));
			} finally {
				player.destroy();
			}
		}
	}

	private static String causes(Throwable failure) {
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) messages.append(cause.getMessage()).append('\n');
		return messages.toString();
	}

	private static ProtocolWorkerProcess start(ProtocolRelease release) {
		return new ProtocolWorkerProcess(CloudburstWorkerLibrary.LIBRARY, release, GradleReleaseClosures.of(release));
	}

	private static ProtocolRelease release() {
		return ReleaseCatalog.load(CloudburstWorkerLibrary.LIBRARY, null).releases().getFirst();
	}

	private static PlayerRequest request(ProtocolRelease release, String version, String virtualHost) {
		return PlayerRequest.builder()
				.name("Alice")
				.edition(release.getEdition())
				.clientVersion(MinecraftVersion.parse(version))
				.connection(GameConnection.builder()
						.address(new InetSocketAddress(InetAddress.getLoopbackAddress(), unusedPort()))
						.virtualHost(virtualHost)
						.build())
				.build();
	}

	private static int unusedPort() {
		try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		} catch (SocketException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static CapabilityChannel typed(ProtocolChannel raw) {
		return new TypedCapabilityChannel(new MessageChannel() {
			public byte @NotNull [] request(@NotNull String operation, byte @NotNull [] request) { return raw.request(operation, request); }
			public @NotNull Subscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener) {
				var subscription = raw.subscribe(event, listener);
				return subscription::close;
			}
			public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) { raw.await(condition, description, timeout); }
			public @NotNull Set<String> installedCapabilities() { return raw.installedCapabilities(); }
		});
	}
}
