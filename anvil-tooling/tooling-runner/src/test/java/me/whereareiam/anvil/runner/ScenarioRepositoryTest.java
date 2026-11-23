package me.whereareiam.anvil.runner;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioRepositoryTest {
	private static final String SERVICE_FILE = "META-INF/services/" + AnvilScenarioDefinition.class.getName();

	@TempDir
	Path directory;

	@Test
	void evaluatesSelectedDefinitionsAndRetainsScenarioMetadata() throws Exception {
		ScenarioRepository repository = registered(
				() -> ScenarioRepository.load(List.of(RegistrationScenario.class.getName())),
				RegistrationScenario.class
		);

		ScenarioDescriptor descriptor = repository.scenarios().getFirst();
		assertEquals(RegistrationScenario.class.getName(), descriptor.definition());
		assertEquals("registration", descriptor.name());
		assertEquals("Player registration", descriptor.displayName());
		assertEquals(List.of("auth"), descriptor.tags());
	}

	@Test
	void discoversEveryRegisteredDefinitionInNameOrder() throws Exception {
		ScenarioRepository repository = registered(ScenarioRepository::discover,
				RegistrationScenario.class, DuplicateRegistrationScenario.class);

		List<String> definitions = repository.entries().stream().map(ScenarioRepository.Entry::definition).toList();
		assertTrue(definitions.contains(RegistrationScenario.class.getName()), definitions.toString());
		assertTrue(definitions.contains(DuplicateRegistrationScenario.class.getName()), definitions.toString());
		assertEquals(definitions.stream().sorted().toList(), definitions);
	}

	@Test
	void createsOnlyTheSelectedDefinitions() throws Exception {
		ScenarioRepository repository = registered(
				() -> ScenarioRepository.load(List.of(RegistrationScenario.class.getName())),
				RegistrationScenario.class, FailingScenario.class
		);

		assertEquals(List.of(RegistrationScenario.class.getName()),
				repository.entries().stream().map(ScenarioRepository.Entry::definition).toList());
	}

	@Test
	void refusesADefinitionThatIsNotRegistered() throws Exception {
		IllegalArgumentException failure = registered(
				() -> assertThrows(IllegalArgumentException.class,
						() -> ScenarioRepository.load(List.of(DuplicateRegistrationScenario.class.getName()))),
				RegistrationScenario.class
		);

		assertTrue(failure.getMessage().startsWith("Scenario definition " + DuplicateRegistrationScenario.class.getName()
				+ " is not registered in " + SERVICE_FILE), failure.getMessage());
	}

	@Test
	void reportsADefinitionThatCannotBeCreated() throws Exception {
		IllegalStateException failure = registered(
				() -> assertThrows(IllegalStateException.class,
						() -> ScenarioRepository.load(List.of(FailingScenario.class.getName()))),
				FailingScenario.class
		);

		assertEquals("Could not create scenario definition " + FailingScenario.class.getName(), failure.getMessage());
	}

	@Test
	void reportsARegisteredClassWithoutAPublicNoArgumentConstructor() throws Exception {
		IllegalStateException failure = registered(
				() -> assertThrows(IllegalStateException.class, ScenarioRepository::discover),
				ConfiguredScenario.class
		);

		assertEquals("Could not load the scenario definitions registered in " + SERVICE_FILE, failure.getMessage());
		assertTrue(failure.getCause().getMessage().contains(ConfiguredScenario.class.getName()), failure.getCause().getMessage());
	}

	@Test
	void refusesABlankScenarioName() throws Exception {
		IllegalArgumentException failure = registered(
				() -> assertThrows(IllegalArgumentException.class, ScenarioRepository::discover),
				BlankScenario.class
		);

		assertEquals("Scenario name must not be blank: " + BlankScenario.class.getName(), failure.getMessage());
	}

	@Test
	void reportsAmbiguousScenarioNamesWithDefinitionIdentities() throws Exception {
		ScenarioRepository repository = registered(
				() -> ScenarioRepository.load(
						List.of(RegistrationScenario.class.getName(), DuplicateRegistrationScenario.class.getName())),
				RegistrationScenario.class, DuplicateRegistrationScenario.class
		);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> repository.require("registration"));
		assertEquals("Scenario name 'registration' is ambiguous; select one of: "
				+ List.of(DuplicateRegistrationScenario.class.getName(), RegistrationScenario.class.getName()), failure.getMessage());
	}

	/**
	 * Runs an action with a context class loader whose service file registers the given definitions.
	 */
	private <T> T registered(Supplier<T> action, Class<?>... definitions) throws Exception {
		Path file = directory.resolve(SERVICE_FILE);
		Files.createDirectories(file.getParent());
		Files.writeString(file, Arrays.stream(definitions).map(Class::getName).collect(Collectors.joining("\n", "", "\n")),
				StandardCharsets.UTF_8);
		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, previous)) {
			thread.setContextClassLoader(loader);
			return action.get();
		} finally {
			thread.setContextClassLoader(previous);
		}
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

	public static final class FailingScenario implements AnvilScenarioDefinition {
		public FailingScenario() {
			throw new IllegalStateException("Definitions that are not selected are never created");
		}

		@Override
		public @NotNull AnvilScenario define() {
			throw new AssertionError("unreachable");
		}
	}

	@RequiredArgsConstructor
	public static final class ConfiguredScenario implements AnvilScenarioDefinition {
		private final String name;

		@Override
		public @NotNull AnvilScenario define() {
			return AnvilScenario.builder().name(name).entrypoint("server").build();
		}
	}

	public static final class BlankScenario implements AnvilScenarioDefinition {
		@Override
		public @NotNull AnvilScenario define() {
			return AnvilScenario.builder().name(" ").entrypoint("server").build();
		}
	}
}
