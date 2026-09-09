package me.whereareiam.anvil.capability.process;

import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCapabilityRuntimeTest {
	@Test
	void composesIndependentOwnersAndRestrictsDependenciesToTheirOwnDeclarations() {
		var dependent = provider("dependent", Dependent.class, context -> {
			OwnerIdentity identity = context.requireCapability(OwnerIdentity.class);
			assertEquals(context.processName(), identity.name());
			assertEquals("test", context.platformId());
			assertNotNull(context.channel());
			return () -> identity;
		}, Set.of(OwnerIdentity.class));
		var identity = provider("identity", OwnerIdentity.class, context -> context::processName, Set.of());
		var isolated = provider("isolated", Unrelated.class, context -> {
			assertTrue(context.findCapability(OwnerIdentity.class).isEmpty());
			assertThrows(RuntimeException.class, () -> context.requireCapability(OwnerIdentity.class));
			return new Unrelated() { };
		}, Set.of());
		var composition = composition(List.of("first", "second"),
				List.of(dependent, identity, isolated));

		try (ProcessGroup processes = composition.bind(group(List.of("first", "second"), ignored -> { }))) {
			var first = processes.server("first");
			var second = processes.server("second");
			assertEquals("first", first.name());
			assertSame(first.capability(OwnerIdentity.class), first.capability(Dependent.class).identity());
			assertNotSame(first.capability(OwnerIdentity.class), second.capability(OwnerIdentity.class));
			assertSame(first, processes.get("first"));
			assertThrows(UnsupportedOperationException.class, () -> processes.all().clear());
			assertThrows(UnsupportedOperationException.class, () -> processes.servers().clear());
		}
	}



	@Test
	void emptyProcessOwnersRejectCapabilityLookup() {
		var composition = composition(List.of("first"), List.of());
		try (var processes = composition.bind(group(List.of("first"), ignored -> { }))) {
			var process = processes.server("first");
			assertEquals("first", process.name());
			assertFalse(process.hasCapability(OwnerIdentity.class));
			assertThrows(CapabilityUnavailableException.class, () -> process.capability(OwnerIdentity.class));
		}
	}

	@Test
	void rollsBackEveryCreatedOwnerBeforeReturningACompositionFailure() {
		List<String> events = new ArrayList<>();
		AssertionError failure = new AssertionError("second owner creation");
		IllegalStateException cleanupFailure = new IllegalStateException("first owner cleanup");
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.onClose(() -> {
				events.add(context.processName());
				if (context.processName().equals("first")) throw cleanupFailure;
			});
			if (context.processName().equals("second")) throw failure;
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		ProcessGroup processes = group(List.of("first", "second"), successful -> fail("Caller owns process rollback"));

		assertSame(failure, assertThrows(AssertionError.class, () -> composition.bind(processes)));
		assertEquals(List.of("second", "first"), events);
		assertArrayEquals(new Throwable[]{cleanupFailure}, failure.getSuppressed());
	}

	@Test
	void closesAllOwnersBeforeProcessesAndPropagatesFailedRetentionAndSuppressedFailures() {
		List<String> events = new ArrayList<>();
		AssertionError first = new AssertionError("second owner cleanup");
		IllegalStateException second = new IllegalStateException("first owner cleanup");
		IllegalStateException processFailure = new IllegalStateException("process cleanup");
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.onClose(() -> {
				events.add(context.processName());
				if (context.processName().equals("second")) throw first;
				throw second;
			});
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(List.of("first", "second"), successful -> {
			events.add("processes:" + successful);
			throw processFailure;
		}));
		var process = processes.server("first");

		assertSame(first, assertThrows(AssertionError.class, processes::close));
		processes.close();
		assertEquals(List.of("second", "first", "processes:false"), events);
		assertArrayEquals(new Throwable[]{second, processFailure}, first.getSuppressed());
		assertThrows(CapabilityUnavailableException.class, () -> process.capability(OwnerIdentity.class));
		assertFalse(process.hasCapability(OwnerIdentity.class));
	}

	@Test
	void callerFailureReachesProcessesEvenWhenCapabilityCleanupSucceeds() {
		List<Boolean> outcomes = new ArrayList<>();
		var composition = composition(List.of("first"), List.of());
		composition.bind(group(List.of("first"), outcomes::add)).finish(false);

		assertEquals(List.of(false), outcomes);
	}

	@Test
	void cleanupCanReadProcessCapabilitiesFromAnotherThread() {
		AtomicReference<ProcessGroup> owner = new AtomicReference<>();
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var provider = provider("identity", OwnerIdentity.class, context -> {
				context.onClose(() -> {
					try {
						assertFalse(tasks.submit(() -> owner.get().get("first")
								.hasCapability(OwnerIdentity.class)).get(2, TimeUnit.SECONDS));
					} catch (Exception failure) {
						throw new AssertionError("Capability cleanup must not hold owner lookup locks", failure);
					}
				});
				return context::processName;
			}, Set.of());
			var composition = composition(List.of("first"), List.of(provider));
			owner.set(composition.bind(group(List.of("first"), ignored -> { })));

			owner.get().close();
		}
	}

	@Test
	void replacementHandlesRetainTheLogicalProcessCapabilities() {
		var provider = provider("identity", OwnerIdentity.class, context -> context::processName, Set.of());
		var composition = composition(List.of("first"), List.of(provider));
		try (var processes = composition.bind(group(List.of("first"), ignored -> {}))) {
			var original = processes.server("first");
			var capability = original.capability(OwnerIdentity.class);
			var replacement = processes.restart("first");

			assertNotSame(original, replacement);
			assertSame(replacement, processes.server("first"));
			assertSame(capability, replacement.capability(OwnerIdentity.class));
			assertSame(capability, original.capability(OwnerIdentity.class));
		}
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

	private ProcessCapabilityRuntime composition(List<String> names, List<AgentProcessCapabilityProvider<?>> providers) {
		Map<String, List<CapabilityProvider<? extends ProcessCapability, CapabilityContext<ProcessCapability>>>> owners = new LinkedHashMap<>();
		RequestChannel channel = new RequestChannel() {
			@Override
			public @Nullable <Q, R> R request(@NotNull ChannelOperation<Q, R> operation, @Nullable Q request) {
				throw new AssertionError("Composition must not send requests");
			}
		};
		for (String name : names) {
			List<CapabilityProvider<? extends ProcessCapability, CapabilityContext<ProcessCapability>>> adapted = new ArrayList<>();
			for (AgentProcessCapabilityProvider<?> provider : providers)
				adapted.add(new AgentProcessCapabilityProviderAdapter<>(provider, name, "test", channel));
			owners.put(name, adapted);
		}

		return new ProcessCapabilityRuntime(owners);
	}

	private ProcessGroup group(List<String> names, Consumer<Boolean> finish) {
		Map<String, RunningProcess> servers = new LinkedHashMap<>();
		for (String name : names)
			servers.put(name, server(name));

		return (ProcessGroup) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ProcessGroup.class},
				(proxy, method, arguments) -> switch (method.getName()) {
					case "get", "server" -> servers.get((String) arguments[0]);
					case "restart" -> {
						String name = (String) arguments[0];
						RunningServer replacement = server(name);
						servers.put(name, replacement);
						yield replacement;
					}
					case "all", "servers" -> List.copyOf(servers.values());
					case "proxies" -> List.of();
					case "finish" -> {
						finish.accept((Boolean) arguments[0]);
						yield null;
					}
					default -> throw new AssertionError(method.getName());
				});
	}

	private RunningServer server(String name) {
		return (RunningServer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RunningServer.class},
				(proxy, method, arguments) -> switch (method.getName()) {
					case "name" -> name;
					case "hasCapability" -> false;
					case "capability" -> throw new CapabilityUnavailableException("No process capabilities installed");
					default -> throw new AssertionError(method.getName());
				});
	}

	private interface OwnerIdentity extends ProcessCapability {
		String name();
	}

	private interface Dependent extends ProcessCapability {
		OwnerIdentity identity();
	}

	private interface Unrelated extends ProcessCapability { }
}
