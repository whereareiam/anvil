package me.whereareiam.anvil.tooling.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioDefinitionScannerTest {
	private static final String SCENARIO = """
			public me.whereareiam.anvil.api.model.scenario.AnvilScenario define() {
				return me.whereareiam.anvil.api.model.scenario.AnvilScenario.builder().name("x").entrypoint("s").build();
			}
			""";

	@TempDir
	Path directory;

	@Test
	void findsPublicConcreteDefinitionsThroughSuperclassesAndSubInterfaces() throws IOException {
		Path classes = compile(Map.of(
				"example/Direct.java", "package example; public final class Direct implements "
						+ "me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition { " + SCENARIO + " }",
				"example/Base.java", "package example; public abstract class Base implements "
						+ "me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition { }",
				"example/Inherited.java", "package example; public final class Inherited extends Base { " + SCENARIO + " }",
				"example/Family.java", "package example; public interface Family extends "
						+ "me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition { }",
				"example/Member.java", "package example; public class Member implements Family { " + SCENARIO + " }",
				"example/Hidden.java", "package example; final class Hidden implements "
						+ "me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition { " + SCENARIO + " }",
				"example/Unrelated.java", "package example; public final class Unrelated { }"
		));

		List<String> definitions = ScenarioDefinitionScanner.scan(List.of(classes.toFile()), List.of());

		assertEquals(List.of("example.Direct", "example.Inherited", "example.Member"), definitions);
	}

	@Test
	void mergesDefinitionsRegisteredInTheResourcesServiceFile() throws IOException {
		Path classes = compile(Map.of(
				"example/Direct.java", "package example; public final class Direct implements "
						+ "me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition { " + SCENARIO + " }"
		));
		Path resources = directory.resolve("resources");
		Path file = resources.resolve(ScenarioDefinitionScanner.SERVICE_FILE);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "# registered by hand\nexample.Registered # kept\n\nexample.Direct\n");

		List<String> definitions = ScenarioDefinitionScanner.scan(List.of(classes.toFile()), List.of(resources.toFile()));

		assertEquals(List.of("example.Direct", "example.Registered"), definitions);
	}

	@Test
	void writesTheDefinitionsAsTheirStandardServiceFile() throws IOException {
		Path output = directory.resolve("index");

		ScenarioDefinitionScanner.writeServiceFile(output, List.of("example.Direct", "example.Inherited"));
		ScenarioDefinitionScanner.writeServiceFile(output, List.of("example.Direct"));

		assertEquals("META-INF/services/me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition",
				ScenarioDefinitionScanner.SERVICE_FILE);
		assertEquals("example.Direct\n", Files.readString(output.resolve(ScenarioDefinitionScanner.SERVICE_FILE)));
	}

	@Test
	void reportsAnUnreadableClassFileInsteadOfSkippingIt() throws IOException {
		Path classes = Files.createDirectories(directory.resolve("broken"));
		Files.writeString(classes.resolve("Broken.class"), "not a class");

		var failure = assertThrows(IOException.class,
				() -> ScenarioDefinitionScanner.scan(List.of(classes.toFile()), List.of()));
		assertTrue(failure.getMessage().contains("Broken.class"), failure.getMessage());
	}

	private Path compile(Map<String, String> sources) throws IOException {
		Path sourceRoot = directory.resolve("sources");
		Path output = Files.createDirectories(directory.resolve("classes"));
		List<String> arguments = new ArrayList<>(List.of(
				"-d", output.toString(),
				"-cp", System.getProperty("java.class.path")
		));
		for (var source : sources.entrySet()) {
			Path file = sourceRoot.resolve(source.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue());
			arguments.add(file.toString());
		}

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)), "Test sources must compile");

		return output;
	}
}
