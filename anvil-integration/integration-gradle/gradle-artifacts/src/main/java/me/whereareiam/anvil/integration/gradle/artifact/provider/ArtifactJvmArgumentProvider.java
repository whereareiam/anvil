package me.whereareiam.anvil.integration.gradle.artifact.provider;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds a produced artifact to JVM properties while retaining its Gradle file dependencies.
 * File paths are read when a launch is prepared, after the producing tasks have completed.
 */
public abstract class ArtifactJvmArgumentProvider implements CommandLineArgumentProvider {
	/**
	 * Initializes optional additional property names for this artifact.
	 */
	@Inject
	public ArtifactJvmArgumentProvider() {
		getPropertyNames().convention(List.of());
	}

	/**
	 * Stable artifact reference used in scenario declarations.
	 */
	@Input
	public abstract @NotNull Property<String> getArtifactName();

	/**
	 * Exactly one artifact and the Gradle tasks that produce it.
	 */
	@Classpath
	public abstract @NotNull ConfigurableFileCollection getArtifactFiles();

	/**
	 * Additional JVM property names that receive the same resolved file path.
	 */
	@Input
	public abstract @NotNull ListProperty<String> getPropertyNames();

	/**
	 * Resolves the single artifact after Gradle has built its inputs.
	 *
	 * @return absolute artifact path
	 */
	public @NotNull String artifactPath() {
		var files = getArtifactFiles().getFiles();
		if (files.size() != 1) {
			throw new IllegalArgumentException("Anvil artifact '" + getArtifactName().get()
					+ "' must resolve to exactly one file, found " + files.size());
		}

		return files.iterator().next().getAbsolutePath();
	}

	/**
	 * Resolves the canonical artifact property and its additional configured names.
	 *
	 * @return property values for a JVM or prepared-launch manifest
	 */
	public @NotNull Map<String, String> asProperties() {
		String path = artifactPath();
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("anvil.artifact." + getArtifactName().get(), path);
		getPropertyNames().get().forEach(name -> properties.put(name, path));

		return Map.copyOf(properties);
	}

	@Override
	public @NotNull Iterable<String> asArguments() {
		return asProperties().entrySet()
				.stream()
				.map(entry -> "-D" + entry.getKey() + "=" + entry.getValue())
				.toList();
	}
}
