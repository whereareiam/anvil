package me.whereareiam.anvil.protocol.mcprotocol.segment;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.mcprotocol.catalog.McProtocolReleaseCatalog;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.GradleReleaseClosures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feeds the linkage manifests that the build wrote into the real client segment JARs to the runtime reader, and
 * verifies each against the locked closure of every release the segment serves, as a worker's self-check does. It
 * fails when the build's manifest format and the runtime reader drift apart, or a release no longer links.
 */
class ClientSegmentLinkageTest {
	private static final String CLIENT = "mcprotocol-client";

	@Test
	void everyClientSegmentLinksAgainstEachReleaseItServesWithEveryRecordedRequirement() throws Exception {
		Map<MinecraftVersion, String> segments = clientSegments();
		assertEquals(List.of("1.18.2", "1.21.11"), segments.keySet().stream().map(MinecraftVersion::toString).toList());

		for (ProtocolRelease release : McProtocolReleaseCatalog.load(null).releases()) {
			MinecraftVersion since = MinecraftVersion.floor(segments.keySet(), version -> version, release.version()).orElseThrow();
			String manifest = text(segments.get(since) + SegmentDescriptor.LINKAGE);
			assertTrue(manifest.lines().allMatch(ClientSegmentLinkageTest::recordsRequirements),
					"segment " + since + " records requirements for every reference:\n" + manifest);

			try (URLClassLoader loader = closure(release)) {
				Optional<String> failure = linkage(segments.get(since)).firstFailure(loader);
				assertEquals(Optional.empty(), failure, "segment " + since + " against MCProtocolLib " + release.getLibraryVersion());
			}
		}
	}

	@Test
	void refusesAClientSegmentOnAReleaseItDoesNotServe() throws Exception {
		Map<MinecraftVersion, String> segments = clientSegments();
		ProtocolRelease modern = McProtocolReleaseCatalog.load(null).releases().stream()
				.max(Comparator.comparing(ProtocolRelease::version))
				.orElseThrow();

		try (URLClassLoader loader = closure(modern)) {
			Optional<String> failure = linkage(segments.get(MinecraftVersion.parse("1.18.2"))).firstFailure(loader);
			assertTrue(failure.isPresent() && failure.get().startsWith("missing "), failure.toString());
		}
	}

	/**
	 * Finds the client segment JARs on the test class path, keyed by start version, as roots of their resources.
	 */
	private Map<MinecraftVersion, String> clientSegments() throws IOException {
		Map<MinecraftVersion, String> segments = new LinkedHashMap<>();
		ClassLoader loader = getClass().getClassLoader();
		for (URL found : Collections.list(loader.getResources(SegmentDescriptor.PROPERTIES))) {
			String location = found.toString();
			String root = location.substring(0, location.length() - SegmentDescriptor.PROPERTIES.length());
			try (InputStream input = found.openStream()) {
				SegmentDescriptor descriptor = SegmentDescriptor.read(input, location);
				if (descriptor.getOwner().equals(CLIENT)) segments.put(descriptor.getSince(), root);
			}
		}

		Map<MinecraftVersion, String> sorted = new LinkedHashMap<>();
		segments.keySet().stream().sorted().forEach(since -> sorted.put(since, segments.get(since)));
		return sorted;
	}

	/**
	 * Whether a manifest line carries requirement keywords after its kind, owner and member: every class reference
	 * records its access or class kind, and every member reference whether it is static.
	 */
	private static boolean recordsRequirements(String line) {
		String[] parts = line.split(" ");
		return parts.length > (parts[0].equals("class") ? 2 : 3);
	}

	private SegmentLinkage linkage(String root) {
		try (InputStream input = URI.create(root + SegmentDescriptor.LINKAGE).toURL().openStream()) {
			return SegmentLinkage.read(input, root + SegmentDescriptor.LINKAGE);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private String text(String location) {
		try (InputStream input = URI.create(location).toURL().openStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/**
	 * Loads a release's locked closure on top of the JDK alone, as a worker does.
	 */
	private URLClassLoader closure(ProtocolRelease release) throws IOException {
		List<Path> files = GradleReleaseClosures.of(release);
		URL[] urls = new URL[files.size()];
		for (int index = 0; index < urls.length; index++) urls[index] = files.get(index).toUri().toURL();

		return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
	}
}
