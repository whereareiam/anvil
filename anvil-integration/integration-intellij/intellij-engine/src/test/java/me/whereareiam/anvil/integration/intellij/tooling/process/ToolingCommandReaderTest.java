package me.whereareiam.anvil.integration.intellij.tooling.process;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolingCommandReaderTest {
	@TempDir Path directory;

	@Test
	void buildsArgumentVectorWithoutShellExpansionOrClasspathGuessing() throws Exception {
		Path manifest = directory.resolve("tooling.json");
		new ObjectMapper()
				.writeValue(
						manifest.toFile(),
						Map.of(
								"schemaVersion", 1,
								"toolingJavaExecutable", "/jdks/java 21/bin/java",
								"classpath", List.of("/libs/project classes", "/libs/dependency.jar"),
								"properties", Map.of("anvil.workspace", "/project/$(do-not-expand)"),
								"definitions", List.of("example.AuthenticationScenario")));

		assertEquals(
				List.of(
						"/jdks/java 21/bin/java",
						"-Danvil.workspace=/project/$(do-not-expand)",
						"-cp",
						"/libs/project classes" + File.pathSeparator + "/libs/dependency.jar",
						"me.whereareiam.anvil.tooling.launcher.AnvilTooling",
						"example.AuthenticationScenario"),
				ToolingCommandReader.command(manifest));
	}

	@Test
	void rejectsUnknownSchemaBeforeStartingProjectCode() throws Exception {
		Path manifest = directory.resolve("tooling.json");
		Files.writeString(manifest, "{\"schemaVersion\":3}");
		assertThrows(IOException.class, () -> ToolingCommandReader.command(manifest));
	}

	@Test
	void rejectsMissingRuntimeClasspath() throws Exception {
		Path manifest = directory.resolve("tooling.json");
		Files.writeString(
				manifest,
				"{\"schemaVersion\":1,\"toolingJavaExecutable\":\"java\",\"classpath\":[],\"definitions\":[]}");
		assertThrows(IOException.class, () -> ToolingCommandReader.command(manifest));
	}
}
