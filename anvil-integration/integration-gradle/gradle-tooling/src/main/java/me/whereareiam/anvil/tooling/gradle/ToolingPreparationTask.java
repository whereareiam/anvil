package me.whereareiam.anvil.tooling.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.integration.gradle.artifact.provider.ArtifactJvmArgumentProvider;
import me.whereareiam.anvil.tooling.gradle.model.ToolingLaunchModel;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a declared project runtime after its compilation and artifact tasks have completed.
 */
@DisableCachingByDefault(because = "The prepared launch contains machine-local absolute paths")
public abstract class ToolingPreparationTask extends DefaultTask {
	/**
	 * Resolved scenario runtime; its producing tasks run before preparation.
	 * @return runtime classpath
	 */
	@Classpath
	public abstract @NotNull ConfigurableFileCollection getRuntimeClasspath();

	/** Compiled class directories scanned for scenario-definition interfaces. */
	@Classpath
	public abstract @NotNull ConfigurableFileCollection getScenarioClasses();

	/** Existing project resources whose scenario index entries should be preserved. */
	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract @NotNull ConfigurableFileCollection getScenarioResources();

	/** Generated definition index placed on the prepared tooling classpath. */
	@OutputDirectory
	public abstract @NotNull DirectoryProperty getDefinitionIndex();

	/**
	 * Explicit scenario JVM properties, excluding secret authentication values.
	 * @return JVM property map
	 */
	@Input
	public abstract @NotNull MapProperty<String, String> getJvmProperties();

	/**
	 * Named artifact file inputs and their producing task dependencies.
	 * @return artifact inputs
	 */
	@Nested
	public abstract @NotNull ListProperty<ArtifactJvmArgumentProvider> getArtifacts();

	/**
	 * Java toolchain selected by the owning project.
	 * @return selected Java launcher
	 */
	@Nested
	public abstract @NotNull Property<JavaLauncher> getJavaLauncher();

	/**
	 * Prepared launch file owned by the calling tool or project build.
	 * @return launch manifest path
	 */
	@OutputFile
	public abstract @NotNull RegularFileProperty getLaunchManifest();

	/**
	 * Chooses a caller-owned absolute manifest path; omission uses the project's build directory.
	 * @param value absolute output file path
	 */
	@Option(option = "output-file", description = "Absolute path for the prepared tooling launch manifest")
	public void outputFile(@NotNull String value) {
		Path output = Path.of(value);
		if (!output.isAbsolute()) throw new IllegalArgumentException("The tooling output file must be an absolute path");
		getLaunchManifest().fileValue(output.toFile());
	}

	/**
	 * Writes the launch contract without loading scenario classes in the Gradle process.
	 * @throws IOException if the requested output cannot be written
	 */
	@TaskAction
	public void prepare() throws IOException {
		Map<String, String> artifacts = new LinkedHashMap<>();
		Map<String, String> properties = new LinkedHashMap<>(getJvmProperties().get());
		for (var input : getArtifacts().get()) {
			artifacts.put(input.getArtifactName().get(), input.artifactPath());
			properties.putAll(input.asProperties());
		}
		List<String> definitions = ScenarioDefinitionScanner.scan(getScenarioClasses(), getScenarioResources());
		Path index = getDefinitionIndex().get().getAsFile().toPath();
		ScenarioDefinitionScanner.writeIndex(index, definitions);
		var launch = ToolingLaunchModel.builder().schemaVersion(ToolingLaunchModel.SCHEMA_VERSION)
				.toolingJavaExecutable(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath())
				.classpath(java.util.stream.Stream.concat(
						getRuntimeClasspath().getFiles().stream().map(java.io.File::getAbsolutePath),
						java.util.stream.Stream.of(index.toAbsolutePath().toString())).toList())
				.definitions(definitions).properties(Map.copyOf(properties)).artifacts(artifacts).build();
		Path output = getLaunchManifest().get().getAsFile().toPath();
		Files.createDirectories(output.getParent());
		new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), launch);
	}

}
