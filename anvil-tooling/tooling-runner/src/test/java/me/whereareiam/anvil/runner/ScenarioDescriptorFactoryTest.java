package me.whereareiam.anvil.runner;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.runner.scenario.ScenarioDescriptorFactory;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioDescriptorFactoryTest {
	@Test
	void exportsIndependentTopologiesWithoutCreatingTheEngineOrInvokingSetup() throws Exception {
		MinecraftServer direct = server("direct-server", Distribution.local(Path.of("unresolved", "server.jar")))
				.toBuilder().minecraftVersion("26.1.2").onlineMode(true).memoryMegabytes(1536)
				.metadata(PresentationMetadata.builder().displayName("Direct backend").description("Local server under test").build())
				.build();
		AnvilScenario local = AnvilScenario.builder().name("direct-environment").entrypoint(direct.name()).server(direct).build();
		MinecraftServer lobby = server("lobby", Distribution.artifact("lobby-runtime"));
		MinecraftServer game = server("game", Distribution.pinned("1.21.11", "a".repeat(64)));
		MinecraftProxy proxy = MinecraftProxy.builder().name("proxy").platform("velocity")
				.distribution(Distribution.remote("3.5.1", "latest"))
				.server("lobby").server("game").defaultServer("lobby").build();
		AnvilScenario network = AnvilScenario.builder().name("proxy-environment")
				.metadata(PresentationMetadata.builder().displayName("Proxy investigation")
						.description("Two backends behind one proxy").category("Routing").tag("matrix").build())
				.entrypoint("proxy").server(lobby).server(game).proxy(proxy)
				.manual(true)
				.executionProviderId("local")
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofSeconds(75)).shutdown(Duration.ofSeconds(7)).build())
				.javaSelection(JavaSelection.builder()
						.requirement(JavaRequirement.builder().featureVersion(21).distribution("temurin").release("21.0.10").build())
						.build())
				.setupHook(ignored -> { throw new AssertionError("Scenario inspection must not execute setup"); }).build();
		List<ScenarioDescriptor> scenarios;
		try (RunnerSession session = new RunnerSession(() -> {
			throw new AssertionError("Scenario inspection must not construct or start an engine");
		}, ScenarioRepository.fromScenarios(List.of(local, network)))) {
			scenarios = session.scenarios();
			assertEquals("IDLE", session.snapshot().state().name());
			assertTrue(session.snapshot().processes().isEmpty());
		}

		assertEquals(List.of("direct-environment", "proxy-environment"), scenarios.stream().map(ScenarioDescriptor::name).toList());
		assertEquals(List.of("direct-server"), scenarios.getFirst().processes().stream().map(ProcessDefinition::name).toList());
		assertNull(scenarios.getFirst().executionProviderId());
		assertEquals(0, scenarios.getFirst().startupTimeoutMillis());
		assertEquals(0, scenarios.getFirst().shutdownTimeoutMillis());
		assertEquals("Project default", scenarios.getFirst().javaRequirement());
		ProcessDefinition directInfo = scenarios.getFirst().processes().getFirst();
		assertEquals(ProcessRole.SERVER, directInfo.role());
		assertEquals("Direct backend", directInfo.displayName());
		assertEquals("Local server under test", directInfo.description());
		assertEquals("Local JAR: " + Path.of("unresolved", "server.jar"), directInfo.distribution());
		assertEquals("26.1.2", directInfo.minecraftVersion());
		assertNull(directInfo.distributionVersion());
		assertTrue(directInfo.onlineMode());
		assertEquals(1536, directInfo.memoryMegabytes());
		assertTrue(directInfo.backendNames().isEmpty());

		ScenarioDescriptor topology = scenarios.get(1);
		assertEquals("Proxy investigation", topology.displayName());
		assertEquals("Two backends behind one proxy", topology.description());
		assertEquals("Routing", topology.category());
		assertEquals(List.of("matrix"), topology.tags());
		assertEquals("proxy", topology.entrypoint());
		assertEquals(75000, topology.startupTimeoutMillis());
		assertEquals(7000, topology.shutdownTimeoutMillis());
		assertEquals("local", topology.executionProviderId());
		assertTrue(topology.manual());
		assertEquals(List.of("lobby", "game", "proxy"), topology.processes().stream().map(ProcessDefinition::name).toList());
		assertEquals("Lobby", topology.processes().getFirst().displayName());
		assertEquals("Artifact: lobby-runtime", topology.processes().getFirst().distribution());
		assertEquals("1.21.11 (SHA-256 " + "a".repeat(64) + ")", topology.processes().get(1).distribution());
		ProcessDefinition route = topology.processes().getLast();
		assertEquals(ProcessRole.PROXY, route.role());
		assertEquals("velocity", route.platform());
		assertEquals("3.5.1", route.distributionVersion());
		assertEquals("latest", route.distributionBuild());
		assertEquals("3.5.1 (build latest)", route.distribution());
		assertNull(route.minecraftVersion());
		assertEquals(List.of("lobby", "game"), route.backendNames());
		assertEquals("lobby", route.defaultBackend());
		assertEquals("Java 21 · temurin · release 21.0.10 (scenario)", route.javaRequirement());
		assertThrows(UnsupportedOperationException.class, () -> route.backendNames().add("other"));
		assertThrows(UnsupportedOperationException.class, () -> topology.processes().clear());

		// As the tooling protocol writes them: through their fields, since their accessors are fluent.
		var json = new ObjectMapper()
				.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
				.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
				.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
		var encoded = json.readTree(json.writeValueAsString(scenarios));
		assertEquals("PROXY", encoded.get(1).path("processes").get(2).path("role").asText());
		assertFalse(encoded.get(1).has("setupHook"));
		assertFalse(encoded.get(1).has("players"));
		for (var scenario : encoded)
			for (var process : scenario.path("processes")) {
				assertFalse(process.has("host"));
				assertFalse(process.has("port"));
				assertFalse(process.has("state"));
				assertFalse(process.has("workDirectory"));
			}
	}

	@Test
	void describesJavaRequirementsAndSourcesWithIndependentInheritance() {
		AnvilScenario scenario = AnvilScenario.builder().name("java-overrides").entrypoint("inherited")
				.javaSelection(JavaSelection.builder()
						.requirement(JavaRequirement.builder().featureVersion(21).build())
						.source(JavaSource.home(Path.of("unresolved", "scenario-jdk")))
						.build())
				.server(server("inherited", Distribution.remote("1.21.11", "132")))
				.server(server("requirement", Distribution.remote("26.1.2", "74")).toBuilder()
						.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(25).build()).build())
						.build())
				.server(server("source", Distribution.remote("1.21.11", "132")).toBuilder()
						.javaSelection(JavaSelection.builder()
								.source(JavaSource.archive(URI.create("https://invalid.example/jdk.tar.gz"), "b".repeat(64)))
								.build())
						.build())
				.server(server("executable", Distribution.remote("1.21.11", "132")).toBuilder()
						.javaSelection(JavaSelection.builder()
								.requirement(JavaRequirement.builder().build())
								.source(JavaSource.executable(Path.of("unresolved", "custom-java")))
								.build())
						.build())
				.build();
		ScenarioDescriptor descriptor = ScenarioDescriptorFactory.describe("example.Definition", scenario);
		String home = "Java home: " + Path.of("unresolved", "scenario-jdk");

		assertEquals("Java 21; " + home, descriptor.javaRequirement());
		assertEquals("Java 21 (scenario); " + home + " (scenario)", descriptor.processes().get(0).javaRequirement());
		assertEquals("Java 25; " + home + " (scenario)", descriptor.processes().get(1).javaRequirement());
		assertEquals(
				"Java 21 (scenario); Java archive: https://invalid.example/jdk.tar.gz",
				descriptor.processes().get(2).javaRequirement()
		);
		assertEquals("Planned default LTS; Java executable: " + Path.of("unresolved", "custom-java"),
				descriptor.processes().get(3).javaRequirement());
	}

	private static MinecraftServer server(String name, Distribution distribution) {
		return MinecraftServer.builder().name(name).platform("paper").distribution(distribution).build();
	}
}
