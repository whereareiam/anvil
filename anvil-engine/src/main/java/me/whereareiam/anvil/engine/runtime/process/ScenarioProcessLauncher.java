package me.whereareiam.anvil.engine.runtime.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.engine.model.EngineOptions;
import me.whereareiam.anvil.engine.provisioning.DownloadCache;
import me.whereareiam.anvil.engine.provisioning.TemurinRuntimeProvisioner;
import me.whereareiam.anvil.engine.provisioning.ProcessJavaResolver;
import me.whereareiam.anvil.engine.provisioning.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.provisioning.WorkspaceFiles;
import me.whereareiam.anvil.engine.provisioning.WorkspaceSession;
import me.whereareiam.anvil.engine.scenario.ScenarioResources;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prepares and starts one declared process through its platform provider.
 */
@RequiredArgsConstructor
public final class ScenarioProcessLauncher {
	private final @NotNull EngineOptions options;
	private final @NotNull ScenarioArtifactResolver artifacts;
	private final @NotNull AgentConnectionProvider agentConnections;
	private final DownloadCache downloads = new DownloadCache();
	private final WorkspaceFiles files = new WorkspaceFiles();

	public void start(
			@NotNull MinecraftProcess declaration,
			@NotNull PlatformProvider provider,
			@NotNull AnvilScenario scenario,
			@NotNull Path runDirectory,
			@NotNull Map<String, Integer> processPorts,
			@NotNull ForwardingConfiguration forwarding,
			@NotNull ScenarioResources resources
	) throws IOException {
		WorkspaceSession workspace = prepareWorkspace(declaration, provider, scenario, runDirectory);
		resources.addWorkspace(workspace);
		PlatformContext context = platformContext(declaration, provider, scenario, workspace, runDirectory, processPorts, forwarding);
		ResolvedDistribution distribution = provider.resolve(declaration, context);
		provider.configure(declaration, context);

		ManagedProcess process = createProcess(declaration, context);
		resources.addProcess(process);
		AgentEndpoint agent = provider.platformAgent() == null ? null : agentEndpoint(resources);
		process.start(command(declaration, provider, context, distribution),
				agent == null ? Map.of() : agent.environment(),
				provider.readinessPattern(), provider.stopCommand(), scenario.getStartupTimeout());
		if (agent == null)
			return;
		resources.addAgent(declaration.getName(), agentConnections.connect(agent.port, agent.token, Duration.ofSeconds(20)));
	}

	private WorkspaceSession prepareWorkspace(
			MinecraftProcess process,
			PlatformProvider provider,
			AnvilScenario scenario,
			Path runDirectory
	) {
		Path root = options.getWorkDirectory().toAbsolutePath().normalize();
		Path directory = process.getWorkspace().getMode() == WorkspaceMode.PERSISTENT
				? root.resolve(WorkspaceFiles.safeName(scenario.getName())).resolve("persistent")
						.resolve(WorkspaceFiles.safeName(process.getName()))
				: runDirectory.resolve(WorkspaceFiles.safeName(process.getName()));
		return WorkspaceSession.prepare(root, directory,
				artifacts.installAgent(process.getWorkspace(), provider.platformAgent()),
				provider.defaultCaches(process), cacheIdentity(process), options.getCacheDirectory(), files);
	}

	private PlatformContext platformContext(
			MinecraftProcess process,
			PlatformProvider provider,
			AnvilScenario scenario,
			WorkspaceSession workspace,
			Path runDirectory,
			Map<String, Integer> processPorts,
			ForwardingConfiguration forwarding
	) {
		Path java = new ProcessJavaResolver(options, new TemurinRuntimeProvisioner(downloads))
				.resolve(provider.minimumJavaVersion(process));
		return PlatformContext.builder()
				.scenario(scenario)
				.cacheDirectory(options.getCacheDirectory())
				.workDirectory(workspace.workspace())
				.workspaceGroupDirectory(runDirectory)
				.bindAddress(scenario.getBindAddress())
				.port(processPorts.get(process.getName()))
				.processPorts(Map.copyOf(processPorts))
				.javaExecutable(java)
				.eulaAccepted(options.isEulaAccepted())
				.artifactResolver(downloads)
				.forwarding(forwarding)
				.build();
	}

	private List<String> command(
			MinecraftProcess process,
			PlatformProvider provider,
			PlatformContext context,
			ResolvedDistribution distribution
	) {
		List<String> command = new ArrayList<>();
		command.add(context.getJavaExecutable().toString());
		command.add("-Xms256m");
		command.add("-Xmx" + process.getMemoryMegabytes() + "m");
		command.addAll(process.getJvmArguments());
		command.add("-jar");
		command.add(distribution.getJar().toAbsolutePath().toString());
		command.addAll(provider.programArguments(process));
		return command;
	}

	private ManagedProcess createProcess(MinecraftProcess process, PlatformContext context) {
		InetSocketAddress address = new InetSocketAddress(context.getBindAddress(), context.getPort());
		if (process instanceof MinecraftServer)
			return new ManagedServer(process.getName(), address, context.getWorkDirectory());
		return new ManagedProxy(process.getName(), address, context.getWorkDirectory());
	}

	private String cacheIdentity(MinecraftProcess process) {
		Distribution distribution = process.getDistribution();
		return process.getName() + "|" + process.getPlatform() + "|"
				+ Objects.toString(distribution.getVersion(), "") + "|" + Objects.toString(distribution.getBuild(), "") + "|"
				+ Objects.toString(distribution.getSha256(), "") + "|" + Objects.toString(distribution.getLocalJar(), "");
	}

	private AgentEndpoint agentEndpoint(ScenarioResources resources) {
		byte[] token = new byte[32];
		new SecureRandom().nextBytes(token);
		return new AgentEndpoint(resources.allocatePort("127.0.0.1"), HexFormat.of().formatHex(token));
	}

	@RequiredArgsConstructor
	private static final class AgentEndpoint {
		private final int port;
		private final String token;

		private Map<String, String> environment() {
			return Map.of("ANVIL_AGENT_PORT", Integer.toString(port), "ANVIL_AGENT_TOKEN", token);
		}
	}
}
