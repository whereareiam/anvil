package me.whereareiam.anvil.environment.provisioning.java.archive;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaArchiveInstallerTest {
	@TempDir
	Path directory;

	@Test
	void installsEverySymbolicLinkInATarArchive() throws Exception {
		Path archive = directory.resolve("jdk.tar.gz");
		try (OutputStream output = Files.newOutputStream(archive);
			 GZIPOutputStream gzip = new GZIPOutputStream(output);
			 TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
			tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			file(tar, "jdk/bin/java", "java");
			symlink(tar, "jdk/bin/java-current", "java");
			symlink(tar, "jdk/current", "bin");
		}

		Path destination = directory.resolve("installed");
		new JavaArchiveInstaller().install(archive, destination);

		Path executable = destination.resolve("jdk/bin/java");
		Path current = destination.resolve("jdk/bin/java-current");
		Path home = destination.resolve("jdk/current");
		assertEquals("java", Files.readSymbolicLink(current).toString());
		assertEquals("bin", Files.readSymbolicLink(home).toString());
		assertTrue(Files.isRegularFile(executable));
	}

	private void file(TarArchiveOutputStream tar, String name, String content) throws Exception {
		byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
		TarArchiveEntry entry = new TarArchiveEntry(name);
		entry.setSize(bytes.length);
		tar.putArchiveEntry(entry);
		tar.write(bytes);
		tar.closeArchiveEntry();
	}

	private void symlink(TarArchiveOutputStream tar, String name, String target) throws Exception {
		TarArchiveEntry entry = new TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK);
		entry.setLinkName(target);
		tar.putArchiveEntry(entry);
		tar.closeArchiveEntry();
	}
}
