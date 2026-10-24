package me.whereareiam.anvil.workbench.requirement;

import me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.RemoteEndpoint;
import me.whereareiam.anvil.api.model.scenario.ScenarioRequirements;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequirementCheckTest {
	private final WorkbenchFacts facts = WorkbenchFacts.builder()
			.workbenchName("workstation")
			.processors(8)
			.memoryMegabytes(16384)
			.execution("local")
			.execution("docker")
			.reachableHost("*.mojang.com")
			.build();

	@Test
	void acceptsAScenarioWithinTheWorkbenchsFacts() {
		AnvilScenario scenario = server().requirements(ScenarioRequirements.builder()
				.reach("sessionserver.mojang.com")
				.processors(8)
				.memoryMegabytes(16384)
				.build()).build();

		assertEquals(List.of(), RequirementCheck.unmet(scenario, "local", facts));
		assertDoesNotThrow(() -> RequirementCheck.require(scenario, "local", facts));
	}

	@Test
	void listsEveryUnmetRequirementInOneRefusal() {
		AnvilScenario scenario = server().requirements(ScenarioRequirements.builder()
				.reach("api.minecraftservices.com")
				.processors(16)
				.memoryMegabytes(32768)
				.build()).build();

		ScenarioRequirementsException refused = assertThrows(ScenarioRequirementsException.class,
				() -> RequirementCheck.require(scenario, "local", facts));

		assertEquals("scenario", refused.getScenarioName());
		assertEquals("workstation", refused.getWorkbenchName());
		assertEquals(List.of(
				"its processes may not reach api.minecraftservices.com",
				"it needs 16 processors, but 8 are available",
				"it needs 32768 MiB of memory, but 16384 MiB are available"
		), refused.getUnmet());
	}

	@Test
	void refusesTheUnavailableExecutionProviderAContainerRunsOn() {
		WorkbenchFacts withoutDocker = facts.toBuilder().unavailableExecution("docker", "the Docker daemon does not answer").build();
		AnvilScenario scenario = server().container(ContainerProcess.builder()
				.name("database")
				.image("postgres:17")
				.executionProviderId("docker")
				.build()).build();

		assertEquals(List.of("execution provider 'docker' is unavailable: the Docker daemon does not answer"),
				RequirementCheck.unmet(scenario, "local", withoutDocker));
	}

	@Test
	void refusesTheUnavailableDefaultExecutionProviderOfServersAndProxies() {
		WorkbenchFacts withoutDocker = facts.toBuilder().unavailableExecution("docker", "the Docker daemon does not answer").build();

		assertEquals(1, RequirementCheck.unmet(server().build(), "docker", withoutDocker).size());
		assertEquals(List.of(), RequirementCheck.unmet(server().executionProviderId("local").build(), "docker", withoutDocker));
	}

	@Test
	void needsNoExecutionProviderForAScenarioOfRemotesAlone() {
		WorkbenchFacts nothingAvailable = facts.toBuilder().unavailableExecution("local", "unavailable").build();
		AnvilScenario remotes = AnvilScenario.builder()
				.name("public-network")
				.entrypoint("network")
				.remote(RemoteEndpoint.builder().name("network").address("play.example.com").minecraftVersion("1.21.11").build())
				.build();

		assertEquals(List.of(), RequirementCheck.unmet(remotes, "local", nothingAvailable));
	}

	@Test
	void keepsNeoForgeAndJavaInstallationsOfThisMachineOnTheLocalWorkbench() {
		AnvilScenario scenario = AnvilScenario.builder()
				.name("modded")
				.entrypoint("server")
				.server(MinecraftServer.builder().name("server").platform("neoforge")
						.distribution(Distribution.remote("1.21.1", "21.1.200"))
						.javaSelection(JavaSelection.builder().source(JavaSource.home(Path.of("/opt/jdk-21"))).build())
						.build())
				.build();

		assertEquals(List.of(), RequirementCheck.unmet(scenario, "local", facts.toBuilder().local(true).build()));
		assertEquals(List.of(
				"NeoForge process 'server' runs only on the workbench in the engine's own JVM",
				"process 'server' names a Java installation by its path on the engine's machine"
		), RequirementCheck.unmet(scenario, "local", facts.toBuilder().local(false).build()));
	}

	private static AnvilScenario.AnvilScenarioBuilder server() {
		return AnvilScenario.builder()
				.name("scenario")
				.entrypoint("server")
				.server(MinecraftServer.builder().name("server").platform("paper").distribution(Distribution.remote("1.21.11", "132")).build());
	}
}
