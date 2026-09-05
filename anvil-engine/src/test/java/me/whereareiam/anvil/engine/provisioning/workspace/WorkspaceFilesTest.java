package me.whereareiam.anvil.engine.provisioning.workspace;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceFilesTest {
	@TempDir
	Path temporary;

	@Test
	void overlaysInDeclarationOrderAndConfinesRecreation() throws Exception {
		Path first = Files.createDirectory(temporary.resolve("first"));
		Path second = Files.createDirectory(temporary.resolve("second"));
		Files.writeString(first.resolve("config.txt"), "first");
		Files.writeString(second.resolve("config.txt"), "second");
		Path root = Files.createDirectory(temporary.resolve("runs"));
		Path run = root.resolve("scenario/run");

		WorkspaceFiles files = new WorkspaceFiles();
		files.recreate(root, run);
		files.overlay(first, run);
		files.overlay(second, run);

		assertEquals("second", Files.readString(run.resolve("config.txt")));
		assertThrows(ScenarioValidationException.class, () -> files.recreate(root, root));
		assertThrows(ScenarioValidationException.class, () -> files.recreate(root, temporary.resolve("outside")));
	}
}
