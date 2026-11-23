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
		PresentationMetadata metadata = scenario.metadata();
		return ScenarioDescriptor.builder()
				.definition(definition)
				.name(scenario.name())
				.displayName(DisplayNameResolver.resolve(scenario.name(), metadata))
				.description(metadata == null ? null : metadata.description())
				.category(metadata == null ? null : metadata.category())
				.tags(metadata == null ? List.of() : metadata.tags())
				.entrypoint(scenario.entrypoint())
				.processes(Stream.concat(
						scenario.servers().stream().map(server -> server(scenario, server)),
						scenario.proxies().stream().map(proxy -> proxy(scenario, proxy))
				).toList())
				.manual(scenario.manual())
				.setupAvailable(scenario.setupHook() != null)
				.startupTimeoutMillis(scenario.processTimeouts().startup() == null
						? 0
						: scenario.processTimeouts().startup().toMillis())
				.shutdownTimeoutMillis(scenario.processTimeouts().shutdown() == null
						? 0
						: scenario.processTimeouts().shutdown().toMillis())
				.executionProviderId(scenario.executionProviderId())
				.javaRequirement(javaSelection(scenario.javaSelection(), null))
				.build();
	}

	private static ProcessDefinition server(AnvilScenario scenario, MinecraftServer server) {
		return process(scenario, server, ProcessRole.SERVER)
				.minecraftVersion(server.minecraftVersion())
				.build();
	}

	private static ProcessDefinition proxy(AnvilScenario scenario, MinecraftProxy proxy) {
		return process(scenario, proxy, ProcessRole.PROXY)
				.backendNames(proxy.servers())
				.defaultBackend(proxy.defaultServer())
				.build();
	}

	private static ProcessDefinition.ProcessDefinitionBuilder process(
			AnvilScenario scenario,
			MinecraftProcess process,
			ProcessRole role
	) {
		PresentationMetadata metadata = process.metadata();
		Distribution distribution = process.distribution();
		return ProcessDefinition.builder()
				.name(process.name())
				.displayName(DisplayNameResolver.resolve(process.name(), metadata))
				.description(metadata == null ? null : metadata.description())
				.role(role)
				.platform(process.platform())
				.distribution(distribution(distribution))
				.distributionVersion(distribution.version())
				.distributionBuild(distribution.build())
				.memoryMegabytes(process.memoryMegabytes())
				.onlineMode(process.onlineMode())
				.javaRequirement(javaSelection(process.javaSelection(), scenario.javaSelection()));
	}

	private static String distribution(Distribution distribution) {
		if (distribution.isLocal()) return "Local JAR: " + distribution.localJar();
		if (distribution.isArtifact()) return "Artifact: " + distribution.artifactReference();

		String selection = distribution.version() == null ? "Unspecified version" : distribution.version();
		List<String> details = new ArrayList<>();
		if (distribution.build() != null) details.add("build " + distribution.build());
		if (distribution.sha256() != null) details.add("SHA-256 " + distribution.sha256());

		return details.isEmpty() ? selection : selection + " (" + String.join(", ", details) + ")";
	}

	private static String javaSelection(JavaSelection declared, @Nullable JavaSelection defaults) {
		JavaSelection effective = defaults == null ? declared : declared.withDefaults(defaults);
		String requirement = effective.requirement() == null ? "Project default"
				: javaRequirement(effective.requirement()) + (declared.requirement() == null ? " (scenario)" : "");
		if (effective.source() == null) return requirement;

		return requirement + "; " + javaSource(effective.source())
				+ (declared.source() == null ? " (scenario)" : "");
	}

	private static String javaRequirement(JavaRequirement requirement) {
		List<String> values = new ArrayList<>();
		values.add(requirement.featureVersion() == null ? "Planned default LTS" : "Java " + requirement.featureVersion());
		if (requirement.distribution() != null) values.add(requirement.distribution());
		if (requirement.release() != null) values.add("release " + requirement.release());

		return String.join(" · ", values);
	}

	private static String javaSource(JavaSource source) {
		if (source instanceof LocalJavaHome home) return "Java home: " + home.home();
		if (source instanceof LocalJavaExecutable executable) return "Java executable: " + executable.executable();
		if (source instanceof JavaArchive archive) return "Java archive: " + archive.uri();

		return "Custom Java source";
	}
}
