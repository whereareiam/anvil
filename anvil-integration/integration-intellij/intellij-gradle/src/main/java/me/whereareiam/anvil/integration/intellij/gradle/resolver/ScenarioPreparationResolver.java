package me.whereareiam.anvil.integration.intellij.gradle.resolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Selects the Gradle executable for a build and allocates the caller-owned preparation manifest.
 */
public final class ScenarioPreparationResolver {
	/**
	 * Resolves a preparation route into an executable command and caller-owned manifest location.
	 * This operation validates the Gradle executable but does not run it.
	 *
	 * @param executionRoot directory containing the selected build's wrapper
	 * @param taskPath native Anvil preparation task path
	 * @param windows whether to produce Windows wrapper arguments
	 * @param settings the IDE's Gradle settings for this build
	 * @return execution instructions and the caller-owned manifest location
	 * @throws IOException if the Gradle executable is unavailable or the manifest cannot be allocated
	 */
	public @NotNull ScenarioPreparation resolve(
			@NotNull Path executionRoot,
			@NotNull String taskPath,
			boolean windows,
			@NotNull Settings settings
	) throws IOException {
		Path gradle = settings.gradleHome() == null
				? wrapper(executionRoot, windows)
				: installation(settings.gradleHome(), windows);
		Path manifest = Files.createTempFile("anvil-tooling-", ".json");

		List<String> command = new ArrayList<>();
		if (windows) command.addAll(List.of("cmd.exe", "/d", "/c"));

		command.add(gradle.toString());
		command.add(taskPath);
		command.add("--output-file");
		command.add(manifest.toString());
		command.add("--console=plain");
		if (settings.offline()) command.add("--offline");

		return ScenarioPreparation.builder()
				.command(List.copyOf(command))
				.workingDirectory(executionRoot)
				.manifestPath(manifest)
				.environment(settings.javaHome() == null ? Map.of() : Map.of("JAVA_HOME", settings.javaHome().toString()))
				.build();
	}

	private static @NotNull Path wrapper(@NotNull Path executionRoot, boolean windows) throws IOException {
		Path wrapper = executionRoot.resolve(windows ? "gradlew.bat" : "gradlew");
		if (!Files.isRegularFile(wrapper))
			throw new IOException("The selected project's Gradle wrapper is missing: " + wrapper);
		if (!windows && !Files.isExecutable(wrapper))
			throw new IOException("The selected project's Gradle wrapper is not executable: " + wrapper);

		return wrapper;
	}

	private static @NotNull Path installation(@NotNull Path gradleHome, boolean windows) throws IOException {
		Path gradle = gradleHome.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");
		if (!Files.isRegularFile(gradle))
			throw new IOException("The Gradle installation selected in the IDE settings has no launcher: " + gradle);

		return gradle;
	}

	/**
	 * The IDE's Gradle settings that apply to preparation.
	 *
	 * @param javaHome Gradle JVM selected in the IDE, or null to inherit the environment
	 * @param gradleHome local Gradle installation selected instead of the wrapper, or null for the wrapper
	 * @param offline whether the IDE runs Gradle in offline mode
	 */
	public record Settings(@Nullable Path javaHome, @Nullable Path gradleHome, boolean offline) {
		/**
		 * Wrapper execution with the inherited Java and network access.
		 */
		public static final Settings DEFAULT = new Settings(null, null, false);
	}
}
