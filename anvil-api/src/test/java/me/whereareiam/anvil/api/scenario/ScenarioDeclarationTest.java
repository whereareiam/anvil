package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScenarioDeclarationTest {
	@Test
	void scenariosCanSelectFreshOrPersistentWorkspaces() {
		AnvilScenario fresh = scenario("fresh");
		AnvilScenario persistent = fresh.toBuilder()
				.clearServers()
				.server(fresh.servers().getFirst().toBuilder()
						.workspace(WorkspacePlan.builder()
								.mode(WorkspaceMode.PERSISTENT)
								.build())
						.build())
				.build();

		assertEquals(WorkspaceMode.FRESH, fresh.servers().getFirst().workspace().mode());
		assertEquals(WorkspaceMode.PERSISTENT, persistent.servers().getFirst().workspace().mode());
	}

	@Test
	void carriesJavaSourcesAtScenarioAndProcessScope() {
		JavaSource scenarioSource = JavaSource.home(Path.of("scenario-jdk"));
		JavaSource processSource = JavaSource.executable(Path.of("process-java"));
		MinecraftServer server = scenario("sources").servers().getFirst().toBuilder()
				.javaSelection(JavaSelection.builder().source(processSource).build()).build();
		AnvilScenario configured = AnvilScenario.builder()
				.name("sources")
				.entrypoint(server.name())
				.javaSelection(JavaSelection.builder().source(scenarioSource).build())
				.server(server)
				.build();

		assertEquals(scenarioSource, configured.javaSelection().source());
		assertEquals(processSource, configured.servers().getFirst().javaSelection().source());
	}

	private AnvilScenario scenario(String name) {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.build();
		return AnvilScenario.builder().name(name).entrypoint(server.name()).server(server).build();
	}
}
