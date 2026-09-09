package me.whereareiam.anvil.environment.provisioning.artifact;

import com.sun.net.httpserver.HttpServer;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.cache.api.model.CacheKey;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpArtifactAcquirerTest {
	@TempDir
	Path temporary;

	@Test
	void downloadsAtomicallyAndChecksSha256() throws Exception {
		byte[] content = "immutable artifact".getBytes(StandardCharsets.UTF_8);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/artifact", exchange -> {
			exchange.sendResponseHeaders(200, content.length);
			exchange.getResponseBody().write(content);
			exchange.close();
		});
		server.start();
		try (var artifacts = new HttpArtifactAcquirer(temporary, new TestArtifactStorage(new FileCache(temporary)), false, false, 4)) {
			var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/artifact");
			Path destination = temporary.resolve("cache/artifact.jar");
			String sha = "eca6f2c7063ef1bf0c7a3ee5beab0e50fb58b13e205106677b8a2470ad8e00ab";
			assertArrayEquals(content, Files.readAllBytes(artifacts.obtain(uri, destination, sha)));
			Set<Path> beforeFailedReplacement = paths();
			ProvisioningException failure = assertThrows(ProvisioningException.class,
					() -> artifacts.obtain(uri, destination, "00"));
			assertTrue(failure.getMessage().contains("SHA-256 mismatch"));
			assertArrayEquals(content, Files.readAllBytes(destination));
			assertEquals(beforeFailedReplacement, paths(), "A rejected replacement must not leave staging data");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void refreshFailurePreservesMetadataAndSharedCacheSurvivesTransportClosure() throws Exception {
		AtomicReference<String> body = new AtomicReference<>("first metadata");
		AtomicInteger status = new AtomicInteger(200);
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/metadata", exchange -> {
			requests.incrementAndGet();
			byte[] content = body.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status.get(), content.length);
			exchange.getResponseBody().write(content);
			exchange.close();
		});
		server.start();
		URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/metadata");
		FileCache cache = new FileCache(temporary);
		try {
			try (var cached = new HttpArtifactAcquirer(temporary, new TestArtifactStorage(cache), false, false, 1)) {
				assertEquals("first metadata", cached.read(uri));
				body.set("refreshed metadata");
				assertEquals("first metadata", cached.read(uri));
				assertEquals(1, requests.get());
			}

			try (var refreshing = new HttpArtifactAcquirer(temporary, new TestArtifactStorage(cache), false, true, 1)) {
				assertEquals("refreshed metadata", refreshing.read(uri));
				assertEquals(2, requests.get());
				Set<Path> beforeFailedRefresh = paths();
				status.set(503);
				ProvisioningException failure = assertThrows(ProvisioningException.class, () -> refreshing.read(uri));
				assertTrue(failure.getMessage().contains("HTTP 503"));
				assertEquals(beforeFailedRefresh, paths(), "A failed refresh must clean its staged download");
			}

			try (var offline = new HttpArtifactAcquirer(temporary, new TestArtifactStorage(cache), true, false, 1)) {
				assertEquals("refreshed metadata", offline.read(uri));
				assertEquals(3, requests.get());
			}
		} finally {
			server.stop(0);
		}
	}

	@Test
	void verifiesPinnedContentBeforeReusingItOffline() throws Exception {
		Path destination = temporary.resolve("artifact.jar");
		Files.writeString(destination, "immutable artifact");
		URI uri = URI.create("https://example.invalid/artifact.jar");
		String sha = "ECA6F2C7063EF1BF0C7A3EE5BEAB0E50FB58B13E205106677B8A2470AD8E00AB";
		try (var artifacts = new HttpArtifactAcquirer(temporary, new TestArtifactStorage(new FileCache(temporary)), true, false, 1)) {
			assertEquals(destination, artifacts.obtain(uri, destination, sha));
			Files.writeString(destination, "damaged artifact");
			ProvisioningException failure = assertThrows(ProvisioningException.class,
					() -> artifacts.obtain(uri, destination, sha));
			assertTrue(failure.getMessage().contains("unavailable offline"));
			assertEquals("damaged artifact", Files.readString(destination));
		}
	}

	@Test
	void reusesMetadataFromTheExistingCacheLayout() throws Exception {
		URI uri = URI.create("https://example.invalid/catalog");
		FileCache cache = new FileCache(temporary);
		Path metadata = cache.path(CacheKey.builder().namespace("metadata").value(uri.toString()).suffix(".json").build());
		Files.createDirectories(metadata.getParent());
		Files.writeString(metadata, "existing metadata");

		try (var artifacts = new HttpArtifactAcquirer(temporary, new TestArtifactStorage(cache), true, false, 1)) {
			assertEquals("existing metadata", artifacts.read(uri));
		}
	}

	@Test
	void reportsCacheAccessFailuresAsProvisioningFailuresWithTheOriginalCause() {
		CacheException storageFailure = new CacheException("Shared cache is inaccessible");
		FileCache storage = new FileCache(temporary);
		Cache unavailable = new Cache() {
			@Override
			public @NotNull Path path(@NotNull CacheKey key) {
				return storage.path(key);
			}

			@Override
			public @NotNull CacheEntry open(@NotNull Path entry) {
				throw storageFailure;
			}
		};
		URI uri = URI.create("https://example.invalid/artifact.jar");
		try (var artifacts = new HttpArtifactAcquirer(temporary, new TestArtifactStorage(unavailable), true, false, 1)) {
			ProvisioningException acquisition = assertThrows(ProvisioningException.class,
					() -> artifacts.obtain(uri, temporary.resolve("artifact.jar"), null));
			assertSame(storageFailure, acquisition.getCause().getCause());
			assertTrue(acquisition.getMessage().contains(uri.toString()));

			ProvisioningException metadata = assertThrows(ProvisioningException.class, () -> artifacts.read(uri));
			assertSame(storageFailure, metadata.getCause().getCause());
			assertTrue(metadata.getMessage().contains(uri.toString()));
		}
	}

	private Set<Path> paths() throws Exception {
		try (var paths = Files.walk(temporary)) {
			return paths.map(temporary::relativize).collect(Collectors.toSet());
		}
	}
}
