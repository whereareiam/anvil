package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.binding.TypedCapabilityChannel;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.MessageChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.GameConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.mcprotocol.catalog.McProtocolReleaseCatalog;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.EventWorkerExtension;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.GradleReleaseClosures;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.ProbePackets;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.ProbePacketsAdapter;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.ProbeWorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts a real worker JVM for every built-in MCProtocolLib release on the locked closure of its release data.
 */
class McProtocolWorkerContractTest {
	private static final String SESSION = "me.whereareiam.anvil.session";
	private static final String MOVEMENT = "me.whereareiam.anvil.movement";
	private static final String MESSAGES = "me.whereareiam.anvil.messages";
	private static final String INVENTORY = "me.whereareiam.anvil.inventory";
	private static final String INTERACTION = "me.whereareiam.anvil.interaction";

	/**
	 * The built-in capabilities every release's worker installs. Session needs no segment and the others have
	 * segments serving every release key, so none of them may be reported unavailable.
	 */
	private static final Set<String> BUILT_IN = Set.of(SESSION, MOVEMENT, MESSAGES, INVENTORY, INTERACTION);

	@TempDir
	Path temporary;

	/**
	 * Every built-in release key with its protocol number and the client segment that serves it.
	 */
	private static final List<ReleaseExpectation> RELEASES = List.of(
			new ReleaseExpectation("1.18.2", 758, "1.18.2"),
			new ReleaseExpectation("1.21.1", 767, "1.21.1"),
			new ReleaseExpectation("1.21.11", 774, "1.21.11"),
			new ReleaseExpectation("26.1.2", 775, "1.21.11")
	);

	@Test
	void expectsEveryReleaseOfTheBuiltInReleaseData() {
		Set<MinecraftVersion> expected = RELEASES.stream()
				.map(release -> MinecraftVersion.parse(release.version()))
				.collect(Collectors.toSet());
		Set<MinecraftVersion> released = McProtocolReleaseCatalog.load(null).releases().stream()
				.map(ProtocolRelease::version)
				.collect(Collectors.toSet());

		assertEquals(released, expected, "add an expectation for each release key of mcprotocol-releases.toml");
	}

	@ParameterizedTest(name = "Minecraft {0}")
	@MethodSource("releases")
	void startsEachReleaseWithItsClientSegmentAndReportsTheExpectedCapabilities(
			String version,
			int protocol,
			String clientSegment,
			TestReporter reporter
	) {
		ProtocolRelease release = release(version);
		assertEquals(protocol, release.getProtocolNumber());

		try (ProtocolWorkerProcess worker = new ProtocolWorkerProcess(release, GradleReleaseClosures.of(release))) {
			assertTrue(worker.diagnosticTail().contains("mcprotocol-client=" + clientSegment), worker.diagnosticTail());

			ProtocolPlayer player = worker.create(request(release, "Alice"), null);
			try {
				ProtocolChannel channel = player.channel().orElseThrow();
				Set<String> installed = channel.installedCapabilities();
				Map<String, String> unavailable = channel.unavailableCapabilities();
				reporter.publishEntry("capabilities", "installed=" + installed + ", unavailable=" + unavailable);

				assertEquals(BUILT_IN, builtIn(installed), "installed built-in capabilities");
				assertEquals(Set.of(), builtIn(unavailable.keySet()), "unavailable built-in capabilities: " + unavailable);
				unavailable.forEach((capability, reason) -> assertFalse(reason.isBlank(), capability));

				assertTrue(installed.contains(EventWorkerExtension.ID), installed.toString());
				assertEquals("no mcprotocol segment selected for Minecraft " + release.version() + " provides an adapter for "
						+ ProbePackets.class.getName(), unavailable.get(ProbeWorkerExtension.ID));

				MessageText echoed = typed(channel).request(EventWorkerExtension.ECHO, new MessageText("release " + version));
				assertEquals(new MessageText("release " + version), echoed);
			} finally {
				player.destroy();
			}
		}
	}

	@Test
	void installsTheCapabilityOfAProbeSegmentThroughItsAdapter() throws Exception {
		ProtocolRelease release = release("1.21.11");
		try (ProtocolWorkerProcess worker = workerWithProbe(release, "class " + ProbePacketsAdapter.class.getName() + " public class\n")) {
			ProtocolPlayer player = worker.create(request(release, "Alice"), null);
			try {
				ProtocolChannel channel = player.channel().orElseThrow();
				assertTrue(channel.installedCapabilities().contains(ProbeWorkerExtension.ID), channel.unavailableCapabilities().toString());
				assertEquals("probe", typed(channel).request(ProbeWorkerExtension.NAME, null));
			} finally {
				player.destroy();
			}
		}
	}

	@Test
	void makesTheCapabilityOfASegmentThatDoesNotLinkUnavailableWithTheFailingMember() throws Exception {
		ProtocolRelease release = release("1.21.11");
		String linkage = "class " + ProbePacketsAdapter.class.getName() + " public interface\nclass example.AbsentFromRelease public\n";
		try (ProtocolWorkerProcess worker = workerWithProbe(release, linkage)) {
			assertTrue(worker.diagnosticTail().contains("probe-mcprotocol=1.16.5"), worker.diagnosticTail());
			ProtocolPlayer player = worker.create(request(release, "Alice"), null);
			try {
				ProtocolChannel channel = player.channel().orElseThrow();
				assertFalse(channel.installedCapabilities().contains(ProbeWorkerExtension.ID));
				assertEquals("segment probe-mcprotocol 1.16.5 does not link against the MCProtocolLib release for Minecraft 1.21.11: "
						+ ProbePacketsAdapter.class.getName() + " is a class, linked as an interface",
						channel.unavailableCapabilities().get(ProbeWorkerExtension.ID));
				assertTrue(channel.installedCapabilities().contains(EventWorkerExtension.ID));
			} finally {
				player.destroy();
			}
		}
	}

	/**
	 * Starts a worker whose host class path also holds a probe segment of the probe capability with the given linkage.
	 */
	private ProtocolWorkerProcess workerWithProbe(ProtocolRelease release, String linkage) throws Exception {
		Path probe = temporary.resolve("probe-segment");
		Files.createDirectories(probe.resolve("META-INF/anvil/segment"));
		Files.createDirectories(probe.resolve("META-INF/services"));
		Files.writeString(probe.resolve("META-INF/anvil/segment.properties"), "library=mcprotocol\nowner=probe-mcprotocol\nsince=1.16.5\n");
		Files.writeString(probe.resolve("META-INF/anvil/segment/linkage.txt"), linkage);
		Files.writeString(probe.resolve("META-INF/services").resolve(ProbePackets.class.getName()), ProbePacketsAdapter.class.getName() + "\n");

		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		try (URLClassLoader withProbe = new URLClassLoader(new URL[]{probe.toUri().toURL()}, previous)) {
			thread.setContextClassLoader(withProbe);
			return new ProtocolWorkerProcess(release, GradleReleaseClosures.of(release));
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	private static Stream<Arguments> releases() {
		return RELEASES.stream().map(release -> Arguments.of(release.version(), release.protocol(), release.clientSegment()));
	}

	private static Set<String> builtIn(Set<String> capabilities) {
		return capabilities.stream().filter(BUILT_IN::contains).collect(Collectors.toSet());
	}

	private ProtocolRelease release(String version) {
		MinecraftVersion key = MinecraftVersion.parse(version);
		return McProtocolReleaseCatalog.load(null).releases().stream()
				.filter(release -> release.version().equals(key))
				.findFirst()
				.orElseThrow();
	}

	private PlayerRequest request(ProtocolRelease release, String name) {
		return PlayerRequest.builder()
				.name(name)
				.clientVersion(release.version())
				.connection(GameConnection.builder()
						.address(new InetSocketAddress("127.0.0.1", 9))
						.virtualHost("lobby.example.test")
						.sourceAddress(InetAddress.getLoopbackAddress())
						.build())
				.build();
	}

	private CapabilityChannel typed(ProtocolChannel raw) {
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

	/**
	 * What the worker of one release key must report.
	 */
	private record ReleaseExpectation(String version, int protocol, String clientSegment) {
	}
}
