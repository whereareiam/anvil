package me.whereareiam.anvil.integration.intellij.gradle.model;

import com.intellij.serialization.PropertyMapping;

import java.io.Serial;
import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Serializable snapshot of a module's native Gradle identity and Anvil preparation route.
 * <p>
 * IntelliJ keeps imported data in its external-system cache across restarts. That cache rebuilds
 * values through the {@link PropertyMapping} constructor, so every stored property must appear there.
 */
@Getter
public final class ImportedModule implements Serializable {
	public static final int SCHEMA_VERSION = 1;

	@Serial
	private static final long serialVersionUID = 1;

	private final int schemaVersion;
	private final boolean enabled;
	private final boolean incompatible;
	private final @NotNull String moduleDirectory;
	private final @NotNull String buildRootDirectory;
	private final @NotNull String projectPath;
	private final @NotNull String executionDirectory;
	private final @NotNull String preparationTaskPath;

	@Builder
	@PropertyMapping({
			"schemaVersion",
			"enabled",
			"incompatible",
			"moduleDirectory",
			"buildRootDirectory",
			"projectPath",
			"executionDirectory",
			"preparationTaskPath"
	})
	private ImportedModule(
			int schemaVersion,
			boolean enabled,
			boolean incompatible,
			@NotNull String moduleDirectory,
			@NotNull String buildRootDirectory,
			@NotNull String projectPath,
			@NotNull String executionDirectory,
			@NotNull String preparationTaskPath
	) {
		this.schemaVersion = schemaVersion;
		this.enabled = enabled;
		this.incompatible = incompatible;
		this.moduleDirectory = moduleDirectory;
		this.buildRootDirectory = buildRootDirectory;
		this.projectPath = projectPath;
		this.executionDirectory = executionDirectory;
		this.preparationTaskPath = preparationTaskPath;
	}

	/**
	 * Builds modules with the current schema version unless a test reproduces an older cache entry.
	 */
	public static final class ImportedModuleBuilder {
		private int schemaVersion = SCHEMA_VERSION;
	}
}
