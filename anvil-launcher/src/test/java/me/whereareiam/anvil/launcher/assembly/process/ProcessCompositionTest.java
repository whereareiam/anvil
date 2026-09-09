package me.whereareiam.anvil.launcher.assembly.process;

import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCompositionTest {

	@Test
	void filtersBeforeGraphValidationAndRejectsMissingDependenciesWithoutCreatingCapabilities() {
		AtomicInteger created = new AtomicInteger();
		var dependent = provider("dependent", Dependent.class, context -> {
			created.incrementAndGet();
			return () -> context.requireCapability(OwnerIdentity.class);
		}, Set.of(OwnerIdentity.class));
		AgentProcessCapabilityProvider<OwnerIdentity> unsupported = new AgentProcessCapabilityProvider<>() {
			@Override
			public @NotNull CapabilityDescriptor descriptor() { return CapabilityDescriptor.builder().id("other-platform").build(); }

			@Override
			public @NotNull Class<OwnerIdentity> capability() { return OwnerIdentity.class; }

			@Override
			public boolean supportsPlatform(@NotNull String platform) { return false; }

			@Override
			public @NotNull OwnerIdentity create(@NotNull AgentProcessCapabilityContext context) {
				throw new AssertionError("Unsupported provider must not be created");
			}
		};

		var failure = assertThrows(CapabilityException.class, () -> new ProcessComposition(plan(true, "first"),
				new ScenarioAgentDirectory(), List.of(dependent, unsupported)));
		assertTrue(failure.getMessage().contains("requires missing capabilities"));
		assertEquals(0, created.get());
	}

	@Test
	void noAgentProcessesKeepTheOriginalGroupAndDoNotDiscoverProviders() {
		var directory = new ScenarioAgentDirectory();
		ProcessGroup raw = group(List.of("first"), ignored -> { });
		var composition = ProcessComposition.discover(plan(false, "first"), directory);

		assertSame(raw, composition.bind(raw));
		assertTrue(directory.agents().isEmpty());
		assertFalse(raw.server("first").hasCapability(OwnerIdentity.class));
		assertThrows(CapabilityUnavailableException.class, () -> raw.server("first").capability(OwnerIdentity.class));
	}






	private <C extends ProcessCapability> AgentProcessCapabilityProvider<C> provider(
			String id,
			Class<C> capability,
			Function<AgentProcessCapabilityContext, C> factory,
			Set<Class<? extends ProcessCapability>> dependencies
	) {
		return new AgentProcessCapabilityProvider<>() {
			@Override
			public @NotNull CapabilityDescriptor descriptor() {
				return CapabilityDescriptor.builder().id(id).requiredCapabilities(dependencies).build();
			}

			@Override
			public @NotNull Class<C> capability() { return capability; }


			@Override
			public @NotNull C create(@NotNull AgentProcessCapabilityContext context) { return factory.apply(context); }
		};
	}

	private PlatformPlan plan(boolean agents, String... names) {
		var scenario = AnvilScenario.builder().name("test").entrypoint(names[0]);
		var plan = PlatformPlan.builder();
		for (String name : names) {
			var server = MinecraftServer.builder().name(name).platform("test")
					.distribution(Distribution.remote("1.21.11", "test")).build();
			scenario.server(server);
			plan.process(name, ProcessPlan.builder().declaration(server).agent(agents).javaRequirement(JavaRequirement.builder().build())
					.workspace(WorkspacePlan.builder().build())
					.forwarding(ForwardingConfiguration.builder().build())
					.readinessPattern(Pattern.compile("READY")).stopCommand("stop").build());
		}

		return plan.scenario(scenario.build()).build();
	}

	private ProcessGroup group(List<String> names, Consumer<Boolean> finish) {
		Map<String, RunningProcess> servers = new LinkedHashMap<>();
		for (String name : names)
			servers.put(name, (RunningServer) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{RunningServer.class}, (proxy, method, arguments) -> switch (method.getName()) {
						case "name" -> name;
						case "hasCapability" -> false;
						case "capability" -> throw new CapabilityUnavailableException("No process capabilities installed");
						default -> throw new AssertionError(method.getName());
					}));

		return (ProcessGroup) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ProcessGroup.class},
				(proxy, method, arguments) -> switch (method.getName()) {
					case "get", "server" -> servers.get((String) arguments[0]);
					case "all", "servers" -> List.copyOf(servers.values());
					case "proxies" -> List.of();
					case "finish" -> {
						finish.accept((Boolean) arguments[0]);
						yield null;
					}
					default -> throw new AssertionError(method.getName());
				});
	}

	private interface OwnerIdentity extends ProcessCapability {
		String name();
	}

	private interface Dependent extends ProcessCapability {
		OwnerIdentity identity();
	}
}
