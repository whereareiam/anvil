package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnvilRunnerTest {
	@Test
	void listsScenariosThroughInjectedOutput() throws Exception {
		StringWriter buffer = new StringWriter();

		new AnvilRunner(new StringReader(""), new PrintWriter(new BufferedWriter(buffer)))
				.run(new String[]{"--provider=" + TestProvider.class.getName(), "--list"});

		String output = buffer.toString();
		assertTrue(output.contains("demo (manual)"), output);
		assertTrue(output.contains("default -> [demo]"), output);
	}

	@Test
	void acceptsCompleteExplicitOptionsWithoutSystemProperties() throws Exception {
		Path cache = Path.of("cache");
		Path work = Path.of("work");
		Path java = Path.of("java-21");
		Path artifact = Path.of("server.jar");

		EngineOptions configuration = EngineOptions.builder()
				.eulaAccepted(true)
				.cacheDirectory(cache)
				.workDirectory(work)
				.protocolId("mcprotocol")
				.keepFailedWorkspaces(false)
				.downloadJava(false)
				.stopTimeout(Duration.ofSeconds(7))
				.javaRequirement(JavaRequirement.builder()
						.featureVersion(21)
						.build()
				)
				.artifact("server", artifact)
				.build();

		assertTrue(configuration.isEulaAccepted());
		assertEquals(cache, configuration.getCacheDirectory());
		assertEquals(work, configuration.getWorkDirectory());
		assertEquals("mcprotocol", configuration.getProtocolId());
		assertEquals(21, configuration.getJavaRequirement().getFeatureVersion());
		assertEquals(artifact, configuration.getArtifacts().get("server"));
		StringWriter output = new StringWriter();
		new AnvilRunner(new StringReader(""), new PrintWriter(output))
				.run(new String[]{"--provider=" + TestProvider.class.getName(), "--list"}, configuration);
		assertTrue(output.toString().contains("demo (manual)"));
		assertEquals(Duration.ofSeconds(7), configuration.getStopTimeout());
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
