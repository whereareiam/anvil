package me.whereareiam.anvil.runner.scenario;

import java.util.List;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioDebuggingTest {
	private static final AnvilScenario NETWORK = AnvilScenario.builder()
			.name("network")
			.entrypoint("velocity")
			.server(MinecraftServer.builder().name("lobby").platform("paper").distribution(Distribution.remote("1.21.11", "1"))
					.jvmArgument("-Dexample=true").build())
			.server(MinecraftServer.builder().name("games").platform("paper").distribution(Distribution.remote("1.21.11", "1")).build())
			.proxy(MinecraftProxy.builder().name("velocity").platform("velocity").distribution(Distribution.remote("3.5.1", "1"))
					.server("lobby").defaultServer("lobby").build())
			.build();

	@Test void addsTheAgentToEveryProcessWithoutATarget() {
		AnvilScenario debugged = ScenarioDebugging.withAgent(NETWORK, null);

		assertEquals(List.of("-Dexample=true", ScenarioDebugging.AGENT), debugged.getServers().get(0).getJvmArguments());
		assertEquals(List.of(ScenarioDebugging.AGENT), debugged.getServers().get(1).getJvmArguments());
		assertEquals(List.of(ScenarioDebugging.AGENT), debugged.getProxies().getFirst().getJvmArguments());
	}

	@Test void addsTheAgentToTheTargetOnly() {
		AnvilScenario debugged = ScenarioDebugging.withAgent(NETWORK, "games");

		assertEquals(List.of("-Dexample=true"), debugged.getServers().get(0).getJvmArguments());
		assertEquals(List.of(ScenarioDebugging.AGENT), debugged.getServers().get(1).getJvmArguments());
		assertEquals(List.of(), debugged.getProxies().getFirst().getJvmArguments());
		assertEquals(NETWORK.toBuilder().clearServers().clearProxies().build(),
				debugged.toBuilder().clearServers().clearProxies().build(), "Only process arguments change");
	}
}
