package me.whereareiam.anvil.protocol.worker.host;

import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.worker.child.WorkerClient;
import me.whereareiam.anvil.protocol.worker.fixture.ProbeLibrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerClasspathTest {
	private static final String CLIENT_SERVICE = "META-INF/services/" + WorkerClient.class.getName();

	@TempDir
	Path temporary;

	private Path closure;
	private Path firstClient;
	private Path laterClient;
	private Path firstProbe;
	private Path laterProbe;
	private Path otherLibrary;
	private Path capability;

	@BeforeEach
	void createEntries() throws Exception {
		closure = jar("protocol.jar", Map.of("example/probe/Protocol.class", ""));
		firstClient = segment("client-V1_16_5.jar", "probelib", "probelib-client", "1.16.5", true);
		laterClient = segment("client-V1_20_6.jar", "probelib", "probelib-client", "1.20.6", true);
		firstProbe = segment("probe-V1_16_5.jar", "probelib", "probe-probelib", "1.16.5", false);
		laterProbe = segment("probe-V1_18_2.jar", "probelib", "probe-probelib", "1.18.2", false);
		otherLibrary = segment("probe-otherlib.jar", "otherlib", "probe-otherlib", "1.16.5", false);
		capability = Files.createDirectories(temporary.resolve("capability/me/whereareiam/example"));
		Files.writeString(capability.resolve("Capability.class"), "");
		capability = temporary.resolve("capability");
	}

	@Test
	void putsTheClosureFirstAndKeepsTheFloorSegmentOfEachOwner() {
		List<Path> host = List.of(capability, laterClient, firstProbe, closure, firstClient, laterProbe, otherLibrary);

		assertEquals(List.of(closure, capability, firstClient, laterProbe), resolve("1.19.4", host));
		assertEquals(List.of(closure, capability, laterClient, laterProbe), resolve("26.1.2", host));
		assertEquals(List.of(closure, capability, firstProbe, firstClient), resolve("1.17.1", host));
	}

	@Test
	void requiresOneClientSegmentForTheReleaseKey() throws Exception {
		IllegalStateException none = assertThrows(IllegalStateException.class, () -> resolve("1.16.4", List.of(firstClient, laterClient)));
		assertTrue(none.getMessage().contains("needs exactly one client segment serving Minecraft 1.16.4"), none.getMessage());

		Path plain = jar("plain-client.jar", Map.of(CLIENT_SERVICE, "example.Client"));
		IllegalStateException unsegmented = assertThrows(IllegalStateException.class, () -> resolve("1.16.5", List.of(firstClient, plain)));
		assertTrue(unsegmented.getMessage().contains(plain.toString()), unsegmented.getMessage());

		Path duplicate = segment("client-copy.jar", "probelib", "probelib-client", "1.16.5", true);
		IllegalStateException twice = assertThrows(IllegalStateException.class, () -> resolve("1.16.5", List.of(firstClient, duplicate)));
		assertTrue(twice.getMessage().contains("both start at Minecraft 1.16.5"), twice.getMessage());
	}

	@Test
	void refusesHostEntriesThatCarryPackagesTheLibrarysClosureOwns() throws Exception {
		Path netty = jar("fat.jar", Map.of("io/netty/channel/Channel.class", ""));
		Path probe = Files.createDirectories(temporary.resolve("classes/example/probe/text"));
		Files.writeString(probe.resolve("Component.class"), "");
		Path foreign = jar("foreign.jar", Map.of("net/kyori/adventure/text/Component.class", ""));

		IllegalStateException jar = assertThrows(IllegalStateException.class, () -> resolve("1.16.5", List.of(firstClient, netty)));
		assertTrue(jar.getMessage().startsWith("Worker class path entry " + netty + " contains [io/netty/]"), jar.getMessage());
		IllegalStateException directory = assertThrows(IllegalStateException.class,
				() -> resolve("1.16.5", List.of(firstClient, temporary.resolve("classes"))));
		assertTrue(directory.getMessage().contains("[example/probe/]"), directory.getMessage());
		assertTrue(directory.getMessage().endsWith("runtime closure of ProbeLib release release-1.16.5"), directory.getMessage());
		assertEquals(List.of(closure, firstClient, foreign), resolve("1.16.5", List.of(firstClient, foreign)),
				"packages another library's closure owns are no concern of this one");
	}

	@Test
	void collectsTheHostClassPathAndFileUrlsOfTheContextLoaders() throws Exception {
		try (var loader = new URLClassLoader(new URL[]{capability.toUri().toURL()}, null)) {
			List<Path> entries = WorkerClasspath.hostEntries(loader);
			assertTrue(entries.contains(capability.toAbsolutePath()));
			assertTrue(entries.size() > 1);
		}
		try (var remote = new URLClassLoader(new URL[]{URI.create("https://repository.example/remote.jar").toURL()}, null)) {
			assertThrows(IllegalArgumentException.class, () -> WorkerClasspath.hostEntries(remote));
		}
	}

	private List<Path> resolve(String version, List<Path> host) {
		ProtocolRelease release = ProtocolRelease.builder()
				.libraryVersion("release-" + version)
				.edition(MinecraftEdition.of("java"))
				.minecraftVersion(MinecraftVersion.parse(version))
				.protocolNumber(MinecraftVersion.parse(version), 1)
				.javaVersion(17)
				.build();
		return new WorkerClasspath(ProbeLibrary.LIBRARY).resolve(release, List.of(closure), host);
	}

	private Path segment(String name, String library, String owner, String since, boolean client) throws Exception {
		String properties = "library=" + library + "\nowner=" + owner + "\nsince=" + since + "\n";
		if (!client) return jar(name, Map.of("META-INF/anvil/segment.properties", properties, "example/Adapter.class", ""));
		return jar(name, Map.of("META-INF/anvil/segment.properties", properties, CLIENT_SERVICE, "example.Client"));
	}

	private Path jar(String name, Map<String, String> entries) throws Exception {
		Path path = temporary.resolve(name);
		try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				jar.putNextEntry(new JarEntry(entry.getKey()));
				jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				jar.closeEntry();
			}
		}
		return path.toAbsolutePath();
	}
}
