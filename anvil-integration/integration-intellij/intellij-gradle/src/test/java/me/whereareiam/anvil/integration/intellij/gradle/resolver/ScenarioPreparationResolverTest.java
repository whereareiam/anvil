package me.whereareiam.anvil.integration.intellij.gradle.resolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScenarioPreparationResolverTest {
	private final ScenarioPreparationResolver resolver = new ScenarioPreparationResolver();
	@TempDir Path directory;

	@Test
	void preservesWindowsArgumentBoundariesAndManifestOwnership() throws IOException {
		Path root = Files.createDirectory(directory.resolve("build with spaces"));
		Path wrapper = Files.createFile(root.resolve("gradlew.bat"));
		var preparation = resolver.resolve(root, ":plugin:anvilTooling", true, ScenarioPreparationResolver.Settings.DEFAULT);
		try {
			assertTrue(Files.isRegularFile(preparation.getManifestPath()));
			assertEquals(root, preparation.getWorkingDirectory());
			assertEquals(List.of("cmd.exe", "/d", "/c", wrapper.toString(), ":plugin:anvilTooling",
					"--output-file", preparation.getManifestPath().toString(), "--console=plain"), preparation.getCommand());
		} finally {
			Files.deleteIfExists(preparation.getManifestPath());
		}
	}

	@Test
	void rejectsMissingWrapper() {
		var failure = assertThrows(IOException.class, () -> resolver.resolve(directory, ":anvilTooling", false, ScenarioPreparationResolver.Settings.DEFAULT));
		assertTrue(failure.getMessage().contains("wrapper is missing"));
	}

	@Test
	void validatesUnixExecutableBeforePreparing() throws IOException {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		Path wrapper = Files.createFile(directory.resolve("gradlew"));
		Files.setPosixFilePermissions(wrapper, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		var failure = assertThrows(IOException.class, () -> resolver.resolve(directory, ":anvilTooling", false, ScenarioPreparationResolver.Settings.DEFAULT));
		assertTrue(failure.getMessage().contains("not executable"));
		Files.setPosixFilePermissions(wrapper, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));

		var preparation = resolver.resolve(directory, ":anvilTooling", false, ScenarioPreparationResolver.Settings.DEFAULT);
		try {
			assertEquals(wrapper.toString(), preparation.getCommand().getFirst());
			assertEquals(":anvilTooling", preparation.getCommand().get(1));
		} finally {
			Files.deleteIfExists(preparation.getManifestPath());
		}
	}

	@Test
	void appliesTheIdeGradleJvmLocalInstallationAndOfflineMode() throws IOException {
		Path home = Files.createDirectories(directory.resolve("gradle-home/bin"));
		Path gradle = Files.createFile(home.resolve("gradle"));
		Path jdk = directory.resolve("jdk");
		var settings = new ScenarioPreparationResolver.Settings(jdk, home.getParent(), true);

		var preparation = resolver.resolve(directory, ":anvilTooling", false, settings);
		try {
			assertEquals(gradle.toString(), preparation.getCommand().getFirst());
			assertEquals("--offline", preparation.getCommand().getLast());
			assertEquals(Map.of("JAVA_HOME", jdk.toString()), preparation.getEnvironment());
		} finally {
			Files.deleteIfExists(preparation.getManifestPath());
		}
	}

	@Test
	void rejectsASelectedInstallationWithoutALauncher() {
		var settings = new ScenarioPreparationResolver.Settings(null, directory.resolve("missing"), false);

		var failure = assertThrows(IOException.class, () -> resolver.resolve(directory, ":anvilTooling", false, settings));
		assertTrue(failure.getMessage().contains("has no launcher"));
	}
}
