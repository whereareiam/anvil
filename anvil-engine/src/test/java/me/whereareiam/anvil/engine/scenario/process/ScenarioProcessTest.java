package me.whereareiam.anvil.engine.scenario.process;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.engine.EngineDefaults;
import me.whereareiam.anvil.engine.process.PortSelection;
import me.whereareiam.anvil.engine.provisioning.artifact.DownloadCache;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class ScenarioProcessTest {
	@TempDir
	Path directory;

	@Test
	void reusesPreparedAssetsAndRefreshesBorrowedAgentWithNewCredentials() throws Exception {
		Path jar = LocalProcessFixture.jar(directory);
		Path source = directory.resolve("asset.txt");
		Files.writeString(source, "first");
		EngineOptions options = EngineDefaults.resolve(EngineOptions.builder().workDirectory(directory)
				.cacheDirectory(directory.resolve("cache")).stopTimeout(Duration.ofSeconds(2)).build());
		MinecraftServer declaration = MinecraftServer.builder().name("server").platform("test")
				.distribution(Distribution.remote("1.21.11", "1")).build();
		AnvilScenario scenario = AnvilScenario.builder().name("test").entrypoint("server").server(declaration).build();
		PortSelection ports = new PortSelection();
		int listener = ports.select("127.0.0.1");
		PlatformContext context = PlatformContext.builder().scenario(scenario).cacheDirectory(options.getCacheDirectory())
				.workDirectory(directory.resolve("work")).workspaceGroupDirectory(directory).bindAddress("127.0.0.1")
				.port(listener).processPorts(Map.of("server", listener)).javaExecutable(options.getDefaultJavaExecutable())
				.artifactResolver(new DownloadCache()).forwarding(ForwardingConfiguration.builder().build()).build();
		List<String> calls = new ArrayList<>();
		List<String> tokens = new ArrayList<>();
		List<Integer> agentPorts = new ArrayList<>();
		AtomicInteger resolutions = new AtomicInteger();
		AtomicReference<AgentClient> borrowed = new AtomicReference<>();
		PlatformProvider provider = new PlatformProvider() {
			@Override
			public @NotNull String id() {
				return "test";
			}

			@Override
			public @NotNull Class<? extends MinecraftProcess> configurationType() {
				return MinecraftServer.class;
			}

			@Override
			public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext supplied) {
				resolutions.incrementAndGet();
				return ResolvedDistribution.builder().jar(jar).description("fixture").build();
			}

			@Override
			public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext supplied) {
				assertSame(context, supplied);
				if (borrowed.get() != null) assertThrows(AgentException.class, () -> borrowed.get().executeCommand("during"));
			}

			@Override
			public @NotNull Pattern readinessPattern() {
				return Pattern.compile("READY");
			}

			@Override
			public int minimumJavaVersion(@NotNull MinecraftProcess process) {
				return 21;
			}

			@Override
			public PlatformAgentDescriptor platformAgent() {
				return PlatformAgentDescriptor.builder().entrypointClassName("fixture").build();
			}
		};
		ScenarioProcess process = ScenarioProcess.builder().options(options).declaration(declaration).provider(provider)
				.context(context).ports(ports)
				.workspacePlan(WorkspacePlan.builder().asset(WorkspaceAsset.builder().source(AssetSource.path(source))
						.target(Path.of("installed.txt")).build()).build())
				.agentConnections((port, token, timeout) -> {
					agentPorts.add(port);
					tokens.add(token);
					int generation = tokens.size();
					return new AgentClient() {
						@Override
						public <T> T request(@NotNull String operation, @NotNull Object args, @NotNull Class<T> response) {
							throw new UnsupportedOperationException();
						}
						@Override
						public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
							return Optional.empty();
						}
						@Override
						public boolean executeCommand(@NotNull String command) {
							calls.add(generation + ":" + command); return true;
						}
						@Override
						public void close() {
							calls.add(generation + ":close");
						}
					};
				}).build();
		try {
			process.start();
			var original = process.current();
			borrowed.set(process.agent());
			borrowed.get().executeCommand("before");
			Files.writeString(source, "second");
			var replacement = process.restart();
			assertSame(borrowed.get(), process.agent());
			borrowed.get().executeCommand("after");
			assertEquals(ProcessState.STOPPED, original.state());
			assertEquals(ProcessState.READY, replacement.state());
			assertEquals(original.address(), replacement.address());
			assertEquals(1, resolutions.get());
			assertNotEquals(tokens.get(0), tokens.get(1));
			assertNotEquals(agentPorts.get(0), agentPorts.get(1));
			assertFalse(agentPorts.contains(listener));
			assertEquals("first", Files.readString(context.getWorkDirectory().resolve("installed.txt")));
		} finally {
			process.closeAgent();
			process.stopProcess();
			process.finish(false);
		}
		assertEquals(List.of("1:before", "1:close", "2:after", "2:close"), calls);
	}
}
