package me.whereareiam.anvil.launcher.assembly;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.ImageLaunch;
import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.execution.local.process.LocalProcess;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.engine.config.EngineDefaults;
import me.whereareiam.anvil.environment.execution.local.LocalExecutionProvider;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.environment.execution.managed.process.PortSelection;
import me.whereareiam.anvil.launcher.adapter.execution.ExecutionPlanMapper;
import me.whereareiam.anvil.launcher.adapter.provisioning.ArtifactPlatformSource;
import me.whereareiam.anvil.launcher.adapter.provisioning.ProvisionedPlatformJavaSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import me.whereareiam.anvil.workbench.local.LocalWorkbench;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ProcessLauncherIntegrationTest {
	@TempDir Path directory;

	@Test
	void retainsWorkspaceAndBorrowedAgentWhileEachGenerationGetsFreshCredentials() throws Exception {
		Path executable = FixtureArtifacts.process();
		Path asset = directory.resolve("source.txt");
		Files.writeString(asset, "first");
		var declaration = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.workspace(WorkspacePlan.builder().asset(WorkspaceAsset.builder()
						.source(AssetSource.path(asset)).target(Path.of("installed.txt")).build()).build())
				.build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("server").server(declaration)
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofSeconds(5)).build()).build();
		var options = EngineDefaults.resolve(EngineOptions.builder().workDirectory(directory.resolve("work"))
				.cacheDirectory(directory.resolve("cache")).eulaAccepted(true).processTimeouts(ProcessTimeouts.builder().shutdown(Duration.ofSeconds(2)).build()).build());
		List<String> calls = new ArrayList<>();
		List<String> tokens = new ArrayList<>();
		List<Integer> ports = new ArrayList<>();
		List<String> preparation = new ArrayList<>();
		AtomicReference<AgentClient> borrowed = new AtomicReference<>();
		AtomicInteger resolutions = new AtomicInteger();
		PlatformProvider provider = new PlatformProvider() {
			@Override
			public @NotNull String id() { return "test"; }
			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() { return MinecraftServer.class; }
			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
				assertEquals("first", Files.readString(context.getWorkDirectory().resolve("installed.txt")));
				preparation.add("resolve");
				resolutions.incrementAndGet();
				return ResolvedDistribution.builder().jar(executable).description("fixture").build();
			}
			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				preparation.add("configure");
				if (borrowed.get() != null) assertThrows(AgentException.class, () -> borrowed.get().request("during", null, Boolean.class));
			}
			@Override
			public @NotNull Pattern readinessPattern() { return Pattern.compile("READY"); }
			@Override
			public @NotNull URL versionData() {
				return ProcessLauncherIntegrationTest.class.getResource("/me/whereareiam/anvil/launcher/assembly/fixture-versions.toml");
			}
			@Override
			public PlatformAgentDescriptor platformAgent() { return PlatformAgentDescriptor.builder().entrypointClassName("fixture").build(); }
		};
		try (var workbench = new LocalWorkbench(options, Map.of("local", new LocalExecutionProvider()))) {
			var platforms = new DefaultPlatformPlanner(options, Map.of(provider.id(), provider),
					new ArtifactPlatformSource(workbench.artifacts()), descriptor -> executable,
					new ProvisionedPlatformJavaSource(workbench.java()));
			var launcher = ProcessLauncher.builder()
					.options(options)
					.processes(new ManagedProcessService(Map.of("local", new LocalExecutionProvider())))
					.workspaces(workbench.workspaces())
					.platforms(platforms)
					.connections((port, token, timeout) -> {
						ports.add(port);
						tokens.add(token);

						return agent(tokens.size(), calls);
					})
					.plans(new ExecutionPlanMapper(options, workbench))
					.build();
			var agents = new ScenarioAgentDirectory();
			try (var group = launcher.prepare(platforms.plan(scenario), agents, null, null, Map.of())) {
				assertEquals(List.of("resolve"), preparation);
				assertTrue(tokens.isEmpty());
				assertTrue(group.all().isEmpty());
				group.startAll();
				var original = group.server("server");
				borrowed.set(agents.require("server"));
				borrowed.get().request("before", null, Boolean.class);
				Files.writeString(asset, "second");
				var replacement = group.restart("server");
				assertSame(borrowed.get(), agents.require("server"));
				borrowed.get().request("after", null, Boolean.class);
				assertEquals(ProcessState.STOPPED, original.state());
				assertEquals(ProcessState.READY, replacement.state());
				assertEquals(original.address(), replacement.address());
				assertEquals(1, resolutions.get());
				assertNotEquals(tokens.get(0), tokens.get(1));
				assertEquals(ports.get(0), ports.get(1));
				assertNotEquals(original.address().getPort(), ports.getFirst());
				assertEquals("first", Files.readString(replacement.workDirectory().resolve("installed.txt")));
			}
		}
		assertEquals(List.of("1:before", "1:close", "2:after", "2:close"), calls);
		assertEquals(List.of("resolve", "configure", "configure"), preparation);
	}

	@Test
	void fillsTheAddressOfAProcessListenerIntoTextAssets() throws Exception {
		Path executable = FixtureArtifacts.process();
		var declaration = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.listener("link")
				.workspace(WorkspacePlan.builder().asset(WorkspaceAsset.builder()
						.source(AssetSource.text("${anvil.process.server.host}:${anvil.process.server.listener.link}"))
						.target(Path.of("link.txt")).build()).build())
				.build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("server").server(declaration).build();
		var options = EngineDefaults.resolve(EngineOptions.builder().workDirectory(directory.resolve("work"))
				.cacheDirectory(directory.resolve("cache")).eulaAccepted(true).build());
		try (var workbench = new LocalWorkbench(options, Map.of("local", new LocalExecutionProvider()))) {
			var platforms = new DefaultPlatformPlanner(options, Map.of("test", fixtureProvider(executable)),
					new ArtifactPlatformSource(workbench.artifacts()), descriptor -> executable,
					new ProvisionedPlatformJavaSource(workbench.java()));
			var launcher = ProcessLauncher.builder()
					.options(options)
					.processes(new ManagedProcessService(Map.of("local", new LocalExecutionProvider())))
					.workspaces(workbench.workspaces())
					.platforms(platforms)
					.connections((port, token, timeout) -> agent(1, new ArrayList<>()))
					.plans(new ExecutionPlanMapper(options, workbench))
					.build();
			try (var group = launcher.prepare(platforms.plan(scenario), new ScenarioAgentDirectory(), null, null, Map.of())) {
				group.startAll();
				var server = group.server("server");
				assertEquals("127.0.0.1:" + server.listener("link").getPort(),
						Files.readString(server.workDirectory().resolve("link.txt")));
			}
		}
	}

	@Test
	void runsAContainerWithItsProviderBeforeTheServerAndNamesEachToTheOther() throws Exception {
		Path executable = FixtureArtifacts.process();
		var declaration = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.workspace(WorkspacePlan.builder().asset(WorkspaceAsset.builder()
						.source(AssetSource.text("${anvil.process.redis.host}:${anvil.process.redis.port}"))
						.target(Path.of("redis.txt")).build()).build())
				.build();
		var container = ContainerProcess.builder().name("redis").image("redis:7.2-alpine").port(6379)
				.environment("SERVER", "${anvil.process.server.host}:${anvil.process.server.port}")
				.readyLog("READY")
				.executionProviderId("images")
				.build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("server").server(declaration).container(container).build();
		var options = EngineDefaults.resolve(EngineOptions.builder().workDirectory(directory.resolve("work"))
				.cacheDirectory(directory.resolve("cache")).eulaAccepted(true).build());
		var images = new ShellImages();
		try (var workbench = new LocalWorkbench(options, Map.of("local", new LocalExecutionProvider()))) {
			var platforms = new DefaultPlatformPlanner(options, Map.of("test", fixtureProvider(executable)),
					new ArtifactPlatformSource(workbench.artifacts()), descriptor -> executable,
					new ProvisionedPlatformJavaSource(workbench.java()));
			var launcher = ProcessLauncher.builder()
					.options(options)
					.processes(new ManagedProcessService(Map.of("local", new LocalExecutionProvider(), "images", images)))
					.workspaces(workbench.workspaces())
					.platforms(platforms)
					.connections((port, token, timeout) -> agent(1, new ArrayList<>()))
					.plans(new ExecutionPlanMapper(options, workbench))
					.build();
			try (var group = launcher.prepare(platforms.plan(scenario), new ScenarioAgentDirectory(), null, null, Map.of())) {
				group.startAll();
				var redis = group.container("redis");
				var server = group.server("server");

				assertEquals("127.0.0.1:" + redis.port(6379).getPort(), Files.readString(server.workDirectory().resolve("redis.txt")));
				assertEquals(server.address().getHostString() + ":" + server.address().getPort(), images.environment.get("SERVER"));
				assertEquals(1, images.started.get());
			}
		}
	}

	/**
	 * Runs a container's image as a shell that prints its readiness line, as an execution provider for images would.
	 */
	private static final class ShellImages implements ExecutionProvider {
		private final PortSelection ports = new PortSelection();
		private final AtomicInteger started = new AtomicInteger();
		private volatile Map<String, String> environment = Map.of();

		@Override
		public @NotNull String id() { return "images"; }
		@Override
		public boolean preservesClientAddress() { return true; }
		@Override
		public boolean connectsEnvironments() { return true; }
		@Override
		public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
			return new ExecutionSession() {
				@Override
				public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
					assertNotNull(request.getImage());
					var address = new InetSocketAddress("127.0.0.1", ports.reserve("127.0.0.1"));
					return new ProcessTarget() {
						@Override
						public @NotNull InetSocketAddress address() { return address; }
						@Override
						public @NotNull InetSocketAddress peerAddress() { return InetSocketAddress.createUnresolved("redis-alias", 6379); }
						@Override
						public @NotNull String bindAddress() { return "0.0.0.0"; }
						@Override
						public @NotNull InetSocketAddress agentAddress() { throw new UnsupportedOperationException(); }
						@Override
						public int agentPort() { throw new UnsupportedOperationException(); }
						@Override
						public @NotNull String agentBindAddress() { throw new UnsupportedOperationException(); }
						@Override
						public @NotNull Map<String, InetSocketAddress> listeners() { return Map.of("6379", address); }
						@Override
						public @NotNull Map<String, InetSocketAddress> peerListeners() { return Map.of("6379", peerAddress()); }
						@Override
						public @NotNull ProcessExecution start(@NotNull ProcessLaunch launch) {
							environment = ((ImageLaunch) launch).getEnvironment();
							started.incrementAndGet();
							try {
								return new LocalProcess(new ProcessBuilder("sh", "-c", "echo READY; exec sleep 60").redirectErrorStream(true).start());
							} catch (IOException failure) {
								throw new UncheckedIOException(failure);
							}
						}
						@Override
						public void close() {
							ports.release(address.getPort());
						}
					};
				}

				@Override
				public void close() {
				}
			};
		}
	}

	private static PlatformProvider fixtureProvider(Path executable) {
		return new PlatformProvider() {
			@Override
			public @NotNull String id() { return "test"; }
			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() { return MinecraftServer.class; }
			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
				return ResolvedDistribution.builder().jar(executable).description("fixture").build();
			}
			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			}
			@Override
			public @NotNull Pattern readinessPattern() { return Pattern.compile("READY"); }
			@Override
			public @NotNull URL versionData() {
				return ProcessLauncherIntegrationTest.class.getResource("/me/whereareiam/anvil/launcher/assembly/fixture-versions.toml");
			}
		};
	}

	private AgentClient agent(int generation, List<String> calls) {
		return new AgentClient() {
			@Override
			public boolean available() {
				return true;
			}

			@Override
			public <T> T request(@NotNull String operation, Object args, @NotNull Class<T> response) {
				calls.add(generation + ":" + operation);
				return response.cast(true);
			}
			@Override
			public void close() { calls.add(generation + ":close"); }
		};
	}
}
