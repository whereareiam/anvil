package me.whereareiam.anvil.engine.provisioning.artifact;

import com.sun.net.httpserver.HttpServer;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadCacheTest {
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
		try {
			var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/artifact");
			Path destination = temporary.resolve("cache/artifact.jar");
			String sha = "eca6f2c7063ef1bf0c7a3ee5beab0e50fb58b13e205106677b8a2470ad8e00ab";
			assertArrayEquals(content, Files.readAllBytes(new DownloadCache().obtain(uri, destination, sha)));
			assertThrows(ProvisioningException.class,
					() -> new DownloadCache().obtain(uri, temporary.resolve("bad.jar"), "00"));
		} finally {
			server.stop(0);
		}
	}
}
