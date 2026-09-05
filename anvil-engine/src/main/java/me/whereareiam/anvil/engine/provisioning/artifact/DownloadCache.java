package me.whereareiam.anvil.engine.provisioning.artifact;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.platform.api.ArtifactResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
 * Content-checked HTTP artifact cache shared by platform and protocol resolvers.
 */
public final class DownloadCache implements ArtifactResolver {
	private final HttpClient client;

	/**
	 * Creates a cache using a redirect-capable JDK HTTP client.
	 */
	public DownloadCache() {
		client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
	}

	/**
	 * Returns a cached file, downloading it atomically when necessary.
	 *
	 * @param uri immutable artifact location
	 * @param destination cache destination
	 * @param expectedSha256 optional lowercase SHA-256
	 * @return verified local file
	 */
	@Override
	public synchronized @NotNull Path obtain(
			@NotNull URI uri,
			@NotNull Path destination,
			@Nullable String expectedSha256
	) {
		try {
			if (Files.isRegularFile(destination) && matches(destination, expectedSha256))
				return destination;

			Files.createDirectories(destination.getParent());
			Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
			HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build();
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() < 200 || response.statusCode() >= 300)
				throw new ProvisioningException("Download failed with HTTP " + response.statusCode() + ": " + uri);

			try (InputStream stream = response.body()) {
				Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
			}

			if (!matches(temporary, expectedSha256)) {
				Files.deleteIfExists(temporary);
				throw new ProvisioningException("SHA-256 mismatch for " + uri);
			}

			try {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}
			return destination;
		} catch (IOException e) {
			throw new ProvisioningException("Could not cache " + uri, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted while downloading " + uri, e);
		}
	}

	/**
	 * Reads a small UTF-8 HTTP resource without caching it.
	 *
	 * @param uri resource location
	 * @return response body
	 */
	@Override
	public @NotNull String read(@NotNull URI uri) {
		try {
			HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300)
				throw new ProvisioningException("Request failed with HTTP " + response.statusCode() + ": " + uri);

			return response.body();
		} catch (IOException e) {
			throw new ProvisioningException("Could not read " + uri, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted while reading " + uri, e);
		}
	}

	private boolean matches(Path file, @Nullable String expectedSha256) throws IOException {
		if (expectedSha256 == null || expectedSha256.isBlank())
			return true;

		try (InputStream stream = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			int count;
			while ((count = stream.read(buffer)) >= 0)
				digest.update(buffer, 0, count);

			return expectedSha256.equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
}
