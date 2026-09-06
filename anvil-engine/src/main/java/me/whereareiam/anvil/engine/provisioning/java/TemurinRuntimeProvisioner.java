package me.whereareiam.anvil.engine.provisioning.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.engine.provisioning.artifact.DownloadCache;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and caches a checksum-verified Temurin JDK when no suitable local runtime exists.
 */
@RequiredArgsConstructor
public final class TemurinRuntimeProvisioner {
	private final ObjectMapper mapper = new ObjectMapper();
	private final @NotNull DownloadCache downloads;

	/**
	 * Resolves the newest GA Temurin runtime for a Java feature version and caches that exact package by checksum.
	 *
	 * @param featureVersion required Java feature version
	 * @param cacheDirectory Anvil cache root
	 * @return executable inside the cached JDK
	 */
	public synchronized @NotNull Path resolve(int featureVersion, @NotNull Path cacheDirectory) {
		String os = JavaExecutables.temurinOperatingSystem(System.getProperty("os.name", ""));
		String architecture = architecture();
		URI metadata = URI.create("https://api.adoptium.net/v3/assets/latest/" + featureVersion
				+ "/hotspot?architecture=" + architecture + "&image_type=jdk&os=" + os + "&vendor=eclipse");
		try {
			JsonNode assets = mapper.readTree(downloads.read(metadata));
			if (!assets.isArray() || assets.isEmpty())
				throw new ProvisioningException("No Temurin Java " + featureVersion + " runtime is available for "
						+ os + "/" + architecture);

			JsonNode packageInfo = assets.get(0).path("binary").path("package");
			String checksum = required(packageInfo, "checksum", featureVersion);
			String name = required(packageInfo, "name", featureVersion);
			URI download = URI.create(required(packageInfo, "link", featureVersion));
			Path root = cacheDirectory.resolve("jdks/temurin").resolve(Integer.toString(featureVersion))
					.resolve(checksum.substring(0, Math.min(16, checksum.length())));
			Path existing = findJava(root);
			if (existing != null)
				return existing;

			Path archive = downloads.obtain(download, root.resolveSibling(name), checksum);
			Path temporary = root.resolveSibling(root.getFileName() + ".part-" + UUID.randomUUID());
			Files.createDirectories(temporary);
			try {
				if (name.endsWith(".zip"))
					extractZip(archive, temporary);
				else
					extractTar(archive, temporary);

				Path extracted = findJava(temporary);
				if (extracted == null)
					throw new ProvisioningException("Temurin archive contains no Java executable: " + archive);
				Path relative = temporary.relativize(extracted);
				Files.createDirectories(root.getParent());
				moveDirectory(temporary, root);
				return root.resolve(relative);
			} finally {
				deleteTree(temporary);
			}
		} catch (IOException e) {
			throw new ProvisioningException("Could not provision Java " + featureVersion, e);
		}
	}

	private void extractZip(Path archive, Path destination) throws IOException {
		try (InputStream input = Files.newInputStream(archive);
			 ZipInputStream zip = new ZipInputStream(input)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				Path target = destination.resolve(entry.getName()).normalize();
				if (!target.startsWith(destination))
					throw new ProvisioningException("Unsafe path in Java archive: " + entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				Files.createDirectories(target.getParent());
				Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private void extractTar(Path archive, Path destination) {
		Path log = destination.resolve("extract.log");
		try {
			Process process = new ProcessBuilder(
					"tar", "-xzf", archive.toAbsolutePath().toString(), "-C", destination.toAbsolutePath().toString()
			).redirectErrorStream(true).redirectOutput(log.toFile()).start();
			if (!process.waitFor(Duration.ofMinutes(5).toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				throw new ProvisioningException("Timed out extracting Java archive; inspect " + log);
			}
			if (process.exitValue() != 0)
				throw new ProvisioningException("Could not extract Java archive; inspect " + log);
		} catch (IOException e) {
			throw new ProvisioningException("The system 'tar' command is required to provision Java on this platform", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted while extracting Java archive", e);
		}
	}

	private Path findJava(Path root) throws IOException {
		if (!Files.isDirectory(root))
			return null;
		String executable = JavaExecutables.executableName(System.getProperty("os.name", ""));
		try (var paths = Files.find(root, 8, (path, attributes) ->
				attributes.isRegularFile()
						&& path.getFileName().toString().equals(executable)
						&& path.getParent() != null
						&& path.getParent().getFileName().toString().equals("bin"))) {
			return paths.findFirst().orElse(null);
		}
	}

	private void moveDirectory(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, destination);
		}
	}

	private void deleteTree(Path root) throws IOException {
		if (!Files.exists(root))
			return;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
				Files.deleteIfExists(path);
		}
	}

	private String required(JsonNode node, String field, int version) {
		String value = node.path(field).asText();
		if (value.isBlank())
			throw new ProvisioningException("Temurin metadata for Java " + version + " has no " + field);
		return value;
	}

	private String architecture() {
		String value = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (value.equals("amd64") || value.equals("x86_64"))
			return "x64";
		if (value.equals("aarch64") || value.equals("arm64"))
			return "aarch64";
		throw new ProvisioningException("Automatic Java provisioning does not support this architecture: " + value);
	}
}
