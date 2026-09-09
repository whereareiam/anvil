package me.whereareiam.anvil.environment.provisioning.java.archive;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs a verified JDK archive into an atomic, private directory.
 * Archive traversal and link targets remain confined to the staging root.
 */
public final class JavaArchiveInstaller {
	public void install(Path archive, Path destination) throws IOException {
		Files.createDirectories(destination.getParent());
		Path temporary = Files.createTempDirectory(destination.getParent(), "jdk-");
		try {
			if (archive.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
				zip(archive, temporary);
			}
			else {
				tar(archive, temporary);
			}

			if (Files.exists(destination)) deleteTree(destination);
			try {
				Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			try (var paths = Files.walk(temporary)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			} catch (NoSuchFileException ignored) {
				// A successful publication moved the staging directory.
			}
		}
	}

	private void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private void zip(Path archive, Path root) throws IOException {
		try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				Path target = target(root, entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}

				Files.createDirectories(target.getParent());
				Files.copy(zip, target);
			}
		}
	}

	private void tar(Path archive, Path root) throws IOException {
		List<Link> links = new ArrayList<>();
		try (var tar = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
			TarArchiveEntry entry;
			while ((entry = tar.getNextEntry()) != null) {
				Path target = target(root, entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}

				if (entry.isSymbolicLink() || entry.isLink()) {
					Path source = entry.isSymbolicLink()
							? target.getParent().resolve(entry.getLinkName()).normalize()
							: target(root, entry.getLinkName());

					if (!source.startsWith(root)) throw new IOException("JDK archive link escapes its root: " + entry.getName());
					links.add(new Link(target, source, entry.isSymbolicLink()));

					continue;
				}

				if (!entry.isFile()) throw new IOException("Unsupported JDK archive entry: " + entry.getName());

				Files.createDirectories(target.getParent());
				Files.copy(tar, target);
				if ((entry.getMode() & 73) != 0 && !target.toFile().setExecutable(true, false))
					throw new IOException("Could not make JDK file executable: " + target);
			}
		}

		for (Link link : links) {
			Files.createDirectories(link.path().getParent());
			if (link.symbolic()) {
				Files.createSymbolicLink(link.path(), link.path().getParent().relativize(link.target()));
				continue;
			}

			Files.createLink(link.path(), link.target());
		}
	}

	private Path target(Path root, String name) throws IOException {
		Path result = root.resolve(name).normalize();
		if (!result.startsWith(root)) throw new IOException("JDK archive entry escapes its root: " + name);

		return result;
	}

	private record Link(Path path, Path target, boolean symbolic) { }
}
