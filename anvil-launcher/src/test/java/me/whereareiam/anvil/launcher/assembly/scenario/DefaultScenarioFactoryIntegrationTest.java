package me.whereareiam.anvil.launcher.assembly.scenario;

import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioAccess;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioHook;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.console.Console;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.engine.AnvilEngineBuilder;
import me.whereareiam.anvil.engine.scenario.ScenarioSession;
import me.whereareiam.anvil.environment.execution.local.LocalExecutionProvider;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.environment.execution.managed.process.PortSelection;
import me.whereareiam.anvil.launcher.assembly.DefaultScenarioFactory;
import me.whereareiam.anvil.launcher.assembly.execution.CacheImageLocks;
import me.whereareiam.anvil.launcher.assembly.execution.JavaExecutionRuntime;
import me.whereareiam.anvil.launcher.assembly.execution.ProcessLauncher;
import me.whereareiam.anvil.launcher.assembly.provisioning.ArtifactPlatformSource;
import me.whereareiam.anvil.launcher.assembly.provisioning.ProvisioningServices;
import me.whereareiam.anvil.launcher.assembly.retention.RetainedProcesses;
import me.whereareiam.anvil.launcher.config.EngineDefaults;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DefaultScenarioFactoryIntegrationTest {
	@TempDir
	Path temporary;

	private Path executable;
	private boolean libraryClosed;
	private final List<AutoCloseable> owned = new ArrayList<>();

	@AfterEach
	void closeAssembly() throws Exception {
		Exception first = null;
		for (AutoCloseable resource : owned.reversed())
			try {
				resource.close();
			} catch (Exception failure) {
				if (first == null) first = failure;
				else first.addSuppressed(failure);
			}
		if (first != null) throw first;
	}

	@BeforeEach
	void resolveProcessFixture() {
		executable = FixtureArtifacts.process();
	}

	@Test
	void transfersOwnershipAfterSetupAndRetainsTheSharedLibraryUntilAssemblyCloses() throws Exception {
		var context = start(true, new TestPlatform(), scenario(anvil -> {
			assertEquals(ProcessState.READY, anvil.processes().server("server").state());
		}), this::library);
		var process = context.processes().server("server");
		Path run = process.workDirectory().getParent();
		assertTrue(Files.isDirectory(run));
		context.close();

		assertEquals(ProcessState.STOPPED, process.state());
		assertFalse(Files.exists(run));
		assertFalse(libraryClosed);
	}

	@Test
	void processesWithoutAgentCapabilitiesUseEmptyLookup() {
		try (var run = start(true, new TestPlatform(), scenario(null), this::library)) {
			var process = run.processes().server("server");
			assertFalse(process.hasCapability(Console.class));
			assertThrows(CapabilityUnavailableException.class, () -> process.capability(Console.class));
		}
	}

	@Test
	void rollsBackAssertionFailuresAndRetainsFailedWorkspace() {
		AtomicReference<ScenarioAccess> captured = new AtomicReference<>();
		AssertionError failure = new AssertionError("setup assertion");
		var scenario = scenario(context -> {
			captured.set(context);
			throw failure;
		});

		assertSame(failure, assertThrows(AssertionError.class,
				() -> start(true, new TestPlatform(), scenario, this::library)));
		var process = captured.get().processes().server("server");
		assertEquals(ProcessState.STOPPED, process.state());
		assertTrue(Files.exists(process.workDirectory()));
		assertFalse(libraryClosed);
	}

	@Test
	void reportsHookFailureWithScenarioContextAndHonorsFailedWorkspaceRemoval() {
		AtomicReference<ScenarioAccess> captured = new AtomicReference<>();
		IOException cause = new IOException("setup failed");
		var scenario = scenario(context -> {
			captured.set(context);
			throw cause;
		});

		ScenarioStartupException failure = assertThrows(ScenarioStartupException.class,
				() -> start(false, new TestPlatform(), scenario, this::library));
		assertEquals("test", failure.getScenarioName());
		assertSame(cause, failure.getCause());
		assertFalse(Files.exists(captured.get().processes().server("server").workDirectory().getParent()));
	}

	@Test
	void validatesBeforeCreatingLibraryOrWorkspace() {
		var invalid = scenario(null).toBuilder().entrypoint("missing").build();
		assertThrows(ScenarioValidationException.class, () -> start(true, new TestPlatform(), invalid, () -> {
			throw new AssertionError("The library must not be created before validation");
		}));
		assertFalse(Files.exists(temporary.resolve("work")));
	}

	@Test
	void validatesOnlyTheCapabilityGraphsOfLibrariesTheScenarioSelectsWithoutDeclarations(@TempDir Path services) throws Exception {
		Path descriptor = services.resolve("META-INF/services/" + ProtocolPlayerCapabilityProvider.class.getName());
		Files.createDirectories(descriptor.getParent());
		Files.writeString(descriptor, UnreachedLibraryCapabilityProvider.class.getName());
		// The scenario's server speaks 1.21.11, so it selects "current" by default and never "unreached".
		List<ProtocolLibraryProvider> libraries = List.of(releasedLibrary("current", "1.21.11"), releasedLibrary("unreached", "1.20.6"));
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try (var loader = new URLClassLoader(new URL[]{services.toUri().toURL()}, original)) {
			Thread.currentThread().setContextClassLoader(loader);
			try (var run = start(true, new TestPlatform(), scenario(null), libraries, (port, token, timeout) -> {
				throw new AssertionError("No agent is declared");
			})) {
				assertEquals(ProcessState.READY, run.processes().server("server").state());
			}

			// Tied libraries are selectable only by declaration, so neither graph is validated up front.
			List<ProtocolLibraryProvider> tied = List.of(releasedLibrary("current", "1.21.11"), releasedLibrary("unreached", "1.21.11"));
			var tie = scenario(null).toBuilder().name("tied").build();
			try (var run = start(true, new TestPlatform(), tie, tied, (port, token, timeout) -> {
				throw new AssertionError("No agent is declared");
			})) {
				assertEquals(ProcessState.READY, run.processes().server("server").state());
			}

			var declared = scenario(null).toBuilder().name("declared").protocolLibrary("unreached").build();
			var failure = assertThrows(CapabilityException.class, () -> start(true, new TestPlatform(), declared, libraries,
					(port, token, timeout) -> { throw new AssertionError("No agent is declared"); }));
			assertTrue(failure.getMessage().contains("requires missing capabilities"), failure.getMessage());
			assertFalse(Files.exists(temporary.resolve("work/declared")));
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	@ParameterizedTest
	@EnumSource(WorkspaceMode.class)
	void rejectsCollidingProcessDirectoriesBeforePreparingAnyWorkspace(WorkspaceMode mode) {
		var workspace = WorkspacePlan.builder().mode(mode).build();
		var server = scenario(null).getServers().getFirst().toBuilder().workspace(workspace);
		var scenario = scenario(null).toBuilder()
				.clearServers()
				.server(server.name("server/a").build())
				.server(server.name("server?a").build())
				.entrypoint("server/a")
				.build();

		var failure = assertThrows(ScenarioValidationException.class,
				() -> start(true, new TestPlatform(), scenario, this::library));

		assertTrue(failure.getMessage().contains("Processes 'server/a' and 'server?a' resolve to the same workspace directory"));
		assertFalse(Files.exists(temporary.resolve("work")));
	}

	@Test
	void preservesProviderFailuresAndRollsBackPreparation() {
		PlatformException failure = new PlatformException("provider failed");
		var provider = new TestPlatform() {
			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				throw failure;
			}
		};
		assertSame(failure, assertThrows(PlatformException.class,
				() -> start(false, provider, scenario(null), this::library)));
		assertNoRunDirectories();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void configurationIoFailureHasTheSameProcessContractOnFirstStartAndRestart(boolean restart) {
		IOException cause = new IOException("Configuration cannot be written");
		AtomicInteger attempts = new AtomicInteger();
		var provider = new TestPlatform() {
			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
				if (attempts.incrementAndGet() == (restart ? 2 : 1)) throw cause;
			}
		};
		ProcessException failure;
		if (restart) {
			try (var run = start(false, provider, scenario(null), this::library)) {
				var original = run.processes().server("server");
				failure = assertThrows(ProcessException.class, () -> run.processes().restart("server"));
				assertEquals(ProcessState.STOPPED, original.state());
			}
		} else {
			failure = assertThrows(ProcessException.class, () -> start(false, provider, scenario(null), this::library));
		}
		assertEquals("server", failure.getProcessName());
		assertSame(cause, failure.getCause());
		assertNoRunDirectories();
	}

	@Test
	void preservesProcessFailuresWithProcessNameAndCleansFailedLaunch() {
		var provider = new TestPlatform() {
			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				return ResolvedDistribution.builder().jar(temporary.resolve("missing.jar")).description("missing").build();
			}
		};
		ProcessException failure = assertThrows(ProcessException.class,
				() -> start(false, provider, scenario(null), this::library));
		assertEquals("server", failure.getProcessName());
		assertNoRunDirectories();
	}

	@Test
	void restartReusesPreparationAndTypedAccessWhileSnapshotsKeepOldGenerations() throws Exception {
		AtomicInteger resolves = new AtomicInteger();
		List<PlatformContext> configurations = new ArrayList<>();
		var provider = new TestPlatform() {
			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() {
				return MinecraftProcess.class;
			}

			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				resolves.incrementAndGet();
				return super.resolve(process, context);
			}

			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				configurations.add(context);
			}
		};
		var scenario = scenario(null).toBuilder()
				.entrypoint("proxy")
				.proxy(MinecraftProxy.builder().name("proxy").platform("test")
						.distribution(Distribution.remote("1", "1")).server("server").defaultServer("server").build())
				.build();
		try (var run = start(true, provider, scenario, this::library)) {
			var processes = run.processes();
			var original = processes.proxy("proxy");
			var server = processes.server("server");
			var snapshot = processes.all();
			assertSame(processes, run.processes());
			assertEquals(List.of(server, original), List.copyOf(snapshot));
			assertThrows(UnsupportedOperationException.class, snapshot::clear);
			assertThrows(UnsupportedOperationException.class, () -> processes.servers().clear());
			assertThrows(UnsupportedOperationException.class, () -> processes.proxies().clear());
			assertThrows(NoSuchElementException.class, () -> processes.get("missing"));
			assertThrows(IllegalArgumentException.class, () -> processes.server("proxy"));
			assertThrows(IllegalArgumentException.class, () -> processes.proxy("server"));
			Files.writeString(original.workDirectory().resolve("marker"), "preserved");

			var replacement = processes.restart("proxy");
			assertEquals(2, resolves.get(), "Restart must not resolve either distribution again");
			var proxyContexts = configurations.stream()
					.filter(configuration -> configuration.getWorkDirectory().getFileName().toString().equals("proxy"))
					.toList();
			assertEquals(2, proxyContexts.size());
			assertEquals(proxyContexts.getFirst(), proxyContexts.getLast(), "Restart uses the same prepared provider inputs");
			assertSame(server, processes.server("server"));
			assertSame(replacement, processes.proxy("proxy"));
			assertSame(replacement, processes.get("proxy"));
			assertEquals(original.address(), replacement.address());
			assertEquals(original.workDirectory(), replacement.workDirectory());
			assertEquals("preserved", Files.readString(replacement.workDirectory().resolve("marker")));
			assertEquals(ProcessState.STOPPED, original.state());
			assertEquals(ProcessState.READY, replacement.state());
			assertEquals(ProcessState.READY, server.state());
			assertEquals(List.of(server, original), List.copyOf(snapshot));
			run.close();
			assertThrows(IllegalStateException.class, () -> processes.restart("proxy"));
		}
	}

	@Test
	void failedRestartRetainsDiagnosticsAndPreventsSuccessCacheSaves() throws Exception {
		AtomicInteger configurations = new AtomicInteger();
		var provider = new TestPlatform() {
			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				if (configurations.incrementAndGet() > 1) throw new IllegalStateException("reconfigure failed");
			}
		};
		var scenario = withWorkspace(scenario(null));
		var run = start(true, provider, scenario, this::library);
		var process = run.processes().server("server");
		Files.createDirectories(process.workDirectory().resolve("data"));
		Files.writeString(process.workDirectory().resolve("data/value"), "unsuccessful");
		Files.writeString(process.workDirectory().resolve("failure-marker"), "remove on failure");
		assertThrows(IllegalStateException.class, () -> run.processes().restart("server"));
		run.close();

		assertEquals(ProcessState.STOPPED, process.state());
		assertTrue(Files.exists(process.workDirectory()));
		assertFalse(Files.exists(process.workDirectory().resolve("failure-marker")));
		assertFalse(Files.exists(temporary.resolve("cache/workspaces")));
	}

	@Test
	void exposesProcessCapabilitiesWithoutPlayersAndKeepsTheirInstancesAcrossGenerations() {
		AtomicInteger connections = new AtomicInteger();
		List<String> commands = new ArrayList<>();
		var provider = new TestPlatform() {
			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() {
				return MinecraftProcess.class;
			}

			@Override
			public PlatformAgentDescriptor platformAgent() {
				return PlatformAgentDescriptor.builder().entrypointClassName("fixture").build();
			}
		};
		var scenario = scenario(null).toBuilder().entrypoint("proxy")
				.proxy(MinecraftProxy.builder().name("proxy").platform("test")
						.distribution(Distribution.remote("1", "1")).server("server").defaultServer("server").build())
				.build();
		try (var run = start(true, provider, scenario, this::library, (port, token, timeout) -> {
			int generation = connections.incrementAndGet();
			return agent(() -> { }, command -> commands.add(generation + ":" + command));
		})) {
			var processes = run.processes();
			var original = processes.proxy("proxy");
			var server = processes.server("server");
			var console = original.capability(Console.class);
			var snapshot = processes.all();
			assertTrue(original.hasCapability(Console.class));
			assertNotSame(console, server.capability(Console.class));
			assertSame(original, processes.get("proxy"));
			assertTrue(console.execute("before"));
			assertEquals(List.of("2:before"), commands);

			var replacement = processes.restart("proxy");
			assertNotSame(original, replacement);
			assertSame(console, replacement.capability(Console.class));
			assertSame(replacement, processes.get("proxy"));
			assertSame(replacement, processes.proxy("proxy"));
			assertSame(server, processes.server("server"));
			assertEquals(List.of(server, original), List.copyOf(snapshot));
			assertEquals(ProcessState.STOPPED, original.state());
			assertEquals(ProcessState.READY, replacement.state());
			assertTrue(console.execute("after"));
			assertEquals(List.of("2:before", "3:after"), commands);

			run.close();
			assertThrows(IllegalStateException.class, () -> original.capability(Console.class));
			assertFalse(original.hasCapability(Console.class));
			assertFalse(replacement.hasCapability(Console.class));
		}
	}

	@Test
	void cleanupContinuesAcrossAgentErrorsAndStopsProcessesInReverseOrder() throws Exception {
		List<String> closed = new ArrayList<>();
		AssertionError first = new AssertionError("first agent cleanup");
		RuntimeException second = new IllegalStateException("second agent cleanup");
		AtomicInteger connections = new AtomicInteger();
		Path order = temporary.resolve("stop-order");
		var provider = new TestPlatform() {
			@Override
			public PlatformAgentDescriptor platformAgent() {
				return PlatformAgentDescriptor.builder().entrypointClassName("fixture").build();
			}

			@Override
			public @NotNull List<String> programArguments(@NotNull MinecraftProcess process) {
				return List.of(order.toString(), process.getName());
			}
		};
		var scenario = scenario(null).toBuilder().server(MinecraftServer.builder().name("second").platform("test")
				.distribution(Distribution.remote("1.21.11", "1")).build()).build();
		scenario = withWorkspace(scenario);
		var run = start(true, provider, scenario, this::library, (port, token, timeout) -> {
			int generation = connections.incrementAndGet();
			return agent(() -> {
				closed.add("agent-" + generation);
				if (generation == 1) throw first;
				throw second;
			});
		});
		var processes = run.processes().all();
		for (var process : processes)
			Files.writeString(process.workDirectory().resolve("failure-marker"), "remove on failure");
		Throwable cleanup = assertThrows(Throwable.class, run::close);
		assertTrue(cleanup == first || cleanup == second);
		assertTrue(List.of(cleanup.getSuppressed()).contains(first) || cleanup == first);
		assertTrue(List.of(cleanup.getSuppressed()).contains(second) || cleanup == second);
		assertEquals(Set.of("agent-1", "agent-2"), Set.copyOf(closed));
		assertEquals(List.of("second", "server"), Files.readAllLines(order));
		for (var process : processes) {
			assertEquals(ProcessState.STOPPED, process.state());
			assertFalse(Files.exists(process.workDirectory().resolve("failure-marker")));
		}
		run.close();
		assertEquals(2, closed.size());
	}

	@Test
	void forwardingSecretsBelongToEachRunAndConnectedGroup() throws Exception {
		Map<String, PlatformContext> contexts = new LinkedHashMap<>();
		var provider = new TestPlatform() {
			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() {
				return MinecraftProcess.class;
			}

			@Override
			public @NotNull List<ForwardingMode> forwardingModes() {
				return List.of(ForwardingMode.MODERN);
			}

			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				contexts.put(process.getName(), context);
			}
		};
		var scenario = scenario(null).toBuilder()
				.server(MinecraftServer.builder().name("second").platform("test").distribution(Distribution.remote("1.21.11", "1")).build())
				.proxy(MinecraftProxy.builder().name("proxy").platform("test").distribution(Distribution.remote("1", "1"))
						.server("server").defaultServer("server").build())
				.proxy(MinecraftProxy.builder().name("other").platform("test").distribution(Distribution.remote("1", "1"))
						.server("second").defaultServer("second").build())
				.build();
		String previous;
		try (var run = start(true, provider, scenario, this::library)) {
			previous = contexts.get("server").getForwarding().getSecret();
			assertNotNull(previous);
			assertEquals(previous, contexts.get("proxy").getForwarding().getSecret());
			assertNotEquals(previous, contexts.get("other").getForwarding().getSecret());
			assertEquals(contexts.get("other").getForwarding(), contexts.get("second").getForwarding());
			run.processes().restart("proxy");
			assertEquals(previous, contexts.get("proxy").getForwarding().getSecret());
		}
		try (var ignored = start(true, provider, scenario, this::library)) {
			assertNotEquals(previous, contexts.get("server").getForwarding().getSecret());
		}
	}

	@Test
	void cleansReplacementGenerationWhenAgentReconnectionFails() {
		AtomicInteger connections = new AtomicInteger();
		var provider = new TestPlatform() {
			@Override
			public PlatformAgentDescriptor platformAgent() {
				return PlatformAgentDescriptor.builder().entrypointClassName("fixture").build();
			}
		};
		var run = start(true, provider, scenario(null), this::library, (port, token, timeout) -> {
			if (connections.incrementAndGet() > 1) throw new IllegalStateException("agent unavailable");
			return agent(() -> { });
		});
		var original = run.processes().server("server");
		assertThrows(IllegalStateException.class, () -> run.processes().restart("server"));
		var replacement = run.processes().server("server");
		assertNotSame(original, replacement);
		run.close();
		assertEquals(ProcessState.STOPPED, original.state());
		assertEquals(ProcessState.STOPPED, replacement.state());
		assertTrue(Files.exists(replacement.workDirectory()));
	}

	@Test
	void engineProcessesServeOneScenarioAfterAnotherAndStopWithTheEngine() {
		Map<String, PlatformContext> contexts = new LinkedHashMap<>();
		ScenarioEngine engine = engine(forwarding(contexts));
		UUID server;
		UUID proxy;
		String secret;
		RunningProcess kept;
		try (var first = engine.start(network(ProcessLifetime.ENGINE))) {
			kept = first.processes().server("server");
			server = kept.executionId();
			proxy = first.processes().proxy("proxy").executionId();
			secret = contexts.get("server").getForwarding().getSecret();
			assertEquals(secret, contexts.get("proxy").getForwarding().getSecret());
			assertEquals(kept.address(), contexts.get("proxy").getProcessAddresses().get("server"));
			assertEquals(List.of("server", "proxy"), first.processes().all().stream().map(RunningProcess::name).toList());
		}
		assertEquals(ProcessState.READY, kept.state(), "An engine process outlives its scenario");

		try (var second = engine.start(network(ProcessLifetime.ENGINE))) {
			assertEquals(server, second.processes().server("server").executionId());
			assertNotEquals(proxy, second.processes().proxy("proxy").executionId());
			assertEquals(secret, contexts.get("proxy").getForwarding().getSecret(), "A later proxy adopts the secret its server runs with");
		}

		engine.close();
		assertEquals(ProcessState.STOPPED, kept.state());
	}

	@Test
	void scenariosRunningAtOnceEachGetTheirOwnEngineProcesses() {
		ScenarioEngine engine = engine(forwarding(new LinkedHashMap<>()));
		UUID first;
		UUID second;
		try (var one = engine.start(network(ProcessLifetime.ENGINE)); var other = engine.start(network(ProcessLifetime.ENGINE))) {
			first = one.processes().server("server").executionId();
			second = other.processes().server("server").executionId();
			assertNotEquals(first, second);
		}

		try (var later = engine.start(network(ProcessLifetime.ENGINE))) {
			assertTrue(Set.of(first, second).contains(later.processes().server("server").executionId()));
		}
	}

	@Test
	void aFailedScenarioStopsItsEngineProcessesAndKeepsTheirWorkspace() {
		ScenarioEngine engine = engine(forwarding(new LinkedHashMap<>()));
		var failed = engine.start(network(ProcessLifetime.ENGINE));
		RunningProcess kept = failed.processes().server("server");
		failed.finish(false);

		assertEquals(ProcessState.STOPPED, kept.state());
		assertTrue(Files.isDirectory(kept.workDirectory()), "The workspace of a failed run is kept for diagnosis");
		try (var next = engine.start(network(ProcessLifetime.ENGINE))) {
			assertNotEquals(kept.executionId(), next.processes().server("server").executionId());
		}
	}

	@Test
	void aChangedDeclarationStartsItsOwnEngineProcess() {
		ScenarioEngine engine = engine(forwarding(new LinkedHashMap<>()));
		UUID original;
		try (var first = engine.start(network(ProcessLifetime.ENGINE))) {
			original = first.processes().server("server").executionId();
		}

		AnvilScenario changed = network(ProcessLifetime.ENGINE);
		changed = changed.toBuilder().clearServers()
				.server(changed.getServers().getFirst().toBuilder().setting("difficulty", "hard").build()).build();
		try (var second = engine.start(changed)) {
			assertNotEquals(original, second.processes().server("server").executionId());
		}
	}

	@Test
	void aScenarioCannotChangeAnEngineProcessAfterItFinished() {
		ScenarioEngine engine = engine(forwarding(new LinkedHashMap<>()));
		var context = engine.start(network(ProcessLifetime.ENGINE));
		context.close();

		assertThrows(IllegalStateException.class, () -> context.processes().stop("server"));
		assertThrows(IllegalStateException.class, () -> context.processes().restart("server"));
	}

	/**
	 * A server behind a proxy, both on the test platform; only the server takes the given lifetime.
	 */
	private AnvilScenario network(ProcessLifetime lifetime) {
		return AnvilScenario.builder()
				.name("network")
				.entrypoint("proxy")
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofSeconds(5)).build())
				.server(MinecraftServer.builder().name("server").platform("test").lifetime(lifetime)
						.distribution(Distribution.remote("1.21.11", "test")).build())
				.proxy(MinecraftProxy.builder().name("proxy").platform("test").distribution(Distribution.remote("1", "1"))
						.server("server").defaultServer("server").build())
				.build();
	}

	/**
	 * A platform for servers and proxies with modern forwarding that records what each process is configured with.
	 */
	private TestPlatform forwarding(Map<String, PlatformContext> contexts) {
		return new TestPlatform() {
			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() {
				return MinecraftProcess.class;
			}

			@Override
			public @NotNull List<ForwardingMode> forwardingModes() {
				return List.of(ForwardingMode.MODERN);
			}

			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				contexts.put(process.getName(), context);
			}
		};
	}

	private AnvilScenario withWorkspace(AnvilScenario scenario) {
		return scenario.toBuilder().clearServers().servers(scenario.getServers().stream().map(server -> server.toBuilder()
				.workspace(WorkspacePlan.builder()
						.cleanup(WorkspaceCleanup.builder().path(Path.of("failure-marker")).phase(CleanupPhase.ON_FAILURE).build())
						.cache(WorkspaceCache.builder().path(Path.of("data")).key(server.getName()).policy(CachePolicy.SAVE_ONLY).build())
						.build()).build()).toList()).build();
	}

	private AgentClient agent(Runnable close) {
		return agent(close, command -> { });
	}

	private AgentClient agent(Runnable close, Consumer<String> commands) {
		return new AgentClient() {
			@Override
			public boolean available() {
				return true;
			}

			@Override
			public <T> T request(@NotNull String operation, @NotNull Object arguments, @NotNull Class<T> responseType) {
				if (!operation.equals(AgentOperations.COMMAND.getName())) throw new UnsupportedOperationException(operation);
				commands.accept(((AgentCommandRequest) arguments).getCommand());

				return responseType.cast(AgentCommandResponse.builder().accepted(true).build());
			}

			@Override
			public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
				return Optional.empty();
			}

			@Override
			public boolean executeCommand(@NotNull String command) {
				commands.accept(command);
				return true;
			}

			@Override
			public void close() {
				close.run();
			}
		};
	}

	private void assertNoRunDirectories() {
		try (var paths = Files.list(temporary.resolve("work/test"))) {
			assertEquals(0, paths.count());
		} catch (IOException failure) {
			throw new AssertionError(failure);
		}
	}

	private ScenarioSession start(boolean keepFailed, PlatformProvider provider, AnvilScenario scenario, Supplier<ProtocolLibrary> library) {
		return start(keepFailed, provider, scenario, library, (port, token, timeout) -> {
			throw new AssertionError("No agent is declared");
		});
	}

	private ScenarioSession start(
			boolean keepFailed,
			PlatformProvider provider,
			AnvilScenario scenario,
			Supplier<ProtocolLibrary> library,
			AgentConnectionProvider connections
	) {
		var protocol = new ProtocolLibraryProvider() {
			@Override
			public @NotNull String id() { return "test"; }

			@Override
			public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
				return List.of();
			}

			@Override
			public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
				return library.get();
			}
		};

		return start(keepFailed, provider, scenario, List.of(protocol), connections);
	}

	private ScenarioSession start(
			boolean keepFailed,
			PlatformProvider provider,
			AnvilScenario scenario,
			List<ProtocolLibraryProvider> libraries,
			AgentConnectionProvider connections
	) {
		return (ScenarioSession) engine(keepFailed, provider, libraries, connections).start(scenario);
	}

	/**
	 * An engine without players or agents, for scenarios that are started one after another on it.
	 */
	private ScenarioEngine engine(PlatformProvider provider) {
		return engine(true, provider, List.of(), (port, token, timeout) -> {
			throw new AssertionError("No agent is declared");
		});
	}

	private ScenarioEngine engine(
			boolean keepFailed,
			PlatformProvider provider,
			List<ProtocolLibraryProvider> libraries,
			AgentConnectionProvider connections
	) {
		EngineOptions options = EngineOptions.builder()
				.eulaAccepted(true)
				.workDirectory(temporary.resolve("work"))
				.cacheDirectory(temporary.resolve("cache"))
				.keepFailedWorkspaces(keepFailed)
				.javaSelection(JavaSelection.builder().requirement(JavaRequirement.builder().build()).build())
				.processTimeouts(ProcessTimeouts.builder().shutdown(Duration.ofSeconds(2)).build())
				.build();
		options = EngineDefaults.resolve(options);
		var provisioning = new ProvisioningServices(options);
		var platforms = new DefaultPlatformPlanner(options, Map.of(provider.id(), provider),
				new ArtifactPlatformSource(provisioning.getArtifacts()), descriptor -> executable);
		var processes = ProcessLauncher.builder()
				.options(options)
				.processes(new ManagedProcessService(Map.of("local", new LocalExecutionProvider())))
				.workspaces(provisioning.getWorkspaces())
				.platforms(platforms)
				.connections(connections)
				.javaRuntime(new JavaExecutionRuntime(provisioning.getJava()))
				.imageLocks(new CacheImageLocks(provisioning.getCache()))
				.ports(new PortSelection())
				.build();
		var players = new DefaultPlayerService(new ProtocolLibraryRegistry(libraries), options, provisioning.getArtifacts()::obtain);
		var retained = new RetainedProcesses(processes);
		var engine = new AnvilEngineBuilder(new DefaultScenarioFactory(platforms, processes, players, retained)).options(options).extension(registration -> {
			registration.own(provisioning);
			registration.own(players);
			registration.own(retained);
		}).build();
		owned.add(engine);

		return engine;
	}

	private AnvilScenario scenario(ScenarioHook hook) {
		return AnvilScenario.builder()
				.name("test")
				.entrypoint("server")
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofSeconds(5)).build())
				.server(MinecraftServer.builder()
						.name("server")
						.platform("test")
						.distribution(Distribution.remote("1.21.11", "test"))
						.build())
				.setupHook(hook)
				.build();
	}

	private ProtocolLibrary library() {
		return new ProtocolLibrary() {
			@Override
			public @NotNull String id() {
				return "test";
			}

			@Override
			public @NotNull List<ProtocolRelease> releases() {
				return List.of();
			}

			@Override
			public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void close() {
				libraryClosed = true;
			}
		};
	}

	private ProtocolLibraryProvider releasedLibrary(String id, String version) {
		MinecraftVersion parsed = MinecraftVersion.parse(version);
		ProtocolRelease release = ProtocolRelease.builder().libraryVersion(id + "-" + version).minecraftVersion(parsed)
				.verifiedVersion(parsed).protocolNumber(774).javaVersion(21).build();
		return new ProtocolLibraryProvider() {
			@Override
			public @NotNull String id() { return id; }

			@Override
			public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
				return List.of(release);
			}

			@Override
			public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
				throw new AssertionError("No player is created");
			}
		};
	}

	/**
	 * Capability provider of the "unreached" library whose dependency no provider supplies.
	 */
	public static final class UnreachedLibraryCapabilityProvider implements ProtocolPlayerCapabilityProvider<UnreachedCapability> {
		@Override
		public @NotNull Set<String> supportedLibraries() {
			return Set.of("unreached");
		}

		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("unreached").requiredCapability(MissingCapability.class).build();
		}

		@Override
		public @NotNull Class<UnreachedCapability> capability() {
			return UnreachedCapability.class;
		}

		@Override
		public @NotNull UnreachedCapability create(@NotNull ProtocolPlayerCapabilityContext context) {
			throw new AssertionError("Validation must fail before creation");
		}
	}

	private interface UnreachedCapability extends PlayerCapability { }

	private interface MissingCapability extends PlayerCapability { }

	private class TestPlatform implements PlatformProvider {
		@Override
		public @NotNull String id() {
			return "test";
		}

		@Override
		public @NotNull Class<? extends MinecraftProcess> configurationType() {
			return MinecraftServer.class;
		}

		@Override
		public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			return ResolvedDistribution.builder().jar(executable).description("local fixture").build();
		}

		@Override
		public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException { }

		@Override
		public @NotNull Pattern readinessPattern() {
			return Pattern.compile("READY");
		}

		@Override
		public @NotNull URL versionData() {
			return DefaultScenarioFactoryIntegrationTest.class.getResource("/me/whereareiam/anvil/launcher/assembly/fixture-versions.toml");
		}
	}

}
