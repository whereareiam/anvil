package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.exception.NativeAdapterUnavailableException;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.ProbePackets;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.ProbePacketsAdapter;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.RecordingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerSegmentsTest {
	private static final String CLIENT_SERVICE = McProtocolClient.class.getName();
	private static final String PROBE_SERVICE = ProbePackets.class.getName();
	private static final String CLIENT_LINKAGE = "class " + RecordingClient.class.getName() + " public class\nmethod "
			+ RecordingClient.class.getName() + " protocolNumber()I public instance class\n";
	private static final String PROBE_LINKAGE = "class " + ProbePacketsAdapter.class.getName() + " public class\n";

	@TempDir
	Path temporary;

	private final List<String> warnings = new ArrayList<>();

	@Test
	void verifiesSelectedSegmentsAndHandsOutTheirAdapters() throws Exception {
		Path client = segment("client", "mcprotocol", "mcprotocol-client", "1.16.5", CLIENT_LINKAGE,
				Map.of(CLIENT_SERVICE, RecordingClient.class.getName()));
		Path probe = segment("probe", "mcprotocol", "probe-mcprotocol", "1.18.2", PROBE_LINKAGE,
				Map.of(PROBE_SERVICE, "# the probe adapter\n" + ProbePacketsAdapter.class.getName()));
		Path other = segment("other", "otherlib", "probe-otherlib", "1.16.5", "class example.Missing\n", Map.of());

		try (URLClassLoader loader = SegmentRoots.loader(client, probe, other)) {
			WorkerSegments segments = WorkerSegments.verify(loader, MinecraftVersion.parse("1.18.2"), warnings::add);

			assertEquals(Map.of("mcprotocol-client", "1.16.5", "probe-mcprotocol", "1.18.2"), segments.selected());
			assertInstanceOf(RecordingClient.class, segments.client());
			ProbePackets adapter = segments.adapter(ProbePackets.class);
			assertEquals("probe", adapter.name());
			assertSame(adapter, segments.adapter(ProbePackets.class), "Adapters are shared by every player");
			assertEquals(List.of(), warnings);
		}
	}

	@Test
	void makesTheAdaptersOfASegmentThatDoesNotLinkUnavailableWithTheFailingMember() throws Exception {
		Path client = segment("client", "mcprotocol", "mcprotocol-client", "1.16.5", CLIENT_LINKAGE,
				Map.of(CLIENT_SERVICE, RecordingClient.class.getName()));
		Path probe = segment("probe", "mcprotocol", "probe-mcprotocol", "1.16.5",
				PROBE_LINKAGE + "method " + ProbePacketsAdapter.class.getName() + " name()Ljava/lang/String; public static class\n",
				Map.of(PROBE_SERVICE, ProbePacketsAdapter.class.getName()));

		try (URLClassLoader loader = SegmentRoots.loader(client, probe)) {
			WorkerSegments segments = WorkerSegments.verify(loader, MinecraftVersion.parse("1.17.1"), warnings::add);

			String reason = "segment probe-mcprotocol 1.16.5 does not link against the MCProtocolLib release for Minecraft 1.17.1: "
					+ "method " + ProbePacketsAdapter.class.getName() + " name()Ljava/lang/String; is not static, linked as static";
			assertEquals(List.of("[Anvil] Warning: " + reason + "; its adapters are unavailable"), warnings);
			NativeAdapterUnavailableException unavailable = assertThrows(NativeAdapterUnavailableException.class,
					() -> segments.adapter(ProbePackets.class));
			assertEquals(reason, unavailable.getMessage());
			assertInstanceOf(RecordingClient.class, segments.client());
		}
	}

	@Test
	void namesWhyAPortHasNoAdapter() throws Exception {
		Path client = segment("client", "mcprotocol", "mcprotocol-client", "1.16.5", CLIENT_LINKAGE,
				Map.of(CLIENT_SERVICE, RecordingClient.class.getName()));
		Path first = segment("first", "mcprotocol", "probe-mcprotocol", "1.16.5", PROBE_LINKAGE, Map.of(PROBE_SERVICE, ProbePacketsAdapter.class.getName()));
		Path second = segment("second", "mcprotocol", "other-mcprotocol", "1.16.5", PROBE_LINKAGE, Map.of(PROBE_SERVICE, ProbePacketsAdapter.class.getName()));
		Path plain = Files.createDirectories(temporary.resolve("plain/META-INF/services")).resolve(PROBE_SERVICE);
		Files.writeString(plain, ProbePacketsAdapter.class.getName());

		assertUnavailable("no mcprotocol segment selected for Minecraft 1.18.2 provides an adapter for " + PROBE_SERVICE, client);
		assertUnavailable("more than one adapter for " + PROBE_SERVICE + " is on the worker class path: ["
				+ ProbePacketsAdapter.class.getName() + " in segment probe-mcprotocol 1.16.5, "
				+ ProbePacketsAdapter.class.getName() + " in segment other-mcprotocol 1.16.5]", client, first, second);
		assertUnavailable("is declared outside a segment", client, temporary.resolve("plain"));
	}

	@Test
	void failsTheWorkerWhenTheClientSegmentDoesNotLinkOrIsMissing() throws Exception {
		Path client = segment("client", "mcprotocol", "mcprotocol-client", "1.16.5",
				"method " + RecordingClient.class.getName() + " absent()V public instance class\n", Map.of(CLIENT_SERVICE, RecordingClient.class.getName()));

		try (URLClassLoader loader = SegmentRoots.loader(client)) {
			WorkerSegments segments = WorkerSegments.verify(loader, MinecraftVersion.parse("1.16.5"), warnings::add);
			IllegalStateException failure = assertThrows(IllegalStateException.class, segments::client);
			assertTrue(failure.getMessage().startsWith("Cannot load the MCProtocolLib client: segment mcprotocol-client 1.16.5"), failure.getMessage());
			assertTrue(failure.getMessage().endsWith("missing method " + RecordingClient.class.getName() + " absent()V"), failure.getMessage());
		}
		try (URLClassLoader loader = SegmentRoots.loader()) {
			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> WorkerSegments.verify(loader, MinecraftVersion.parse("1.16.5"), warnings::add).client());
			assertTrue(failure.getMessage().contains("provides an adapter for " + CLIENT_SERVICE), failure.getMessage());
		}
	}

	@Test
	void requiresOneSegmentPerOwner() throws Exception {
		Path probe = segment("probe", "mcprotocol", "probe-mcprotocol", "1.16.5", "", Map.of());
		Path duplicate = segment("duplicate", "mcprotocol", "probe-mcprotocol", "1.18.2", "", Map.of());

		try (URLClassLoader loader = SegmentRoots.loader(probe, duplicate)) {
			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> WorkerSegments.verify(loader, MinecraftVersion.parse("1.18.2"), warnings::add));
			assertTrue(failure.getMessage().contains("more than one segment of probe-mcprotocol"), failure.getMessage());
		}
	}

	private void assertUnavailable(String message, Path... roots) throws Exception {
		try (URLClassLoader loader = SegmentRoots.loader(roots)) {
			WorkerSegments segments = WorkerSegments.verify(loader, MinecraftVersion.parse("1.18.2"), warnings::add);
			NativeAdapterUnavailableException failure = assertThrows(NativeAdapterUnavailableException.class,
					() -> segments.adapter(ProbePackets.class));
			assertTrue(failure.getMessage().contains(message), failure.getMessage());
		}
	}

	private Path segment(String name, String library, String owner, String since, String linkage, Map<String, String> services) throws Exception {
		Path root = temporary.resolve(name);
		Files.createDirectories(root.resolve("META-INF/anvil/segment"));
		Files.writeString(root.resolve("META-INF/anvil/segment.properties"), "library=" + library + "\nowner=" + owner + "\nsince=" + since + "\n");
		Files.writeString(root.resolve("META-INF/anvil/segment/linkage.txt"), linkage);
		for (Map.Entry<String, String> service : services.entrySet()) {
			Path file = root.resolve("META-INF/services").resolve(service.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, service.getValue() + "\n");
		}
		return root;
	}
}
