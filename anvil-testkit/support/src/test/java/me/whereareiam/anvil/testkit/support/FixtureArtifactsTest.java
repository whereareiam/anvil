package me.whereareiam.anvil.testkit.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class FixtureArtifactsTest {
	private static final String PROCESS_PROPERTY = "anvil.testkit.fixture.process";

	@TempDir
	Path directory;

	@Test
	void runsThePreparedProcessJarAndRecordsItsShutdown() throws Exception {
		Path marker = directory.resolve("stopped.txt");
		Path java = Path.of(System.getProperty("java.home"), "bin", "java");
		Process process = new ProcessBuilder(java.toString(), "-jar", FixtureArtifacts.process().toString(),
				marker.toString(), "fixture").redirectErrorStream(true).start();
		try {
			process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
			process.getOutputStream().flush();
			assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Fixture process did not stop");
			assertEquals(0, process.exitValue());
			assertEquals("READY", new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip());
			assertEquals("fixture\n", Files.readString(marker));
		} finally {
			process.destroyForcibly();
		}
	}

	@Test
	void reportsMissingArtifactConfigurationWithoutSearchingTheFilesystem() {
		String configured = System.clearProperty(PROCESS_PROPERTY);
		try {
			var failure = assertThrows(IllegalStateException.class, FixtureArtifacts::process);
			assertTrue(failure.getMessage().contains(PROCESS_PROPERTY));
		} finally {
			restore(configured);
		}
	}

	@Test
	void rejectsSuppliedDirectoriesAndMissingFiles() {
		String configured = System.getProperty(PROCESS_PROPERTY);
		try {
			for (Path invalid : new Path[] {directory, directory.resolve("missing.jar")}) {
				System.setProperty(PROCESS_PROPERTY, invalid.toString());
				var failure = assertThrows(IllegalStateException.class, FixtureArtifacts::process);
				assertTrue(failure.getMessage().contains(invalid.toString()));
			}
		} finally {
			restore(configured);
		}
	}

	private void restore(String configured) {
		if (configured == null) System.clearProperty(PROCESS_PROPERTY);
		else System.setProperty(PROCESS_PROPERTY, configured);
	}
}
