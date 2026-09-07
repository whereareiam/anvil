package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioRegistryTest {
	@Test
	void preservesOrderAndRejectsDuplicateNames() {
		ScenarioRegistry registry = new ScenarioRegistry();
		AnvilScenario scenario = scenario("first");

		registry.scenario(scenario);

		assertEquals(scenario, registry.requireScenario("first"));
		assertThrows(IllegalArgumentException.class, () -> registry.scenario(scenario));
		assertThrows(NoSuchElementException.class, () -> registry.requireScenario("missing"));
	}

	@Test
	void registersTypedScenarioDefinitions() {
		ScenarioRegistry registry = new ScenarioRegistry();
		AnvilScenario scenario = scenario("typed");

		registry.scenario(() -> scenario);

		assertEquals(scenario, registry.requireScenario("typed"));
	}

	@Test
	void scenariosCanSelectFreshOrPersistentWorkspaces() {
		AnvilScenario fresh = scenario("fresh");
		AnvilScenario persistent = fresh.toBuilder()
				.clearServers()
				.server(fresh.getServers().getFirst().toBuilder()
						.workspace(WorkspacePlan.builder()
								.mode(WorkspaceMode.PERSISTENT)
								.build())
						.build())
				.build();

		assertEquals(WorkspaceMode.FRESH, fresh.getServers().getFirst().getWorkspace().getMode());
		assertEquals(WorkspaceMode.PERSISTENT, persistent.getServers().getFirst().getWorkspace().getMode());
	}

	@Test
	void carriesJavaSourcesAtScenarioAndProcessScope() {
		JavaSource scenarioSource = JavaSource.home(Path.of("scenario-jdk"));
		JavaSource processSource = JavaSource.executable(Path.of("process-java"));
		MinecraftServer server = scenario("sources").getServers().getFirst().toBuilder()
				.javaSource(processSource).build();
		AnvilScenario configured = AnvilScenario.builder()
				.name("sources")
				.entrypoint(server.getName())
				.javaSource(scenarioSource)
				.server(server)
				.build();

		assertEquals(scenarioSource, configured.getJavaSource());
		assertEquals(processSource, configured.getServers().getFirst().getJavaSource());
	}

	private AnvilScenario scenario(String name) {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.build();
		return AnvilScenario.builder().name(name).entrypoint(server.getName()).server(server).build();
	}
}
