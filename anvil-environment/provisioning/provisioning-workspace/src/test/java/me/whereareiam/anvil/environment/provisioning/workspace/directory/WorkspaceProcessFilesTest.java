package me.whereareiam.anvil.environment.provisioning.workspace.directory;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceProcessFilesTest {
	@TempDir
	Path temporary;

	@Test
	void writesReadsAndListsTextBelowTheWorkspace() throws Exception {
		var files = new WorkspaceProcessFiles(temporary.resolve("server"), new WorkspaceFiles());

		files.write("config/paper-global.yml", "proxies: {}\n");
		files.write("server.properties", "motd=Anvil\n");

		assertEquals("proxies: {}\n", files.read("config/paper-global.yml"));
		assertTrue(files.exists("config"));
		assertEquals(List.of("config", "server.properties"), files.list(""));
		assertEquals(List.of("paper-global.yml"), files.list("config"));
		assertEquals(temporary.resolve("server").toString(), files.location());
	}

	@Test
	void deletesFilesAndDirectoriesAndReportsWhetherAnythingExisted() throws Exception {
		var files = new WorkspaceProcessFiles(temporary, new WorkspaceFiles());
		files.write("plugins/Example/config.yml", "enabled: true\n");

		assertTrue(files.delete("plugins"));
		assertFalse(files.exists("plugins"));
		assertFalse(files.delete("plugins"));
	}

	@Test
	void reportsAMissingFileAsSuch() {
		var files = new WorkspaceProcessFiles(temporary, new WorkspaceFiles());

		assertThrows(NoSuchFileException.class, () -> files.read("server.properties"));
	}

	@Test
	void refusesPathsThatLeaveTheWorkspace() throws Exception {
		var files = new WorkspaceProcessFiles(temporary.resolve("server"), new WorkspaceFiles());
		Files.createDirectories(temporary.resolve("server"));
		Files.createSymbolicLink(temporary.resolve("server/outside"), temporary);

		assertThrows(ScenarioValidationException.class, () -> files.write("../escaped", "no"));
		assertThrows(ScenarioValidationException.class, () -> files.read(temporary.resolve("other").toString()));
		assertThrows(ScenarioValidationException.class, () -> files.write("outside/escaped", "no"));
		assertFalse(Files.exists(temporary.resolve("escaped")));
	}
}
