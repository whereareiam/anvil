package me.whereareiam.anvil.environment.provisioning.artifact;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactAcquirer;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactEntry;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;

/**
 * Acquires verified artifacts and metadata with bounded HTTP transfers and shared cache entries.
 */
public final class HttpArtifactAcquirer implements ArtifactAcquirer {
	private final @NotNull Path root;
	private final @NotNull ArtifactStorage storage;
	private final @NotNull HttpClient client;
	private final boolean offline;
	private final boolean refresh;
	private final @NotNull Semaphore downloads;

	/**
	 * Creates an artifact resolver using coordinated artifact file storage.
	 * Closing this resolver releases its HTTP transport; borrowed storage remains available.
	 *
	 * @param root        shared directory containing acquisition metadata
	 * @param storage     exclusive artifact access and verified file publication
	 * @param offline     whether only previously cached artifacts and metadata may be used
	 * @param refresh     whether resolution metadata is downloaded again when requested
	 * @param parallelism maximum simultaneous HTTP transfers
	 * @throws IllegalArgumentException if parallelism is not positive or offline and refresh are both enabled
	 */
	public HttpArtifactAcquirer(
			@NotNull Path root,
			@NotNull ArtifactStorage storage,
			boolean offline,
			boolean refresh,
			int parallelism
	) {
		if (parallelism < 1) throw new IllegalArgumentException("Download parallelism must be positive");
		if (offline && refresh) throw new IllegalArgumentException("Offline and refresh cannot be combined");

		this.root = root.toAbsolutePath().normalize();
		this.storage = storage;
		this.offline = offline;
		this.refresh = refresh;
		this.downloads = new Semaphore(parallelism);
		this.client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
	}

	/**
	 * Returns matching cached bytes or downloads and verifies a replacement before publishing it.
	 * The previous destination remains available if downloading or checksum verification fails.
	 *
	 * @param uri            artifact source
	 * @param destination    local artifact path
	 * @param expectedSha256 expected checksum, or null when the provider permits an unpinned artifact
	 * @return normalized artifact path
	 */
	@Override
	public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String expectedSha256) {
		Path target = destination.toAbsolutePath().normalize();
		try (ArtifactEntry entry = storage.open(target)) {
			if (Files.isRegularFile(target) && matches(target, expectedSha256)) return target;
			if (offline) throw new ProvisioningException("Artifact is unavailable offline: " + uri);

			return entry.replace(staging -> {
				download(uri, staging);
				if (!matches(staging, expectedSha256))
					throw new ProvisioningException("SHA-256 mismatch for " + uri);

				return target;
			});
		} catch (IOException failure) {
			throw new ProvisioningException("Could not cache " + uri, failure);
		}
	}

	/**
	 * Reads cached UTF-8 metadata, downloading it when missing or when refresh is enabled.
	 *
	 * @param uri metadata source and cache identity
	 * @return metadata contents
	 */
	@Override
	public @NotNull String read(@NotNull URI uri) {
		try (ArtifactEntry entry = storage.open(metadata(uri))) {
			Path target = entry.path();
			if (Files.isRegularFile(target) && !refresh) return Files.readString(target);
			if (offline) throw new ProvisioningException("Resolution metadata is unavailable offline: " + uri);

			return entry.replace(staging -> {
				download(uri, staging);
				return Files.readString(staging);
			});
		} catch (IOException failure) {
			throw new ProvisioningException("Could not read resolution metadata: " + uri, failure);
		}
	}

	private boolean matches(@NotNull Path file, @Nullable String expected) throws IOException {
		if (expected == null) return true;

		MessageDigest digest = sha256();
		try (InputStream stream = Files.newInputStream(file)) {
			byte[] buffer = new byte[65536];
			int count;
			while ((count = stream.read(buffer)) >= 0)
				digest.update(buffer, 0, count);
		}

		return expected.equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()));
	}

	private Path metadata(URI uri) {
		byte[] key = sha256().digest(uri.toString().getBytes(StandardCharsets.UTF_8));
		return root.resolve("metadata").resolve(HexFormat.of().formatHex(key) + ".json");
	}

	private MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable", failure);
		}
	}

	private void download(@NotNull URI uri, @NotNull Path target) throws IOException {
		try {
			downloads.acquire();
			try {
				HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build();
				HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
				try (InputStream body = response.body()) {
					if (response.statusCode() < 200 || response.statusCode() >= 300)
						throw new ProvisioningException("Download failed with HTTP " + response.statusCode() + ": " + uri);

					Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				downloads.release();
			}
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted downloading " + uri, failure);
		}
	}

	/**
	 * Releases this resolver's HTTP transport after its operations have finished.
	 * The shared cache remains available to its other users.
	 */
	@Override
	public void close() {
		client.close();
	}
}
