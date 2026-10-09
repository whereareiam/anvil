package me.whereareiam.anvil.runner.scenario;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Projects declared scenarios and processes into portable tooling descriptions.
 */
public final class ScenarioDescriptorFactory {
	/**
	 * Projects one discovered definition and its evaluated scenario into a portable descriptor.
	 *
	 * @param definition fully qualified definition class name
	 * @param scenario   evaluated scenario declaration
	 * @return immutable descriptor retaining the definition identity and metadata
	 */
	public static @NotNull ScenarioDescriptor describe(@NotNull String definition, @NotNull AnvilScenario scenario) {
		PresentationMetadata metadata = scenario.getMetadata();
		return ScenarioDescriptor.builder()
				.definition(definition)
				.name(scenario.getName())
				.displayName(DisplayNameResolver.resolve(scenario.getName(), metadata))
				.description(metadata == null ? null : metadata.getDescription())
				.category(metadata == null ? null : metadata.getCategory())
				.tags(metadata == null ? List.of() : metadata.getTags())
				.entrypoint(scenario.getEntrypoint())
				.processes(Stream.concat(
						scenario.getServers().stream().map(server -> server(scenario, server)),
						scenario.getProxies().stream().map(proxy -> proxy(scenario, proxy))
				).toList())
				.manual(scenario.isManual())
				.setupAvailable(scenario.getSetupHook() != null)
				.startupTimeoutMillis(scenario.getProcessTimeouts().getStartup() == null ? 0 : scenario.getProcessTimeouts().getStartup().toMillis())
				.shutdownTimeoutMillis(scenario.getProcessTimeouts().getShutdown() == null ? 0 : scenario.getProcessTimeouts().getShutdown().toMillis())
				.executionProviderId(scenario.getExecutionProviderId())
				.javaRequirement(javaSelection(scenario.getJavaSelection(), null))
				.build();
	}

	private static ProcessDefinition server(AnvilScenario scenario, MinecraftServer server) {
		return process(scenario, server, ProcessRole.SERVER)
				.minecraftVersion(server.getMinecraftVersion())
				.build();
	}

	private static ProcessDefinition proxy(AnvilScenario scenario, MinecraftProxy proxy) {
		return process(scenario, proxy, ProcessRole.PROXY)
				.backendNames(proxy.getServers())
				.defaultBackend(proxy.getDefaultServer())
				.build();
	}

	private static ProcessDefinition.ProcessDefinitionBuilder process(
			AnvilScenario scenario,
			MinecraftProcess process,
			ProcessRole role
	) {
		PresentationMetadata metadata = process.getMetadata();
		Distribution distribution = process.getDistribution();
		return ProcessDefinition.builder()
				.name(process.getName())
				.displayName(DisplayNameResolver.resolve(process.getName(), metadata))
				.description(metadata == null ? null : metadata.getDescription())
				.role(role)
				.platform(process.getPlatform())
				.distribution(distribution(distribution))
				.distributionVersion(distribution.getVersion())
				.distributionBuild(distribution.getBuild())
				.memoryMegabytes(process.getMemoryMegabytes())
				.onlineMode(process.isOnlineMode())
				.javaRequirement(javaSelection(process.getJavaSelection(), scenario.getJavaSelection()));
	}

	private static String distribution(Distribution distribution) {
		if (distribution.isLocal()) return "Local JAR: " + distribution.getLocalJar();
		if (distribution.isArtifact()) return "Artifact: " + distribution.getArtifactReference();

		String selection = distribution.getVersion() == null ? "Unspecified version" : distribution.getVersion();
		List<String> details = new ArrayList<>();
		if (distribution.getBuild() != null) details.add("build " + distribution.getBuild());
		if (distribution.getSha256() != null) details.add("SHA-256 " + distribution.getSha256());

		return details.isEmpty() ? selection : selection + " (" + String.join(", ", details) + ")";
	}

	private static String javaSelection(JavaSelection declared, @Nullable JavaSelection defaults) {
		JavaSelection effective = defaults == null ? declared : declared.withDefaults(defaults);
		String requirement = effective.getRequirement() == null ? "Project default"
				: javaRequirement(effective.getRequirement()) + (declared.getRequirement() == null ? " (scenario)" : "");
		if (effective.getSource() == null) return requirement;

		return requirement + "; " + javaSource(effective.getSource())
				+ (declared.getSource() == null ? " (scenario)" : "");
	}

	private static String javaRequirement(JavaRequirement requirement) {
		List<String> values = new ArrayList<>();
		values.add(requirement.getFeatureVersion() == null ? "Planned default LTS" : "Java " + requirement.getFeatureVersion());
		if (requirement.getDistribution() != null) values.add(requirement.getDistribution());
		if (requirement.getRelease() != null) values.add("release " + requirement.getRelease());

		return String.join(" · ", values);
	}

	private static String javaSource(JavaSource source) {
		if (source instanceof LocalJavaHome home) return "Java home: " + home.getHome();
		if (source instanceof LocalJavaExecutable executable) return "Java executable: " + executable.getExecutable();
		if (source instanceof JavaArchive archive) return "Java archive: " + archive.getUri();

		return "Custom Java source";
	}
}
