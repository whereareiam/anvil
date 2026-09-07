package me.whereareiam.anvil.provisioning.cache;

import com.sun.net.httpserver.HttpServer;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactCacheConcurrencyTest {
	@TempDir
	Path directory;

	@Test
	void sharesOneTransferAcrossIndependentCacheInstancesAndSupportsOfflineMetadata() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			byte[] content = "immutable".getBytes();
			exchange.sendResponseHeaders(200, content.length);
			exchange.getResponseBody().write(content);
			exchange.close();
		});
		server.start();
		URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/artifact");
		Path target = directory.resolve("artifact.jar");
		try (var first = new ArtifactCache(directory, false, false, 2);
		     var second = new ArtifactCache(directory, false, false, 2);
		     var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = IntStream.range(0, 12).mapToObj(index -> tasks.submit(() ->
					(index % 2 == 0 ? first : second).obtain(uri, target, null))).toList();
			for (var future : futures) assertEquals(target, future.get(5, TimeUnit.SECONDS));
			assertEquals(1, requests.get());
			assertEquals("immutable", first.read(uri));
		} finally {
			server.stop(0);
		}

		try (var offline = new ArtifactCache(directory, true, false, 2)) {
			assertEquals("immutable", offline.read(uri));
			assertEquals(target, offline.obtain(uri, target, null));
			assertThrows(ProvisioningException.class, () -> offline.obtain(uri, directory.resolve("missing"), null));
		}
	}

	@Test
	void downloadsIndependentEntriesTogether() throws Exception {
		CountDownLatch entered = new CountDownLatch(2);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		try (var handlers = Executors.newVirtualThreadPerTaskExecutor();
		     var tasks = Executors.newVirtualThreadPerTaskExecutor();
		     var cache = new ArtifactCache(directory, false, false, 2)) {
			server.setExecutor(handlers);
			server.createContext("/", exchange -> {
				entered.countDown();
				try {
					if (!entered.await(5, TimeUnit.SECONDS)) {
						exchange.sendResponseHeaders(503, -1);
						return;
					}
					exchange.sendResponseHeaders(200, 1);
					exchange.getResponseBody().write(1);
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
				} finally {
					exchange.close();
				}
			});
			server.start();
			URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
			var first = tasks.submit(() -> cache.obtain(uri.resolve("first"), directory.resolve("first"), null));
			var second = tasks.submit(() -> cache.obtain(uri.resolve("second"), directory.resolve("second"), null));
			assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
				assertTrue(Files.exists(first.get()));
				assertTrue(Files.exists(second.get()));
			});
		} finally {
			server.stop(0);
		}
	}
}
