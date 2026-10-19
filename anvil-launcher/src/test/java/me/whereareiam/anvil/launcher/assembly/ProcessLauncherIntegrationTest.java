package me.whereareiam.anvil.launcher.assembly;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.service.ServiceContainer;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.service.ServiceCommandResult;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.api.type.ServiceState;
import me.whereareiam.anvil.engine.config.EngineDefaults;
import me.whereareiam.anvil.environment.execution.local.LocalExecutionProvider;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.environment.execution.managed.process.PortSelection;
import me.whereareiam.anvil.environment.service.api.ServiceInstance;
import me.whereareiam.anvil.environment.service.api.ServiceProvider;
import me.whereareiam.anvil.environment.service.api.ServiceSession;
import me.whereareiam.anvil.environment.service.api.model.ServiceContext;
import me.whereareiam.anvil.launcher.adapter.execution.CacheImageLocks;
import me.whereareiam.anvil.launcher.adapter.execution.ExecutionPlanMapper;
import me.whereareiam.anvil.launcher.adapter.execution.JavaExecutionRuntime;
import me.whereareiam.anvil.launcher.adapter.provisioning.ArtifactPlatformSource;
import me.whereareiam.anvil.launcher.adapter.provisioning.ProvisionedPlatformJavaSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.planning.DefaultPlatformPlanner;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
		try (var provisioning = new ProvisioningServices(options)) {
			var platforms = new DefaultPlatformPlanner(options, Map.of(provider.id(), provider),
					new ArtifactPlatformSource(provisioning.getArtifacts()), descriptor -> executable,
					new ProvisionedPlatformJavaSource(provisioning.getJava()));
			var reservations = new PortSelection();
			var launcher = ProcessLauncher.builder()
					.options(options)
					.processes(new ManagedProcessService(Map.of("local", new LocalExecutionProvider())))
					.workspaces(provisioning.getWorkspaces())
					.platforms(platforms)
					.connections((port, token, timeout) -> {
						ports.add(port);
						tokens.add(token);

						return agent(tokens.size(), calls);
					})
					.plans(new ExecutionPlanMapper(options, new JavaExecutionRuntime(provisioning.getJava()),
							new CacheImageLocks(provisioning.getCache()), reservations))
					.ports(reservations)
					.build();
			var agents = new ScenarioAgentDirectory();
			try (var group = launcher.prepare(platforms.plan(scenario), agents, null, null, Map.of(), launcher.services(List.of()))) {
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
	void startsServicesWithTheEnvironmentAndFillsServiceAndListenerAddressesIntoTextAssets() throws Exception {
		Path executable = FixtureArtifacts.process();
		var declaration = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.listener("link")
				.workspace(WorkspacePlan.builder()
						.asset(WorkspaceAsset.builder()
								.source(AssetSource.text("redis://${anvil.service.redis.host}:${anvil.service.redis.port}"))
								.target(Path.of("redis.txt")).build())
						.asset(WorkspaceAsset.builder()
								.source(AssetSource.text("${anvil.process.server.host}:${anvil.process.server.listener.link}"))
								.target(Path.of("link.txt")).build())
						.build())
				.build();
		var service = ServiceContainer.builder().name("redis").image("redis:7.2-alpine").port(6379).build();
		var scenario = AnvilScenario.builder().name("test").entrypoint("server").server(declaration).service(service).build();
		var options = EngineDefaults.resolve(EngineOptions.builder().workDirectory(directory.resolve("work"))
				.cacheDirectory(directory.resolve("cache")).eulaAccepted(true).build());
		List<String> calls = new ArrayList<>();
		PlatformProvider provider = new PlatformProvider() {
			@Override
			public @NotNull String id() { return "test"; }
			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() { return MinecraftServer.class; }
			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
				calls.add("resolve:" + Files.readString(context.getWorkDirectory().resolve("redis.txt")));
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
		try (var provisioning = new ProvisioningServices(options)) {
			var platforms = new DefaultPlatformPlanner(options, Map.of(provider.id(), provider),
					new ArtifactPlatformSource(provisioning.getArtifacts()), descriptor -> executable,
					new ProvisionedPlatformJavaSource(provisioning.getJava()));
			var reservations = new PortSelection();
			var launcher = ProcessLauncher.builder()
					.options(options)
					.processes(new ManagedProcessService(Map.of("local", new LocalExecutionProvider())))
					.workspaces(provisioning.getWorkspaces())
					.platforms(platforms)
					.connections((port, token, timeout) -> agent(1, new ArrayList<>()))
					.plans(new ExecutionPlanMapper(options, new JavaExecutionRuntime(provisioning.getJava()),
							new CacheImageLocks(provisioning.getCache()), reservations))
					.ports(reservations)
					.services(new RecordingServices(calls))
					.build();
			var services = launcher.services(scenario.getServices());
			try (var group = launcher.prepare(platforms.plan(scenario), new ScenarioAgentDirectory(), null, null, Map.of(), services)) {
				assertEquals("redis", services.get("redis").name());
				calls.add("prepared");

				group.startAll();
				var server = group.server("server");
				assertEquals("127.0.0.1:" + server.listener("link").getPort(),
						Files.readString(server.workDirectory().resolve("link.txt")));
			}
			assertTrue(services.all().isEmpty());
		}

		assertEquals(List.of("open:null", "start:redis", "resolve:redis://127.0.0.1:16379", "prepared", "close"), calls);
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

	private record RecordingServices(List<String> calls) implements ServiceProvider {
		@Override
		public @NotNull String id() {
			return "recording";
		}

		@Override
		public @NotNull ServiceSession open(@NotNull ServiceContext context) {
			calls.add("open:" + context.getNetwork());
			return new ServiceSession() {
				@Override
				public @NotNull ServiceInstance start(@NotNull ServiceContainer declaration) {
					calls.add("start:" + declaration.getName());
					return new HostService(declaration.getName());
				}

				@Override
				public void close() {
					calls.add("close");
				}
			};
		}
	}

	private record HostService(String name) implements ServiceInstance {
		@Override
		public @NotNull ServiceState state() {
			return ServiceState.RUNNING;
		}

		@Override
		public @NotNull InetSocketAddress address(int containerPort) {
			return new InetSocketAddress("127.0.0.1", 10000 + containerPort);
		}

		@Override
		public InetSocketAddress networkAddress(int containerPort) {
			return null;
		}

		@Override
		public void pause() {
		}

		@Override
		public void resume() {
		}

		@Override
		public void kill() {
		}

		@Override
		public void restart() {
		}

		@Override
		public @NotNull ServiceCommandResult execute(@NotNull String... command) {
			return new ServiceCommandResult(0, "");
		}

		@Override
		public @NotNull ServiceCommandResult execute(@NotNull Duration timeout, @NotNull String... command) {
			return new ServiceCommandResult(0, "");
		}
	}
}
