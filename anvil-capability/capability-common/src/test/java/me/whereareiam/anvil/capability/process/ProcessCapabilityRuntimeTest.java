package me.whereareiam.anvil.capability.process;

import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.event.lifecycle.Closed;
import me.whereareiam.anvil.capability.api.event.lifecycle.ProcessRestarted;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioFinished;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioStarted;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.model.platform.PlatformEvent;
import me.whereareiam.anvil.capability.api.platform.ProcessPlatform;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCapabilityRuntimeTest {
	@Test
	void preparedOwnersComposeIndividuallyAfterStartAndKeepTheExecutionHandle() {
		List<String> created = new ArrayList<>();
		var identity = provider("identity", OwnerIdentity.class, context -> {
			created.add(context.processName());
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(identity));
		try (var processes = composition.bind(group(composition, List.of("first", "second"), ignored -> { }))) {
			var before = processes.get("first");
			assertFalse(before.hasCapability(OwnerIdentity.class));
			assertTrue(created.isEmpty());
			var first = processes.start("first");
			assertSame(before, first);
			assertSame(first, processes.get("first"));
			assertEquals(List.of("first"), created);
			assertFalse(processes.get("second").hasCapability(OwnerIdentity.class));
			var capability = first.capability(OwnerIdentity.class);
			var replacement = processes.restart("first");
			assertNotSame(first, replacement);
			assertSame(capability, replacement.capability(OwnerIdentity.class));
			processes.startAll();
			assertEquals(List.of("first", "second"), created);
		}
	}

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

		try (ProcessGroup processes = composition.bind(group(composition, List.of("first", "second"), ignored -> { }))) {
			processes.startAll();
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
	void takesOnlyProvidersOfTheProcessPlatformAndRejectsMissingDependenciesWithoutCreatingCapabilities() {
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

		var failure = assertThrows(CapabilityException.class, () -> composition(List.of("first"), List.of(dependent, unsupported)));
		assertTrue(failure.getMessage().contains("requires missing capabilities"));
		assertEquals(0, created.get());
	}

	@Test
	void discoversNothingAndAsksForNoChannelWithoutProcessesThatRunAnAgent() {
		ProcessCapabilityRuntime runtime = ProcessCapabilityRuntime.discover(Map.of(), name -> {
			throw new AssertionError("No process runs an agent");
		}, name -> {
			throw new AssertionError("No process runs an agent");
		});

		assertNull(runtime.owner("first"));
	}

	@Test
	void emptyProcessOwnersRejectCapabilityLookup() {
		var composition = composition(List.of("first"), List.of());
		try (var processes = composition.bind(group(composition, List.of("first"), ignored -> { }))) {
			processes.startAll();
			var process = processes.server("first");
			assertEquals("first", process.name());
			assertFalse(process.hasCapability(OwnerIdentity.class));
			assertThrows(CapabilityUnavailableException.class, () -> process.capability(OwnerIdentity.class));
		}
	}

	@Test
	void failedDeferredCompositionReleasesEveryCreatedOwnerOnFinalization() {
		List<String> events = new ArrayList<>();
		AssertionError failure = new AssertionError("second owner creation");
		IllegalStateException cleanupFailure = new IllegalStateException("first owner cleanup");
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(Closed.class, closed -> {
				events.add(context.processName());
				if (context.processName().equals("first")) throw cleanupFailure;
			});
			if (context.processName().equals("second")) throw failure;
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		ProcessGroup processes = composition.bind(group(composition, List.of("first", "second"), successful -> events.add("processes:" + successful)));

		assertSame(failure, assertThrows(AssertionError.class, processes::startAll));
		assertEquals(List.of("second"), events);
		assertSame(cleanupFailure, assertThrows(IllegalStateException.class, () -> processes.finish(false)));
		assertEquals(List.of("second", "first", "processes:false"), events);
	}

	@Test
	void closesAllOwnersBeforeProcessesAndPropagatesFailedRetentionAndSuppressedFailures() {
		List<String> events = new ArrayList<>();
		AssertionError first = new AssertionError("second owner cleanup");
		IllegalStateException second = new IllegalStateException("first owner cleanup");
		IllegalStateException processFailure = new IllegalStateException("process cleanup");
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(Closed.class, closed -> {
				events.add(context.processName());
				if (context.processName().equals("second")) throw first;
				throw second;
			});
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(composition, List.of("first", "second"), successful -> {
			events.add("processes:" + successful);
			throw processFailure;
		}));
		processes.startAll();
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
		composition.bind(group(composition, List.of("first"), outcomes::add)).finish(false);

		assertEquals(List.of(false), outcomes);
	}

	@Test
	void cleanupCanReadProcessCapabilitiesFromAnotherThread() {
		AtomicReference<ProcessGroup> owner = new AtomicReference<>();
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var provider = provider("identity", OwnerIdentity.class, context -> {
				context.on(Closed.class, closed -> {
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
			owner.set(composition.bind(group(composition, List.of("first"), ignored -> { })));
			owner.get().startAll();

			owner.get().close();
		}
	}

	@Test
	void replacementHandlesRetainTheLogicalProcessCapabilities() {
		var provider = provider("identity", OwnerIdentity.class, context -> context::processName, Set.of());
		var composition = composition(List.of("first"), List.of(provider));
		try (var processes = composition.bind(group(composition, List.of("first"), ignored -> {}))) {
			processes.startAll();
			var original = processes.server("first");
			var snapshot = processes.all();
			var capability = original.capability(OwnerIdentity.class);
			var replacement = processes.restart("first");

			assertNotSame(original, replacement);
			assertSame(replacement, processes.server("first"));
			assertSame(capability, replacement.capability(OwnerIdentity.class));
			assertSame(capability, original.capability(OwnerIdentity.class));
			assertNotNull(original.executionId());
			assertNotEquals(original.executionId(), replacement.executionId());
			assertSame(original, snapshot.iterator().next());
			assertSame(replacement, processes.get("first"));
		}
	}

	@Test
	void aScenariosGroupReportsScenarioStartedOnceEveryProcessIsReadyInProcessOrder() {
		List<String> events = new ArrayList<>();
		var provider = provider("identity", OwnerIdentity.class, context -> {
			events.add("created:" + context.processName());
			context.on(ScenarioStarted.class, started -> events.add("started:" + context.processName()));
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(composition, List.of("first", "second"), ignored -> { }));

		processes.startAll();

		assertEquals(List.of("created:first", "created:second", "started:first", "started:second"), events);
	}

	@Test
	void aFailedScenarioStartedActionFailsTheStartAfterTheOthersRan() {
		List<String> events = new ArrayList<>();
		IllegalStateException failure = new IllegalStateException("cannot start");
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(ScenarioStarted.class, started -> {
				events.add("started:" + context.processName());
				if (context.processName().equals("first")) throw failure;
			});
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(composition, List.of("first", "second"), ignored -> { }));

		assertSame(failure, assertThrows(IllegalStateException.class, processes::startAll));
		assertEquals(List.of("started:first", "started:second"), events);
	}

	@Test
	void reportsProcessRestartedToTheSameCapabilitiesForEveryLaterGeneration() {
		List<String> events = new ArrayList<>();
		var provider = provider("identity", OwnerIdentity.class, context -> {
			events.add("created:" + context.processName());
			context.on(ProcessRestarted.class, restarted -> events.add("restarted:" + context.processName()));
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(composition, List.of("first", "second"), ignored -> { }));
		processes.startAll();

		processes.restart("first");
		processes.restart("first");

		assertEquals(List.of("created:first", "created:second", "restarted:first", "restarted:first"), events);
	}

	@Test
	void aRetainedGroupReportsScenarioStartedForEveryScenarioItIsLentTo() {
		List<String> events = new ArrayList<>();
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(ScenarioStarted.class, started -> events.add("started"));
			context.on(ScenarioFinished.class, finished -> events.add("finished"));
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first"), List.of(provider));
		var processes = composition.bindRetained(group(composition, List.of("first"), ignored -> { }));
		processes.startAll();

		composition.startScenario();
		composition.finishScenario(true);
		composition.startScenario();

		assertEquals(List.of("started", "finished", "started"), events);
	}

	@Test
	void aScenariosGroupRunsScenarioFinishActionsInReverseBeforeClosingCapabilities() {
		List<String> events = new ArrayList<>();
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(ScenarioFinished.class, finished -> events.add("first-action:" + context.processName() + ":" + finished.isPassed()));
			context.on(ScenarioFinished.class, finished -> events.add("second-action:" + context.processName() + ":" + finished.isPassed()));
			context.on(Closed.class, closed -> events.add("close:" + context.processName()));
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(composition, List.of("first", "second"), successful -> events.add("processes:" + successful)));
		processes.startAll();

		processes.finish(true);

		assertEquals(List.of("second-action:second:true", "first-action:second:true", "second-action:first:true",
				"first-action:first:true", "close:second", "close:first", "processes:true"), events);
	}

	@Test
	void aRetainedGroupRunsScenarioFinishActionsForEveryScenarioButNotWhenItStops() {
		List<String> events = new ArrayList<>();
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(ScenarioFinished.class, finished -> events.add("scenario:" + finished.isPassed()));
			context.on(Closed.class, closed -> events.add("close"));
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first"), List.of(provider));
		var processes = composition.bindRetained(group(composition, List.of("first"), successful -> events.add("processes:" + successful)));
		processes.startAll();

		composition.finishScenario(true);
		composition.finishScenario(false);
		processes.finish(true);
		composition.finishScenario(true);

		assertEquals(List.of("scenario:true", "scenario:false", "close", "processes:true"), events);
	}

	@Test
	void scenarioFinishActionsOfCapabilitiesNeverCreatedDoNotRun() {
		AtomicInteger runs = new AtomicInteger();
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(ScenarioFinished.class, finished -> runs.incrementAndGet());
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(composition, List.of("first", "second"), ignored -> { }));
		processes.start("first");

		processes.finish(true);

		assertEquals(1, runs.get());
	}

	@Test
	void aFailedScenarioFinishActionRunsTheOthersClosesCapabilitiesAndFailsTheProcessesCleanup() {
		List<String> events = new ArrayList<>();
		IllegalStateException first = new IllegalStateException("second process action");
		IllegalStateException second = new IllegalStateException("first process action");
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(ScenarioFinished.class, finished -> {
				events.add("action:" + context.processName());
				throw context.processName().equals("second") ? first : second;
			});
			context.on(Closed.class, closed -> events.add("close:" + context.processName()));
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first", "second"), List.of(provider));
		var processes = composition.bind(group(composition, List.of("first", "second"), successful -> events.add("processes:" + successful)));
		processes.startAll();

		assertSame(first, assertThrows(IllegalStateException.class, () -> processes.finish(true)));
		assertArrayEquals(new Throwable[]{second}, first.getSuppressed());
		assertEquals(List.of("action:second", "action:first", "close:second", "close:first", "processes:false"), events);
	}

	@Test
	void aRetainedGroupReportsTheFirstFailedScenarioFinishAction() {
		IllegalStateException failure = new IllegalStateException("action");
		var provider = provider("identity", OwnerIdentity.class, context -> {
			context.on(ScenarioFinished.class, finished -> {
				throw failure;
			});
			return context::processName;
		}, Set.of());
		var composition = composition(List.of("first"), List.of(provider));
		composition.bindRetained(group(composition, List.of("first"), ignored -> { })).startAll();

		assertSame(failure, assertThrows(IllegalStateException.class, () -> composition.finishScenario(true)));
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
		Map<String, String> platforms = new LinkedHashMap<>();
		names.forEach(name -> platforms.put(name, "test"));

		return ProcessCapabilityRuntime.of(platforms, name -> SILENT, name -> PLATFORM, providers);
	}

	private static final ProcessPlatform PLATFORM = new ProcessPlatform() {
		@Override
		public boolean execute(@NotNull String command) {
			throw new AssertionError("Composition must not run commands");
		}

		@Override
		public @NotNull List<PlatformEvent> events() {
			return List.of();
		}
	};

	private static final RequestChannel SILENT = new RequestChannel() {
		@Override
		public @Nullable <Q, R> R request(@NotNull ChannelOperation<Q, R> operation, @Nullable Q request) {
			throw new AssertionError("Composition must not send requests");
		}
	};

	private ProcessGroup group(ProcessCapabilityRuntime composition, List<String> names, Consumer<Boolean> finish) {
		Map<String, RunningProcess> servers = new LinkedHashMap<>();
		for (String name : names)
			servers.put(name, server(name, composition.owner(name)));

		return (ProcessGroup) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ProcessGroup.class},
				(proxy, method, arguments) -> switch (method.getName()) {
					case "get", "server" -> servers.get((String) arguments[0]);
					case "start" -> {
						String name = (String) arguments[0];
						composition.initialize(name);
						yield servers.get(name);
					}
					case "restart" -> {
						String name = (String) arguments[0];
						RunningServer replacement = server(name, composition.owner(name));
						servers.put(name, replacement);
						composition.initialize(name);
						yield replacement;
					}
					case "all", "servers" -> List.copyOf(servers.values());
					case "startAll" -> {
						names.forEach(composition::initialize);
						yield null;
					}
					case "stop" -> null;
					case "proxies" -> List.of();
					case "finish" -> {
						finish.accept((Boolean) arguments[0]);
						yield null;
					}
					default -> throw new AssertionError(method.getName());
				});
	}

	private RunningServer server(String name, CapabilityOwner<ProcessCapability> owner) {
		UUID executionId = UUID.randomUUID();
		return (RunningServer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RunningServer.class},
				(proxy, method, arguments) -> switch (method.getName()) {
					case "name" -> name;
					case "executionId" -> executionId;
					case "state" -> ProcessState.READY;
					case "hasCapability" -> owner != null && owner.hasCapability((Class<? extends ProcessCapability>) arguments[0]);
					case "capability" -> {
						if (owner == null) throw new CapabilityUnavailableException("No process capabilities installed");
						yield owner.capability((Class<? extends ProcessCapability>) arguments[0]);
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

	private interface Unrelated extends ProcessCapability { }
}
