package me.whereareiam.anvil.workbench.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioRequirements;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class WireMapperTest {
	private final ObjectMapper mapper = WireMapper.create();

	@Test
	void carriesAScenarioWithItsProcessesAndWorkspacesButNotItsHook() throws Exception {
		AnvilScenario scenario = AnvilScenario.builder()
				.name("network")
				.entrypoint("proxy")
				.server(MinecraftServer.builder()
						.name("lobby")
						.platform("paper")
						.distribution(Distribution.remote("1.21.11", "132"))
						.javaSelection(JavaSelection.builder()
								.requirement(JavaRequirement.builder().featureVersion(21).build())
								.source(JavaArchive.builder().uri(URI.create("https://example.com/jdk.tar.gz")).sha256("a".repeat(64)).build())
								.build())
						.workspace(WorkspacePlan.builder()
								.asset(WorkspaceAsset.builder()
										.source(AssetSource.path(Path.of("/build/libs/plugin.jar")))
										.target(Path.of("plugins", "plugin.jar"))
										.build())
								.build())
						.setting("difficulty", "peaceful")
						.build())
				.proxy(MinecraftProxy.builder()
						.name("proxy")
						.platform("velocity")
						.distribution(Distribution.remote("3.5.1", "615"))
						.server("lobby")
						.defaultServer("lobby")
						.build())
				.container(ContainerProcess.builder().name("redis").image("redis:7").port(6379).build())
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofSeconds(90)).build())
				.requirements(ScenarioRequirements.builder().reach("sessionserver.mojang.com").memoryMegabytes(4096).build())
				.setupHook(context -> { })
				.build();

		AnvilScenario carried = mapper.readValue(mapper.writeValueAsString(scenario), AnvilScenario.class);

		assertEquals(scenario.toBuilder().setupHook(null).build(), carried);
		assertEquals("plugins/plugin.jar", mapper.readTree(mapper.writeValueAsString(scenario))
				.at("/servers/0/workspace/assets/0/target").asText(), "Relative paths mean the same on either side");
	}

	@Test
	void keepsTheTypeOfAnInterfaceAModelHolds() throws Exception {
		MinecraftProcess process = MinecraftProxy.builder().name("proxy").platform("velocity")
				.distribution(Distribution.local(Path.of("/opt/velocity.jar"))).server("lobby").defaultServer("lobby").build();
		ProcessLaunch launch = JavaCommand.builder().jar(Path.of("/srv/anvil/cache/paper.jar")).memoryMegabytes(1024)
				.jvmArgument("-Dexample=true").environmentVariable("ANVIL_AGENT_PORT", "25566").build();

		assertEquals(process, mapper.readValue(mapper.writeValueAsString(process), MinecraftProcess.class));
		assertEquals(launch, mapper.readValue(mapper.writeValueAsString(launch), ProcessLaunch.class));
	}

	@Test
	void carriesExecutionRequestsAddressesAndFacts() throws Exception {
		ProcessRequest request = ProcessRequest.builder().name("lobby").workspace(Path.of("/srv/anvil/work/run/lobby"))
				.agent(true).listener("metrics").build();
		WorkbenchFacts facts = WorkbenchFacts.builder().workbenchName("workstation").processors(16).memoryMegabytes(65536)
				.execution("docker").unavailableExecution("local", "the operator allows only docker").reachableHost("*").build();
		InetSocketAddress address = new InetSocketAddress("127.0.0.1", 25565);
		InetSocketAddress alias = InetSocketAddress.createUnresolved("lobby", 25565);

		assertEquals(request, mapper.readValue(mapper.writeValueAsString(request), ProcessRequest.class));
		assertEquals(facts, mapper.readValue(mapper.writeValueAsString(facts), WorkbenchFacts.class));
		assertEquals(address, mapper.readValue(mapper.writeValueAsString(address), InetSocketAddress.class));
		assertEquals(alias.toString(), mapper.readValue(mapper.writeValueAsString(alias), InetSocketAddress.class).toString());
	}
}
