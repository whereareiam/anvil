package me.whereareiam.anvil.runner;
import me.whereareiam.anvil.runner.scenario.ScenarioDescriptorFactory;

import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
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
		AnvilScenario local = AnvilScenario.builder().name("direct-environment").entrypoint(direct.getName()).server(direct).build();
		MinecraftServer lobby = server("lobby", Distribution.artifact("lobby-runtime"));
		MinecraftServer game = server("game", Distribution.pinned("1.21.11", "a".repeat(64)));
		MinecraftProxy proxy = MinecraftProxy.builder().name("proxy").platform("velocity")
				.distribution(Distribution.remote("3.5.1", "latest"))
				.server("lobby").server("game").defaultServer("lobby").build();
		AnvilScenario network = AnvilScenario.builder().name("proxy-environment")
				.metadata(PresentationMetadata.builder().displayName("Proxy investigation")
						.description("Two backends behind one proxy").category("Routing").tag("matrix").build())
				.entrypoint("proxy").server(lobby).server(game).proxy(proxy)
				.manual(true).executionProviderId("local").processTimeouts(ProcessTimeouts.builder().startup(Duration.ofSeconds(75)).shutdown(Duration.ofSeconds(7)).build())
				.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(21).distribution("temurin").release("21.0.10").build()).build())
				.setupHook(ignored -> { throw new AssertionError("Scenario inspection must not execute setup"); }).build();
		List<ScenarioDescriptor> scenarios;
		try (RunnerSession session = new RunnerSession(() -> {
			throw new AssertionError("Scenario inspection must not construct or start an engine");
		}, ScenarioRepository.fromScenarios(List.of(local, network)))) {
			scenarios = session.scenarios();
			assertEquals("IDLE", session.snapshot().getState().name());
			assertTrue(session.snapshot().getProcesses().isEmpty());
		}

		assertEquals(List.of("direct-environment", "proxy-environment"), scenarios.stream().map(ScenarioDescriptor::getName).toList());
		assertEquals(List.of("direct-server"), scenarios.getFirst().getProcesses().stream().map(ProcessDefinition::getName).toList());
		assertNull(scenarios.getFirst().getExecutionProviderId());
		assertEquals(0, scenarios.getFirst().getStartupTimeoutMillis());
		assertEquals(0, scenarios.getFirst().getShutdownTimeoutMillis());
		assertEquals("Project default", scenarios.getFirst().getJavaRequirement());
		ProcessDefinition directInfo = scenarios.getFirst().getProcesses().getFirst();
		assertEquals(ProcessRole.SERVER, directInfo.getRole());
		assertEquals("Direct backend", directInfo.getDisplayName());
		assertEquals("Local server under test", directInfo.getDescription());
		assertEquals("Local JAR: " + Path.of("unresolved", "server.jar"), directInfo.getDistribution());
		assertEquals("26.1.2", directInfo.getMinecraftVersion());
		assertNull(directInfo.getDistributionVersion());
		assertTrue(directInfo.isOnlineMode());
		assertEquals(1536, directInfo.getMemoryMegabytes());
		assertTrue(directInfo.getBackendNames().isEmpty());

		ScenarioDescriptor topology = scenarios.get(1);
		assertEquals("Proxy investigation", topology.getDisplayName());
		assertEquals("Two backends behind one proxy", topology.getDescription());
		assertEquals("Routing", topology.getCategory());
		assertEquals(List.of("matrix"), topology.getTags());
		assertEquals("proxy", topology.getEntrypoint());
		assertEquals(75000, topology.getStartupTimeoutMillis());
		assertEquals(7000, topology.getShutdownTimeoutMillis());
		assertEquals("local", topology.getExecutionProviderId());
		assertTrue(topology.isManual());
		assertEquals(List.of("lobby", "game", "proxy"), topology.getProcesses().stream().map(ProcessDefinition::getName).toList());
		assertEquals("Lobby", topology.getProcesses().getFirst().getDisplayName());
		assertEquals("Artifact: lobby-runtime", topology.getProcesses().getFirst().getDistribution());
		assertEquals("1.21.11 (SHA-256 " + "a".repeat(64) + ")", topology.getProcesses().get(1).getDistribution());
		ProcessDefinition route = topology.getProcesses().getLast();
		assertEquals(ProcessRole.PROXY, route.getRole());
		assertEquals("velocity", route.getPlatform());
		assertEquals("3.5.1", route.getDistributionVersion());
		assertEquals("latest", route.getDistributionBuild());
		assertEquals("3.5.1 (build latest)", route.getDistribution());
		assertNull(route.getMinecraftVersion());
		assertEquals(List.of("lobby", "game"), route.getBackendNames());
		assertEquals("lobby", route.getDefaultBackend());
		assertEquals("Java 21 · temurin · release 21.0.10 (scenario)", route.getJavaRequirement());
		assertThrows(UnsupportedOperationException.class, () -> route.getBackendNames().add("other"));
		assertThrows(UnsupportedOperationException.class, () -> topology.getProcesses().clear());

		var json = new ObjectMapper();
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
				.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(21).build()).source(JavaSource.home(Path.of("unresolved", "scenario-jdk"))).build())
				.server(server("inherited", Distribution.remote("1.21.11", "132")))
				.server(server("requirement", Distribution.remote("26.1.2", "74")).toBuilder()
						.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().featureVersion(25).build()).build()).build())
				.server(server("source", Distribution.remote("1.21.11", "132")).toBuilder()
						.javaSelection(JavaSelection.builder().source(JavaSource.archive(URI.create("https://invalid.example/jdk.tar.gz"), "b".repeat(64))).build()).build())
				.server(server("executable", Distribution.remote("1.21.11", "132")).toBuilder()
						.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().build()).source(JavaSource.executable(Path.of("unresolved", "custom-java"))).build()).build())
				.build();
		ScenarioDescriptor descriptor = ScenarioDescriptorFactory.describe("example.Definition", scenario);
		String home = "Java home: " + Path.of("unresolved", "scenario-jdk");

		assertEquals("Java 21; " + home, descriptor.getJavaRequirement());
		assertEquals("Java 21 (scenario); " + home + " (scenario)", descriptor.getProcesses().get(0).getJavaRequirement());
		assertEquals("Java 25; " + home + " (scenario)", descriptor.getProcesses().get(1).getJavaRequirement());
		assertEquals("Java 21 (scenario); Java archive: https://invalid.example/jdk.tar.gz", descriptor.getProcesses().get(2).getJavaRequirement());
		assertEquals("Planned default LTS; Java executable: " + Path.of("unresolved", "custom-java"),
				descriptor.getProcesses().get(3).getJavaRequirement());
	}

	private static MinecraftServer server(String name, Distribution distribution) {
		return MinecraftServer.builder().name(name).platform("paper").distribution(distribution).build();
	}
}
