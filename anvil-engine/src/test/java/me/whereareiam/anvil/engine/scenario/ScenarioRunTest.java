package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.scenario.AnvilContext;
import me.whereareiam.anvil.api.scenario.ScenarioHook;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.engine.EngineDefaults;
import me.whereareiam.anvil.engine.provisioning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.provisioning.java.JavaExecutables;
import me.whereareiam.anvil.engine.scenario.process.LocalProcessFixture;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioRunTest {
	@TempDir
	Path temporary;

	private Path executable;
	private boolean backendClosed;

	@BeforeEach
	void buildLocalProcessFixture() throws Exception {
		executable = LocalProcessFixture.jar(temporary);
	}

	@Test
	void transfersOwnershipAfterSetupAndRetainsTheEngineOwnedBackend() throws Exception {
		var context = start(true, new TestPlatform(), scenario(anvil -> {
			assertEquals(ProcessState.READY, anvil.processes().server("server").state());
		}), this::backend);
		var process = context.processes().server("server");
		Path run = process.workDirectory().getParent();
		assertTrue(Files.isDirectory(run));
		context.close();

		assertEquals(ProcessState.STOPPED, process.state());
		assertFalse(Files.exists(run));
		assertFalse(backendClosed);
	}

	@Test
	void rollsBackAssertionFailuresAndRetainsFailedWorkspace() {
		AtomicReference<AnvilContext> captured = new AtomicReference<>();
		AssertionError failure = new AssertionError("setup assertion");
		var scenario = scenario(context -> {
			captured.set(context);
			throw failure;
		});

		assertSame(failure, assertThrows(AssertionError.class,
				() -> start(true, new TestPlatform(), scenario, this::backend)));
		var process = captured.get().processes().server("server");
		assertEquals(ProcessState.STOPPED, process.state());
		assertTrue(Files.exists(process.workDirectory()));
		assertFalse(backendClosed);
	}

	@Test
	void reportsHookFailureWithScenarioContextAndHonorsFailedWorkspaceRemoval() {
		AtomicReference<AnvilContext> captured = new AtomicReference<>();
		IOException cause = new IOException("setup failed");
		var scenario = scenario(context -> {
			captured.set(context);
			throw cause;
		});

		ScenarioStartupException failure = assertThrows(ScenarioStartupException.class,
				() -> start(false, new TestPlatform(), scenario, this::backend));
		assertEquals("test", failure.getScenarioName());
		assertSame(cause, failure.getCause());
		assertFalse(Files.exists(captured.get().processes().server("server").workDirectory().getParent()));
	}

	@Test
	void validatesBeforeCreatingBackendOrWorkspace() {
		var invalid = scenario(null).toBuilder().entrypoint("missing").build();
		assertThrows(ScenarioValidationException.class, () -> start(true, new TestPlatform(), invalid, () -> {
			throw new AssertionError("Backend must not be created before validation");
		}));
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
				() -> start(false, provider, scenario(null), this::backend)));
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
				() -> start(false, provider, scenario(null), this::backend));
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
		try (var run = start(true, provider, scenario, this::backend)) {
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
			assertSame(configurations.get(1), configurations.get(2), "Restart reuses the prepared provider context");
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
		var run = start(true, provider, scenario, this::backend);
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
		var run = start(true, provider, scenario, this::backend, (port, token, timeout) -> {
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
		assertSame(first, assertThrows(AssertionError.class, run::close));
		assertArrayEquals(new Throwable[]{second}, first.getSuppressed());
		assertEquals(List.of("agent-1", "agent-2"), closed);
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
		try (var run = start(true, provider, scenario, this::backend)) {
			previous = contexts.get("server").getForwarding().getSecret();
			assertNotNull(previous);
			assertEquals(previous, contexts.get("proxy").getForwarding().getSecret());
			assertNotEquals(previous, contexts.get("other").getForwarding().getSecret());
			assertEquals(contexts.get("other").getForwarding(), contexts.get("second").getForwarding());
			run.processes().restart("proxy");
			assertEquals(previous, contexts.get("proxy").getForwarding().getSecret());
		}
		try (var ignored = start(true, provider, scenario, this::backend)) {
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
		var run = start(true, provider, scenario(null), this::backend, (port, token, timeout) -> {
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

	private AnvilScenario withWorkspace(AnvilScenario scenario) {
		return scenario.toBuilder().clearServers().servers(scenario.getServers().stream().map(server -> server.toBuilder()
				.workspace(WorkspacePlan.builder()
						.cleanup(WorkspaceCleanup.builder().path(Path.of("failure-marker")).phase(CleanupPhase.ON_FAILURE).build())
						.cache(WorkspaceCache.builder().path(Path.of("data")).key(server.getName()).policy(CachePolicy.SAVE_ONLY).build())
						.build()).build()).toList()).build();
	}

	private AgentClient agent(Runnable close) {
		return new AgentClient() {
			@Override
			public <T> T request(@NotNull String operation, @NotNull Object arguments, @NotNull Class<T> responseType) {
				throw new UnsupportedOperationException();
			}

			@Override
			public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
				return Optional.empty();
			}

			@Override
			public boolean executeCommand(@NotNull String command) {
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

	private ScenarioRun start(boolean keepFailed, PlatformProvider provider, AnvilScenario scenario, Supplier<ProtocolBackend> backend) {
		return start(keepFailed, provider, scenario, backend, (port, token, timeout) -> {
			throw new AssertionError("No agent is declared");
		});
	}

	private ScenarioRun start(
			boolean keepFailed,
			PlatformProvider provider,
			AnvilScenario scenario,
			Supplier<ProtocolBackend> backend,
			AgentConnectionProvider connections
	) {
		EngineOptions options = EngineOptions.builder()
				.eulaAccepted(true)
				.workDirectory(temporary.resolve("work"))
				.cacheDirectory(temporary.resolve("cache"))
				.keepFailedWorkspaces(keepFailed)
				.defaultJavaExecutable(JavaExecutables.current())
				.stopTimeout(Duration.ofSeconds(2))
				.build();
		var artifacts = new ScenarioArtifactResolver(Map.of(), entrypoint -> {
			return executable;
		});

		ProtocolPlayerComposer composer = (player, services, destroyed) -> {
			throw new AssertionError("No players are created");
		};

		return ScenarioRun.builder()
				.options(options)
				.scenario(scenario)
				.providers(Map.of(provider.id(), provider))
				.playerComposer(composer)
				.artifacts(artifacts)
				.agentConnections(connections)
				.backend(backend)
				.start();
	}

	private AnvilScenario scenario(ScenarioHook hook) {
		return AnvilScenario.builder()
				.name("test")
				.entrypoint("server")
				.startupTimeout(Duration.ofSeconds(5))
				.server(MinecraftServer.builder()
						.name("server")
						.platform("test")
						.distribution(Distribution.remote("1.21.11", "test"))
						.build())
				.setupHook(hook)
				.build();
	}

	private ProtocolBackend backend() {
		return new ProtocolBackend() {
			@Override
			public @NotNull String id() {
				return "test";
			}

			@Override
			public @NotNull Collection<ProtocolSupport> supportedProtocols() {
				return List.of();
			}

			@Override
			public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void close() {
				backendClosed = true;
			}
		};
	}

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
		public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) { }

		@Override
		public @NotNull Pattern readinessPattern() {
			return Pattern.compile("READY");
		}

		@Override
		public int minimumJavaVersion(@NotNull MinecraftProcess process) {
			return 21;
		}
	}

}
