package me.whereareiam.anvil.launcher.assembly.execution;

import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.client.ScenarioAgentDirectory;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.environment.execution.local.LocalExecutionProvider;
import me.whereareiam.anvil.environment.execution.managed.ManagedProcessService;
import me.whereareiam.anvil.launcher.assembly.provisioning.ArtifactPlatformSource;
import me.whereareiam.anvil.launcher.assembly.provisioning.ProvisioningServices;
import me.whereareiam.anvil.launcher.config.EngineDefaults;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
				.startupTimeout(Duration.ofSeconds(5)).build();
		var options = EngineDefaults.resolve(EngineOptions.builder().workDirectory(directory.resolve("work"))
				.cacheDirectory(directory.resolve("cache")).eulaAccepted(true).stopTimeout(Duration.ofSeconds(2)).build());
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
				if (borrowed.get() != null) assertThrows(AgentException.class, () -> borrowed.get().executeCommand("during"));
			}
			@Override
			public @NotNull Pattern readinessPattern() { return Pattern.compile("READY"); }
			@Override
			public int minimumJavaVersion(@NotNull MinecraftProcess process) { return 21; }
			@Override
			public PlatformAgentDescriptor platformAgent() { return PlatformAgentDescriptor.builder().entrypointClassName("fixture").build(); }
		};
		try (var provisioning = new ProvisioningServices(options)) {
			var platforms = new DefaultPlatformPlanner(options, Map.of(provider.id(), provider),
					new ArtifactPlatformSource(provisioning.getArtifacts()), descriptor -> executable);
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
					.javaRuntime(new JavaExecutionRuntime(provisioning.getJava()))
					.imageLocks(new CacheImageLocks(provisioning.getCache()))
					.build();
			var agents = new ScenarioAgentDirectory();
			try (var group = launcher.start(platforms.plan(scenario), agents)) {
				var original = group.server("server");
				borrowed.set(agents.require("server"));
				borrowed.get().executeCommand("before");
				Files.writeString(asset, "second");
				var replacement = group.restart("server");
				assertSame(borrowed.get(), agents.require("server"));
				borrowed.get().executeCommand("after");
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

	private AgentClient agent(int generation, List<String> calls) {
		return new AgentClient() {
			@Override
			public <T> T request(@NotNull String operation, @NotNull Object args, @NotNull Class<T> response) { throw new UnsupportedOperationException(); }
			@Override
			public @NotNull Optional<AgentIdentity> identity(@NotNull String username) { return Optional.empty(); }
			@Override
			public boolean executeCommand(@NotNull String command) { calls.add(generation + ":" + command); return true; }
			@Override
			public void close() { calls.add(generation + ":close"); }
		};
	}
}
