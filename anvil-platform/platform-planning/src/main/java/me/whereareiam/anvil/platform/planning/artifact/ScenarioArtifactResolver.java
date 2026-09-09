package me.whereareiam.anvil.platform.planning.artifact;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.platform.api.PlatformAgentSource;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Resolves scenario artifact references and assembles the platform-agent workspace asset.
 */
public final class ScenarioArtifactResolver {
	private final Map<String, Path> artifacts;
	private final PlatformAgentSource agents;

	public ScenarioArtifactResolver(@NotNull Map<String, Path> artifacts, @NotNull PlatformAgentSource agents) {
		this.artifacts = Map.copyOf(artifacts);
		this.agents = agents;
	}

	/**
	 * Resolves every declared artifact before provisioning starts, preserving the supplied scenario.
	 */
	public @NotNull AnvilScenario resolve(@NotNull AnvilScenario scenario) {
		return scenario.toBuilder()
				.clearServers()
				.servers(scenario.getServers().stream().map(server -> server.toBuilder()
						.distribution(resolve(server.getDistribution()))
						.workspace(resolve(server.getWorkspace()))
						.build()).toList())
				.clearProxies()
				.proxies(scenario.getProxies().stream().map(proxy -> proxy.toBuilder()
						.distribution(resolve(proxy.getDistribution()))
						.workspace(resolve(proxy.getWorkspace()))
						.build()).toList())
				.build();
	}

	/**
	 * Adds the provider's agent to an already resolved workspace plan.
	 */
	public @NotNull WorkspacePlan installAgent(
			@NotNull WorkspacePlan workspace,
			@Nullable PlatformAgentDescriptor agent
	) {
		if (agent == null)
			return workspace;
		return workspace.toBuilder()
				.asset(WorkspaceAsset.builder()
						.group("anvil-platform-agent")
						.source(AssetSource.path(agents.locate(agent)))
						.target(agent.getTarget())
						.mode(AssetInstallMode.ALWAYS)
						.build())
				.build();
	}

	private WorkspacePlan resolve(WorkspacePlan workspace) {
		return workspace.toBuilder().clearAssets()
				.assets(workspace.getAssets().stream().map(this::resolve).toList()).build();
	}

	private WorkspaceAsset resolve(WorkspaceAsset asset) {
		AssetSource source = asset.getSource();
		if ((source.getPath() == null) == (source.getArtifactReference() == null))
			throw new ScenarioValidationException("Workspace asset must declare exactly one source: " + asset);
		Path path = source.isArtifact() ? requireArtifact(source.getArtifactReference()) : source.getPath();
		return asset.toBuilder().source(AssetSource.path(path.toAbsolutePath().normalize())).build();
	}

	private Distribution resolve(Distribution distribution) {
		if (!distribution.isArtifact())
			return distribution;
		return distribution.toBuilder().localJar(requireArtifact(distribution.getArtifactReference()))
				.artifactReference(null).build();
	}

	private Path requireArtifact(String reference) {
		Path artifact = artifacts.get(reference);
		if (artifact == null)
			throw new ProvisioningException("No artifact named '" + reference + "' was supplied. Available: "
					+ artifacts.keySet());
		if (!Files.isRegularFile(artifact))
			throw new ProvisioningException("Artifact '" + reference + "' does not exist: " + artifact);
		return artifact.toAbsolutePath().normalize();
	}
}
