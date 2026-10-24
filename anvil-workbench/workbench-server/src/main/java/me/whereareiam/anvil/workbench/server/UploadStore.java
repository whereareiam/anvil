package me.whereareiam.anvil.workbench.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Keeps the files engines store on the workbench by the SHA-256 of their content, so a file uploaded once, such as a
 * plugin JAR that did not change, is never uploaded again. A directory arrives zipped and is kept unpacked.
 */
final class UploadStore {
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private final @NotNull Path root;

	UploadStore(@NotNull Path root) {
		this.root = root;
	}

	/**
	 * Returns where the workbench keeps content it already holds.
	 *
	 * @param sha256 content hash
	 * @param name file or directory name the engine gave it
	 * @return path of the content, or null when the workbench does not hold it
	 */
	@Nullable Path find(@NotNull String sha256, @NotNull String name) {
		Path target = target(sha256, name);
		return Files.exists(target) ? target : null;
	}

	/**
	 * Receives content, verifies its hash and keeps it.
	 *
	 * @param sha256 hash the engine announced
	 * @param name file or directory name
	 * @param directory whether the content is a zipped directory
	 * @param input the content, which ends where the upload ends
	 * @return path of the kept content
	 * @throws IOException when the content does not match its hash or cannot be written
	 */
	@NotNull Path receive(@NotNull String sha256, @NotNull String name, boolean directory, @NotNull InputStream input) throws IOException {
		Path target = target(sha256, name);
		Files.createDirectories(target.getParent());
		Path staged = Files.createTempFile(target.getParent(), "upload-", ".part");
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream content = new DigestInputStream(input, digest); OutputStream output = Files.newOutputStream(staged)) {
				content.transferTo(output);
			}
			String received = HexFormat.of().formatHex(digest.digest());
			if (!received.equals(sha256))
				throw new IOException("Upload of " + name + " arrived with SHA-256 " + received + " instead of " + sha256);

			if (Files.exists(target)) return target;
			if (directory) unzip(staged, target);
			else Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
			return target;
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is not available", failure);
		} finally {
			Files.deleteIfExists(staged);
		}
	}

	private Path target(String sha256, String name) {
		if (!SHA256.matcher(sha256).matches()) throw new IllegalArgumentException("Not a SHA-256: " + sha256);
		Path file = Path.of(name).getFileName();
		if (file == null || !file.toString().equals(name) || name.equals("..") || name.equals("."))
			throw new IllegalArgumentException("Not a plain file name: " + name);

		return root.resolve(sha256).resolve(name);
	}

	private static void unzip(Path zip, Path target) throws IOException {
		Path staged = Files.createTempDirectory(target.getParent(), "unpack-");
		try (ZipInputStream entries = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;
			while ((entry = entries.getNextEntry()) != null) {
				Path destination = staged.resolve(entry.getName()).normalize();
				if (!destination.startsWith(staged)) throw new IOException("Zip entry leaves its directory: " + entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(destination);
					continue;
				}
				Files.createDirectories(destination.getParent());
				Files.copy(entries, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
	}
}
