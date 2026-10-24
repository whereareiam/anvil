package me.whereareiam.anvil.platform.planning;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.platform.api.PlatformAgentSource;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformJavaSource;
import me.whereareiam.anvil.platform.api.PlatformPlanner;
import me.whereareiam.anvil.platform.api.PlatformPreparer;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import me.whereareiam.anvil.platform.api.model.PlatformRequest;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import me.whereareiam.anvil.platform.planning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.platform.planning.topology.ForwardingNegotiator;
import me.whereareiam.anvil.platform.planning.topology.ForwardingPlan;
import me.whereareiam.anvil.platform.planning.topology.ProcessTopology;
import me.whereareiam.anvil.platform.planning.validation.ProcessDeclarationValidator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns provider selection, artifact declarations, Java planning, forwarding, and platform preparation.
 * Execution supplies endpoints only after the scenario has a compatible platform plan.
 */
public final class DefaultPlatformPlanner implements PlatformPlanner, PlatformPreparer {
	private final EngineOptions options;
	private final JavaRequirementPlanner javaPlanner;
	private final Map<String, PlatformProvider> providers;
	private final PlatformArtifactSource downloads;
	private final ScenarioArtifactResolver artifacts;

	public DefaultPlatformPlanner(
			@NotNull EngineOptions options,
			@NotNull Map<String, PlatformProvider> providers,
			@NotNull PlatformArtifactSource downloads,
			@NotNull PlatformAgentSource agents,
			@NotNull PlatformJavaSource javaSources
	) {
		this.options = options;
		this.javaPlanner = new JavaRequirementPlanner(options, providers, javaSources, System.err);
		this.providers = Map.copyOf(providers);
		this.downloads = downloads;
		this.artifacts = new ScenarioArtifactResolver(options.getArtifacts(), agents);
	}

	@Override
	public @NotNull PlatformPlan plan(@NotNull AnvilScenario scenario) {
		AnvilScenario resolved = artifacts.resolve(ProcessDebugging.apply(scenario, options));
		var declarations = new ProcessDeclarationValidator().validate(resolved, providers);
		ForwardingPlan topology = new ForwardingNegotiator().negotiate(ProcessTopology.from(resolved), providers);
		Map<String, ForwardingConfiguration> forwarding = forwarding(topology);

		List<String> servers = resolved.getServers().stream().map(MinecraftProcess::getName).toList();
		PlatformPlan.PlatformPlanBuilder plan = PlatformPlan.builder().scenario(resolved);
		declarations.forEach((name, declaration) -> {
			PlatformProvider provider = providers.get(declaration.getPlatform());
			var agent = provider.platformAgent();
			boolean proxy = !(declaration instanceof MinecraftServer);
			JavaRequirementPlanner.PlannedJava planned = javaPlanner.plan(resolved, declaration);
			plan.process(name, ProcessPlan.builder()
					.declaration(declaration)
					.javaSelection(planned.selection())
					.proxy(proxy)
					.publishGame(proxy || resolved.getNetworkPolicy().getBackendNetworkExposure() != NetworkExposure.PRIVATE)
					.dependencies(proxy ? servers : List.of())
					.workspace(artifacts.installAgent(declaration.getWorkspace(), declaration.getPlatform(), agent))
					.forwarding(forwarding.get(name))
					.agent(agent != null)
					.readinessPattern(provider.readinessPattern())
					.stopCommand(provider.stopCommand())
					.jvmArguments(jvmArguments(declaration, provider, planned))
					.programArguments(provider.programArguments(declaration))
					.defaultCaches(provider.defaultCaches(declaration))
					.build());
		});

		return plan.build();
	}

	/**
	 * Orders a process's JVM arguments so that a later one overrides an earlier one of the same name: the
	 * engine's processor limit, the Java planning arguments, the platform's defaults and session server, and
	 * last the arguments the declaration supplies itself.
	 */
	private List<String> jvmArguments(MinecraftProcess declaration, PlatformProvider provider, JavaRequirementPlanner.PlannedJava planned) {
		List<String> arguments = new ArrayList<>();
		Integer processors = options.getProcessScheduling().getProcessors();
		if (processors != null) arguments.add("-XX:ActiveProcessorCount=" + processors);

		arguments.addAll(planned.jvmArguments());
		arguments.addAll(provider.jvmArguments(declaration, options.isConsoleColors()));
		if (declaration.getSessionServer() != null)
			arguments.addAll(provider.sessionServerArguments(declaration, declaration.getSessionServer()));
		arguments.addAll(declaration.getJvmArguments());

		return arguments;
	}

	@Override
	public @NotNull Path resolve(@NotNull ProcessPlan process, @NotNull PlatformRequest request) throws IOException {
		return provider(process).resolve(process.getDeclaration(), context(process, request)).getJar();
	}

	@Override
	public void configure(@NotNull ProcessPlan process, @NotNull PlatformRequest request) throws IOException {
		provider(process).configure(process.getDeclaration(), context(process, request));
	}

	private PlatformProvider provider(ProcessPlan process) {
		return providers.get(process.getDeclaration().getPlatform());
	}

	private PlatformContext context(ProcessPlan process, PlatformRequest request) {
		return PlatformContext.builder()
				.scenario(request.getScenario())
				.cacheDirectory(request.getCacheDirectory())
				.files(request.getFiles())
				.workDirectory(request.getWorkDirectory())
				.workspaceGroupDirectory(request.getWorkspaceGroupDirectory())
				.bindAddress(request.getBindAddress())
				.port(request.getPort())
				.processAddresses(request.getProcessAddresses())
				.eulaAccepted(options.isEulaAccepted())
				.artifactSource(downloads)
				.forwarding(process.getForwarding())
				.build();
	}

	private Map<String, ForwardingConfiguration> forwarding(ForwardingPlan plan) {
		Map<String, ForwardingConfiguration> result = new LinkedHashMap<>();
		SecureRandom random = new SecureRandom();
		for (ForwardingPlan.Group group : plan.groups()) {
			byte[] secret = new byte[32];
			random.nextBytes(secret);
			ForwardingConfiguration configuration = ForwardingConfiguration.builder()
					.mode(group.mode())
					.proxyOnlineMode(group.proxyOnlineMode())
					.secret(group.mode() == ForwardingMode.NONE ? null : HexFormat.of().formatHex(secret))
					.build();

			group.processes().forEach(name -> result.put(name, configuration));
		}

		return Map.copyOf(result);
	}
}
