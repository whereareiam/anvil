package me.whereareiam.anvil.workbench.remote;

import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.protocol.WireLines;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Hands files of the engine's machine to the workbench by the SHA-256 of their content, so content the workbench
 * holds already, such as a plugin JAR that did not change since the last run, is never sent again. A directory goes
 * as a zip whose entries are sorted and undated, so equal directories hash alike.
 */
final class Uploads {
	private final @NotNull WorkbenchConnection connection;
	private final Map<Key, Path> stored = new ConcurrentHashMap<>();

	Uploads(@NotNull WorkbenchConnection connection) {
		this.connection = connection;
	}

	/**
	 * Returns where the workbench keeps a file or directory, uploading it when the workbench lacks it.
	 *
	 * @param file existing file or directory on the engine's machine
	 * @return its path on the workbench
	 */
	@NotNull Path store(@NotNull Path file) {
		Path absolute = file.toAbsolutePath().normalize();
		try {
			if (!Files.exists(absolute)) throw new IOException("Nothing exists at " + absolute);

			boolean directory = Files.isDirectory(absolute);
			Key key = new Key(absolute, directory ? -1 : Files.size(absolute), Files.getLastModifiedTime(absolute));
			Path known = directory ? null : stored.get(key);
			if (known != null) return known;

			Path content = directory ? zip(absolute) : absolute;
			try {
				String sha256 = sha256(content);
				String name = absolute.getFileName().toString();
				var found = connection.call("store.find", connection.mapper().createObjectNode().put("sha256", sha256).put("name", name));
				Path path = found.isNull() ? upload(content, sha256, name, directory) : Path.of(found.asText());
				if (!directory) stored.put(key, path);
				return path;
			} finally {
				if (directory) Files.deleteIfExists(content);
			}
		} catch (IOException failure) {
			throw new WorkbenchException("Could not hand " + absolute + " to workbench '" + connection.pairing().getName() + "': "
					+ failure.getMessage(), failure);
		}
	}

	private Path upload(Path content, String sha256, String name, boolean directory) throws IOException {
		try (SSLSocket socket = connection.stream(WireProtocol.UPLOAD, connection.mapper().createObjectNode()
				.put("sha256", sha256).put("name", name).put("directory", directory).put("size", Files.size(content)))) {
			OutputStream output = socket.getOutputStream();
			Files.copy(content, output);
			output.flush();

			var answer = WireLines.read(connection.mapper(), socket.getInputStream());
			if (answer == null || !answer.has("path"))
				throw new IOException(answer == null ? "the workbench closed the upload" : answer.path("error").asText());
			return Path.of(answer.path("path").asText());
		}
	}

	private static Path zip(Path directory) throws IOException {
		Path zip = Files.createTempFile("anvil-upload-", ".zip");
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip)); Stream<Path> tree = Files.walk(directory)) {
			List<Path> files = tree.filter(Files::isRegularFile).sorted().toList();
			for (Path file : files) {
				ZipEntry entry = new ZipEntry(directory.relativize(file).toString().replace('\\', '/'));
				entry.setTime(0);
				output.putNextEntry(entry);
				Files.copy(file, output);
				output.closeEntry();
			}
		}
		return zip;
	}

	private static String sha256(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
				input.transferTo(OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is not available", failure);
		}
	}

	private record Key(Path path, long size, FileTime modified) {
	}
}
