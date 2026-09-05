package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import me.whereareiam.anvil.protocol.mcprotocol.model.ProtocolDefinition;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Resolves a pinned protocol artifact without exposing Maven version mechanics to users.
 */
public final class ProtocolArtifactResolver {
	private final HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(30))
			.build();

	/**
	 * Obtains the exact catalog artifact, verifying cached and downloaded bytes by SHA-256.
	 *
	 * @param definition pinned runtime definition
	 * @param cacheDirectory cache root used by this backend
	 * @return verified local runtime JAR
	 */
	public @NotNull Path resolve(@NotNull ProtocolDefinition definition, @NotNull Path cacheDirectory) {
		String filename = "protocol-" + definition.getSupport().getLibraryVersion() + ".jar";
		Path destination = cacheDirectory.resolve("protocol")
				.resolve(definition.getSupport().getMinecraftVersion())
				.resolve(filename);
		try {
			if (Files.isRegularFile(destination) && sha256(destination).equals(definition.getSha256())) return destination;

			Files.createDirectories(destination.getParent());
			Path part = destination.resolveSibling(filename + ".part");
			HttpRequest request = HttpRequest.newBuilder(definition.getArtifact())
					.timeout(Duration.ofMinutes(3))
					.GET()
					.build();

			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				if (response.statusCode() < 200 || response.statusCode() >= 300)
					throw new IllegalStateException("Protocol download failed with HTTP " + response.statusCode());
				Files.copy(body, part, StandardCopyOption.REPLACE_EXISTING);
			}

			if (!sha256(part).equals(definition.getSha256())) {
				Files.deleteIfExists(part);
				throw new IllegalStateException("Protocol artifact checksum mismatch: " + definition.getArtifact());
			}

			try {
				Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
			}

			return destination;
		} catch (IOException e) {
			throw new IllegalStateException("Could not resolve " + definition.getArtifact(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while resolving " + definition.getArtifact(), e);
		}
	}

	private String sha256(Path file) throws IOException {
		try (InputStream stream = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];

			int count;
			while ((count = stream.read(buffer)) >= 0) digest.update(buffer, 0, count);

			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
