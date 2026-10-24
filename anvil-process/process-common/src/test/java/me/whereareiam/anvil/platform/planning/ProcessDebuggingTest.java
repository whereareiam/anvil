package me.whereareiam.anvil.platform.planning;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessDebuggingTest {
	private final AnvilScenario scenario = AnvilScenario.builder()
			.name("network")
			.entrypoint("proxy")
			.server(MinecraftServer.builder().name("lobby").platform("paper").distribution(Distribution.remote("1.21.11", "132")).build())
			.proxy(MinecraftProxy.builder().name("proxy").platform("velocity").distribution(Distribution.remote("3.5.1", "615"))
					.server("lobby").defaultServer("lobby").build())
			.build();

	@Test
	void leavesAScenarioAloneWhenNothingIsDebugged() {
		assertSame(scenario, ProcessDebugging.apply(scenario, EngineOptions.builder().build()));
		assertSame(scenario, ProcessDebugging.apply(scenario, EngineOptions.builder().debugProcess("elsewhere").build()));
	}

	@Test
	void givesANamedProcessADebugListenerAndAgent() {
		AnvilScenario debugged = ProcessDebugging.apply(scenario, EngineOptions.builder().debugProcess("proxy").build());

		MinecraftProxy proxy = debugged.getProxies().getFirst();
		assertEquals(List.of("debug"), proxy.getListeners());
		assertEquals(List.of("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${anvil.process.proxy.listener.debug}"),
				proxy.getJvmArguments());
		assertEquals(scenario.getServers(), debugged.getServers());
		assertEquals(scenario.getProcessTimeouts(), debugged.getProcessTimeouts());
	}

	@Test
	void waitsLongEnoughForADebuggerToAttachWhenEveryProcessSuspends() {
		AnvilScenario debugged = ProcessDebugging.apply(scenario, EngineOptions.builder().debugProcess("*").debugSuspend(true).build());

		assertTrue(debugged.getServers().getFirst().getJvmArguments().getFirst().contains("suspend=y"));
		assertTrue(debugged.getProxies().getFirst().getJvmArguments().getFirst().contains("suspend=y"));
		assertEquals(Duration.ofMinutes(30), debugged.getProcessTimeouts().getStartup());
	}
}
