package me.whereareiam.anvil.player.library.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.library.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.player.library.mcprotocol.worker.fixture.ProbePackets;
import me.whereareiam.anvil.player.library.mcprotocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.player.library.mcprotocol.worker.fixture.TestSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerSegmentsTest {
	private static final String PROBE_SERVICE = ProbePackets.class.getName();
	private static final String CLIENT = "client.segment.SegmentClient";
	private static final String CLIENT_LINKAGE = "class " + RecordingClient.class.getName() + " public class\nmethod "
			+ RecordingClient.class.getName() + " protocolNumber()I public instance class\n";
	private static final String PROBE = "probe.segment.ProbeAdapter";
	private static final String PROBE_LINKAGE = "class " + PROBE + " public class\n";

	@TempDir
	Path temporary;

	private final List<String> warnings = new ArrayList<>();

	@Test
	void verifiesSelectedSegmentsAndHandsOutTheirAdapters() throws Exception {
		TestSegment client = client("client", CLIENT_LINKAGE);
		TestSegment probe = TestSegment.create(temporary.resolve("probe"), "mcprotocol", "probe-mcprotocol", "1.18.2", PROBE_LINKAGE)
				.probe(PROBE);
		TestSegment other = TestSegment.create(temporary.resolve("other"), "otherlib", "probe-otherlib", "1.16.5",
				"class example.Missing\n");

		try (URLClassLoader loader = SegmentRoots.loader(roots(client, probe, other))) {
			WorkerSegments segments = WorkerSegments.verify(loader, MinecraftVersion.parse("1.18.2"), warnings::add);

			assertEquals(Map.of("mcprotocol-java", "1.16.5", "probe-mcprotocol", "1.18.2"), segments.selected());
			McProtocolClient<Object> created = segments.client();
			assertInstanceOf(RecordingClient.class, created);
			assertEquals(CLIENT, created.getClass().getName());
			ProbePackets adapter = segments.adapter(ProbePackets.class);
			assertEquals("probe", adapter.name());
			assertEquals(PROBE, adapter.getClass().getName());
			assertSame(loader, adapter.getClass().getClassLoader(), "The adapter's class comes from its segment");
			assertSame(adapter, segments.adapter(ProbePackets.class), "Adapters are shared by every player");
			assertEquals(List.of(), warnings);
		}
	}

	@Test
	void makesTheAdaptersOfASegmentThatDoesNotLinkUnavailableWithTheFailingMember() throws Exception {
		TestSegment client = client("client", CLIENT_LINKAGE);
		TestSegment probe = TestSegment.create(temporary.resolve("probe"), "mcprotocol", "probe-mcprotocol", "1.16.5",
				PROBE_LINKAGE + "method " + PROBE + " name()Ljava/lang/String; public static class\n").probe(PROBE);

		try (URLClassLoader loader = SegmentRoots.loader(roots(client, probe))) {
			WorkerSegments segments = WorkerSegments.verify(loader, MinecraftVersion.parse("1.17.1"), warnings::add);

			String reason = "segment probe-mcprotocol 1.16.5 does not link against the MCProtocolLib release for Minecraft 1.17.1: "
					+ "method " + PROBE + " name()Ljava/lang/String; is not static, linked as static";
			assertEquals(List.of("[Anvil] Warning: " + reason + "; its adapters are unavailable"), warnings);
			ProtocolUnavailableException unavailable = assertThrows(ProtocolUnavailableException.class,
					() -> segments.adapter(ProbePackets.class));
			assertEquals(reason, unavailable.getMessage());
			assertInstanceOf(RecordingClient.class, segments.client());
		}
	}

	@Test
	void namesWhyAPortHasNoAdapter() throws Exception {
		TestSegment client = client("client", CLIENT_LINKAGE);
		TestSegment first = TestSegment.create(temporary.resolve("first"), "mcprotocol", "probe-mcprotocol", "1.16.5", "")
				.probe("probe.first.ProbeAdapter");
		TestSegment second = TestSegment.create(temporary.resolve("second"), "mcprotocol", "other-mcprotocol", "1.16.5", "")
				.probe("probe.second.ProbeAdapter");
		TestSegment plain = TestSegment.plain(temporary.resolve("plain")).probe("probe.plain.ProbeAdapter");

		assertUnavailable("no mcprotocol segment selected for Minecraft 1.18.2 provides an adapter for " + PROBE_SERVICE, client);
		assertUnavailable("more than one adapter for " + PROBE_SERVICE + " is on the worker class path: "
				+ "[probe.first.ProbeAdapter in segment probe-mcprotocol 1.16.5, "
				+ "probe.second.ProbeAdapter in segment other-mcprotocol 1.16.5]", client, first, second);
		assertUnavailable("the adapter probe.plain.ProbeAdapter for " + PROBE_SERVICE + " is declared outside a segment, by "
				+ plain.root().toUri().toURL(), client, plain);
	}

	@Test
	void reportsAnAdapterThatCannotBeLoadedOrCreated() throws Exception {
		TestSegment client = client("client", CLIENT_LINKAGE);
		TestSegment unrelated = TestSegment.create(temporary.resolve("unrelated"), "mcprotocol", "probe-mcprotocol", "1.16.5", "")
				.provide(ProbePackets.class, "probe.unrelated.Unrelated", "package probe.unrelated; public final class Unrelated { }");
		TestSegment refusing = TestSegment.create(temporary.resolve("refusing"), "mcprotocol", "probe-mcprotocol", "1.16.5", "")
				.provide(ProbePackets.class, "probe.refusing.ProbeAdapter", """
						package probe.refusing;
						public final class ProbeAdapter implements %s {
							public ProbeAdapter() { throw new IllegalStateException("refused"); }
							public String name() { return "refusing"; }
						}
						""".formatted(PROBE_SERVICE));

		assertUnavailable("could not load the adapters for " + PROBE_SERVICE + ": ServiceConfigurationError: " + PROBE_SERVICE
				+ ": probe.unrelated.Unrelated not a subtype", client, unrelated);
		assertUnavailable("could not create the adapter probe.refusing.ProbeAdapter of segment probe-mcprotocol 1.16.5: "
				+ "IllegalStateException: refused", client, refusing);
	}

	@Test
	void failsTheWorkerWhenTheClientSegmentDoesNotLinkOrIsMissing() throws Exception {
		TestSegment client = client("client", "method " + RecordingClient.class.getName() + " absent()V public instance class\n");

		try (URLClassLoader loader = SegmentRoots.loader(roots(client))) {
			WorkerSegments segments = WorkerSegments.verify(loader, MinecraftVersion.parse("1.16.5"), warnings::add);
			IllegalStateException failure = assertThrows(IllegalStateException.class, segments::client);
			assertTrue(
					failure.getMessage().startsWith("Cannot load the MCProtocolLib client: segment mcprotocol-java 1.16.5"),
					failure.getMessage()
			);
			assertTrue(
					failure.getMessage().endsWith("missing method " + RecordingClient.class.getName() + " absent()V"),
					failure.getMessage()
			);
		}
		try (URLClassLoader loader = SegmentRoots.loader()) {
			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> WorkerSegments.verify(loader, MinecraftVersion.parse("1.16.5"), warnings::add).client());
			assertTrue(failure.getMessage().contains("provides an adapter for " + McProtocolClient.class.getName()), failure.getMessage());
		}
	}

	@Test
	void requiresOneSegmentPerOwner() throws Exception {
		TestSegment probe = TestSegment.create(temporary.resolve("probe"), "mcprotocol", "probe-mcprotocol", "1.16.5", "");
		TestSegment duplicate = TestSegment.create(temporary.resolve("duplicate"), "mcprotocol", "probe-mcprotocol", "1.18.2", "");

		try (URLClassLoader loader = SegmentRoots.loader(roots(probe, duplicate))) {
			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> WorkerSegments.verify(loader, MinecraftVersion.parse("1.18.2"), warnings::add));
			assertTrue(failure.getMessage().contains("more than one segment of probe-mcprotocol"), failure.getMessage());
		}
	}

	private void assertUnavailable(String message, TestSegment... segments) throws Exception {
		try (URLClassLoader loader = SegmentRoots.loader(roots(segments))) {
			WorkerSegments verified = WorkerSegments.verify(loader, MinecraftVersion.parse("1.18.2"), warnings::add);
			ProtocolUnavailableException failure = assertThrows(ProtocolUnavailableException.class,
					() -> verified.adapter(ProbePackets.class));
			assertEquals(message, failure.getMessage());
		}
	}

	/**
	 * Creates the client segment, which provides a recording client compiled into it.
	 */
	private TestSegment client(String name, String linkage) {
		return TestSegment.create(temporary.resolve(name), "mcprotocol", "mcprotocol-java", "1.16.5", linkage)
				.provide(McProtocolClient.class, CLIENT,
						"package client.segment; public final class SegmentClient extends " + RecordingClient.class.getName() + " { }");
	}

	private static Path[] roots(TestSegment... segments) {
		return Arrays.stream(segments).map(TestSegment::root).toArray(Path[]::new);
	}
}
