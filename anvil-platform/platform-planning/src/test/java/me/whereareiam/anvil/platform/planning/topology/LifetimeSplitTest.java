package me.whereareiam.anvil.platform.planning.topology;

import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LifetimeSplitTest {
	@Test
	void leavesAScenarioWhoseProcessesAllStopWithItUndivided() {
		assertNull(LifetimeSplit.of(plan("first", ProcessLifetime.SCENARIO, server("lobby", ProcessLifetime.SCENARIO))));
	}

	@Test
	void plansTheProcessesThatOutliveTheScenarioAsAScenarioOfTheirOwn() {
		LifetimeSplit split = LifetimeSplit.of(plan("first", ProcessLifetime.ENGINE, server("lobby", ProcessLifetime.ENGINE)));
		assertNotNull(split);

		PlatformPlan retained = split.retained();
		assertEquals(Set.of("lobby", "auth"), retained.getProcesses().keySet());
		assertEquals("engine-lobby-auth", retained.getScenario().getName());
		assertEquals("lobby", retained.getScenario().getEntrypoint(), "The scenario's proxy entrypoint is not part of the kept processes");
		assertEquals(List.of(), retained.getScenario().getProxies());
		assertNull(retained.getScenario().getSetupHook());
	}

	@Test
	void lendsAScenarioItsOwnProcessesWithTheForwardingTheRunningOnesUse() {
		LifetimeSplit running = LifetimeSplit.of(plan("first", ProcessLifetime.ENGINE, server("lobby", ProcessLifetime.ENGINE)));
		LifetimeSplit later = LifetimeSplit.of(plan("second", ProcessLifetime.ENGINE, server("lobby", ProcessLifetime.ENGINE)));
		assertNotNull(running);
		assertNotNull(later);

		PlatformPlan own = later.own(running.retained());

		assertEquals(Set.of("proxy", "fresh"), own.getProcesses().keySet());
		assertEquals("first", own.getProcesses().get("proxy").getForwarding().getSecret(), "The proxy adopts the secret its servers run with");
		assertEquals("first", own.getProcesses().get("fresh").getForwarding().getSecret(), "So does a server of the same forwarding group");
		assertEquals("second", own.getScenario().getName());
	}

	@Test
	void identifiesTheSameRunningProcessesWhateverSecretAScenarioPlanned() {
		LifetimeSplit first = LifetimeSplit.of(plan("first", ProcessLifetime.ENGINE, server("lobby", ProcessLifetime.ENGINE)));
		LifetimeSplit second = LifetimeSplit.of(plan("second", ProcessLifetime.ENGINE, server("lobby", ProcessLifetime.ENGINE)));
		LifetimeSplit changed = LifetimeSplit.of(plan("third", ProcessLifetime.ENGINE,
				server("lobby", ProcessLifetime.ENGINE).toBuilder().setting("difficulty", "hard").build()));
		assertNotNull(first);
		assertNotNull(second);
		assertNotNull(changed);

		assertEquals(first.identity(), second.identity());
		assertEquals(first.identity().hashCode(), second.identity().hashCode());
		assertNotEquals(first.identity(), changed.identity());
	}

	/**
	 * A proxy in front of {@code lobby}, {@code auth} and {@code fresh}, all in one forwarding group whose
	 * secret is the scenario's name. Only {@code fresh} always stops with the scenario.
	 */
	private static PlatformPlan plan(String name, ProcessLifetime auth, MinecraftServer lobby) {
		MinecraftServer authServer = server("auth", auth);
		MinecraftServer fresh = server("fresh", ProcessLifetime.SCENARIO);
		MinecraftProxy proxy = MinecraftProxy.builder().name("proxy").platform("test").distribution(Distribution.remote("1", "1"))
				.server("lobby").server("auth").server("fresh").defaultServer("lobby").build();
		AnvilScenario scenario = AnvilScenario.builder().name(name).entrypoint("proxy")
				.server(lobby).server(authServer).server(fresh).proxy(proxy).build();
		ForwardingConfiguration forwarding = ForwardingConfiguration.builder().mode(ForwardingMode.MODERN).secret(name).build();

		PlatformPlan.PlatformPlanBuilder plan = PlatformPlan.builder().scenario(scenario);
		for (MinecraftProcess process : List.of(lobby, authServer, fresh, proxy))
			plan.process(process.getName(), ProcessPlan.builder()
					.declaration(process)
					.workspace(process.getWorkspace())
					.forwarding(forwarding)
					.javaSelection(JavaSelection.builder().build())
					.proxy(process instanceof MinecraftProxy)
					.readinessPattern(Pattern.compile("READY"))
					.stopCommand("stop")
					.build());

		return plan.build();
	}

	private static MinecraftServer server(String name, ProcessLifetime lifetime) {
		return MinecraftServer.builder().name(name).platform("test").distribution(Distribution.remote("1.21.11", "1"))
				.lifetime(lifetime).build();
	}
}
