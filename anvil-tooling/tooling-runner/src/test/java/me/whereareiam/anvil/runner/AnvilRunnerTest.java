package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.runner.model.AnvilRunnerConfiguration;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnvilRunnerTest {
	@Test
	void listsScenariosThroughInjectedOutput() throws Exception {
		StringWriter buffer = new StringWriter();

		new AnvilRunner(new StringReader(""), new PrintWriter(buffer))
				.run(new String[]{"--provider=" + TestProvider.class.getName(), "--list"});

		String output = buffer.toString();
		assertTrue(output.contains("demo (manual)"), output);
		assertTrue(output.contains("default -> [demo]"), output);
	}

	@Test
	void mapsExplicitConfigurationWithoutSystemProperties() {
		Path cache = Path.of("cache");
		Path work = Path.of("work");
		Path java = Path.of("java-21");
		Path artifact = Path.of("server.jar");

		AnvilRunnerConfiguration configuration = AnvilRunnerConfiguration.builder()
				.eulaAccepted(true)
				.cacheDirectory(cache)
				.workDirectory(work)
				.protocolId("mcprotocol")
				.javaExecutable(21, java)
				.artifact("server", artifact)
				.build();

		assertTrue(configuration.isEulaAccepted());
		assertEquals(cache, configuration.getCacheDirectory());
		assertEquals(work, configuration.getWorkDirectory());
		assertEquals("mcprotocol", configuration.getProtocolId());
		assertEquals(java, configuration.getJavaExecutables().get(21));
		assertEquals(artifact, configuration.getArtifacts().get("server"));
	}

	@Test
	void rejectsUnknownOptionsBeforeLoadingAProvider() {
		String provider = "--provider=" + TestProvider.class.getName();

		assertThrows(
				IllegalArgumentException.class,
				() -> new AnvilRunner(new StringReader(""), new PrintWriter(new StringWriter()))
						.run(new String[]{"--unknown", provider, "--list"})
		);
	}

	public static final class TestProvider implements AnvilScenarioProvider {
		@Override
		public void register(ScenarioRegistry registry) {
			registry.scenario(AnvilScenario.builder()
					.name("demo")
					.entrypoint("server")
					.manual(true)
					.build());
			registry.group(ScenarioGroup.builder()
					.name("default")
					.scenario("demo")
					.build());
		}
	}
}
