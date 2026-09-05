package me.whereareiam.anvil.runner.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.engine.model.EngineOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;

/**
 * Immutable runtime configuration supplied to the foreground scenario runner.
 *
 * <p>This is the runner-facing boundary. It keeps reusable tooling independent of Gradle while
 * allowing the runner to translate the values into the engine's internal configuration.</p>
 */
@Value
@Builder
public class AnvilRunnerConfiguration {
	@Builder.Default
	boolean eulaAccepted = false;

	@NotNull
	@Builder.Default
	Path cacheDirectory = Path.of(System.getProperty("user.home"), ".anvil");

	@NotNull
	@Builder.Default
	Path workDirectory = Path.of("build", "anvil");

	@Nullable String protocolId;
	@NotNull
	@Singular("javaExecutable")
	Map<Integer, Path> javaExecutables;

	@NotNull
	@Singular("artifact")
	Map<String, Path> artifacts;

	/**
	 * Reads the standard process-property representation through the shared engine mapping.
	 *
	 * @return immutable runner configuration
	 */
	public static @NotNull AnvilRunnerConfiguration fromSystemProperties() {
		EngineOptions options = EngineOptions.fromSystemProperties();
		AnvilRunnerConfigurationBuilder builder = builder()
				.eulaAccepted(options.isEulaAccepted())
				.cacheDirectory(options.getCacheDirectory())
				.workDirectory(options.getWorkDirectory())
				.protocolId(options.getProtocolId());

		options.getJavaExecutables().forEach(builder::javaExecutable);
		options.getArtifacts().forEach(builder::artifact);

		return builder.build();
	}
}
