package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioRepositoryTest {
	@Test
	void evaluatesDirectDefinitionsAndRetainsScenarioMetadata() throws Exception {
		ScenarioRepository repository = ScenarioRepository.load(List.of(RegistrationScenario.class.getName()));

		ScenarioDescriptor descriptor = repository.scenarios().getFirst();
		assertEquals(RegistrationScenario.class.getName(), descriptor.getDefinition());
		assertEquals("registration", descriptor.getName());
		assertEquals("Player registration", descriptor.getDisplayName());
		assertEquals(List.of("auth"), descriptor.getTags());
	}

	@Test
	void readsGeneratedIndexWithoutEvaluatingProvidersOrGroups() throws Exception {
		Path root = Files.createTempDirectory("anvil-definition-index");
		Path resource = root.resolve(AnvilScenarioDefinition.INDEX_RESOURCE);
		Files.createDirectories(resource.getParent());
		Files.writeString(resource, "# generated\n" + RegistrationScenario.class.getName() + "\n", StandardCharsets.UTF_8);
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, previous)) {
			Thread.currentThread().setContextClassLoader(loader);
			assertTrue(ScenarioRepository.discover().entries().stream()
					.anyMatch(entry -> entry.definition().equals(RegistrationScenario.class.getName())));
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
			Files.deleteIfExists(resource);
			Files.deleteIfExists(resource.getParent());
			Files.deleteIfExists(resource.getParent().getParent());
			Files.deleteIfExists(root);
		}
	}

	@Test
	void reportsAmbiguousScenarioNamesWithDefinitionIdentities() throws Exception {
		ScenarioRepository repository = ScenarioRepository.load(List.of(RegistrationScenario.class.getName(), DuplicateRegistrationScenario.class.getName()));

		assertThrows(IllegalArgumentException.class, () -> repository.require("registration"));
	}

	public static final class RegistrationScenario implements AnvilScenarioDefinition {
		@Override
		public @NotNull AnvilScenario define() {
			return AnvilScenario.builder().name("registration").entrypoint("server")
					.metadata(PresentationMetadata.builder().displayName("Player registration").tag("auth").build()).build();
		}
	}

	public static final class DuplicateRegistrationScenario implements AnvilScenarioDefinition {
		@Override
		public @NotNull AnvilScenario define() {
			return AnvilScenario.builder().name("registration").entrypoint("server").build();
		}
	}
}
