package me.whereareiam.anvil.engine.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Immutable local-engine configuration.
 */
@Value
@Builder(toBuilder = true)
public class EngineOptions {
	/**
	 * First Java feature version that can be configured for managed processes.
	 */
	public static final int FIRST_JAVA_VERSION = 8;
	/**
	 * Last Java feature version that can be configured for managed processes.
	 */
	public static final int LAST_JAVA_VERSION = 30;
	/**
	 * System property containing the selected protocol-provider identifier.
	 */
	public static final String PROTOCOL_PROPERTY = "anvil.protocol";
	/**
	 * System property containing explicit EULA acceptance.
	 */
	public static final String EULA_ACCEPTED_PROPERTY = "anvil.eula.accepted";
	/**
	 * System property containing the shared cache directory.
	 */
	public static final String CACHE_DIRECTORY_PROPERTY = "anvil.cacheDir";
	/**
	 * System property containing the disposable workspace directory.
	 */
	public static final String WORK_DIRECTORY_PROPERTY = "anvil.workDir";
	private static final String JAVA_EXECUTABLE_PROPERTY_PREFIX = "anvil.java.";
	private static final String ARTIFACT_PROPERTY_PREFIX = "anvil.artifact.";

	/**
	 * Selected protocol-provider identifier, or {@code null} when inferred.
	 */
	@Nullable String protocolId;
	@Builder.Default Path cacheDirectory = Path.of(System.getProperty("user.home"), ".anvil");
	@Builder.Default Path workDirectory = Path.of("build", "anvil");
	@Builder.Default boolean eulaAccepted = false;
	@Builder.Default boolean keepFailedWorkspaces = true;
	@Builder.Default boolean autoDownloadJavaRuntimes = true;
	@Builder.Default Duration startupTimeout = Duration.ofMinutes(2);
	@Builder.Default Duration stopTimeout = Duration.ofSeconds(15);
	@Builder.Default Path defaultJavaExecutable = Path.of(
			System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java"
	);
	@Singular("javaExecutable") Map<Integer, Path> javaExecutables;
	@Singular("artifact") Map<String, Path> artifacts;

	/**
	 * Builds engine options from the process-property representation used by JUnit and the
	 * standalone foreground runner.
	 *
	 * @return immutable engine options
	 */
	public static @NotNull EngineOptions fromSystemProperties() {
		EngineOptionsBuilder builder = builder()
				.protocolId(System.getProperty(PROTOCOL_PROPERTY))
				.eulaAccepted(Boolean.getBoolean(EULA_ACCEPTED_PROPERTY))
				.cacheDirectory(Path.of(System.getProperty(
						CACHE_DIRECTORY_PROPERTY,
						defaultCacheDirectory().toString()
				)))
				.workDirectory(Path.of(System.getProperty(WORK_DIRECTORY_PROPERTY, "build/anvil")));

		for (int version = FIRST_JAVA_VERSION; version <= LAST_JAVA_VERSION; version++) {
			String executable = System.getProperty(javaExecutableProperty(version));
			if (executable != null)
				builder.javaExecutable(version, Path.of(executable));
		}

		System.getProperties().stringPropertyNames().stream()
				.filter(name -> name.startsWith(ARTIFACT_PROPERTY_PREFIX))
				.forEach(name -> builder.artifact(
						name.substring(ARTIFACT_PROPERTY_PREFIX.length()),
						Path.of(System.getProperty(name))
				));
		return builder.build();
	}

	/**
	 * Returns the Java executable property name for one feature version.
	 *
	 * @param version Java feature version
	 * @return matching system property name
	 * @throws IllegalArgumentException when the version is negative
	 */
	public static @NotNull String javaExecutableProperty(int version) {
		if (version < 0)
			throw new IllegalArgumentException("Java feature version must not be negative: " + version);
		return JAVA_EXECUTABLE_PROPERTY_PREFIX + version;
	}

	/**
	 * Returns the artifact path property name for one declared artifact.
	 *
	 * @param name stable artifact name
	 * @return matching system property name
	 */
	public static @NotNull String artifactProperty(@NotNull String name) {
		if (name.isBlank())
			throw new IllegalArgumentException("Artifact name must not be blank");
		return ARTIFACT_PROPERTY_PREFIX + name;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private static @NotNull Path defaultCacheDirectory() {
		return Path.of(System.getProperty("user.home"), ".anvil");
	}
}
