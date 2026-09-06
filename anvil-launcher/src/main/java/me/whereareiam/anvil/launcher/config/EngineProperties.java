package me.whereareiam.anvil.launcher.config;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.engine.EngineDefaults;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * Maps the process-property contract used by Gradle, JUnit, and command-line tooling.
 * Property names remain separate from the immutable engine configuration.
 */
public final class EngineProperties {
	/**
	 * Selected protocol-provider identifier.
	 */
	public static final String PROTOCOL_PROPERTY = "anvil.protocol";

	/**
	 * Explicit Minecraft EULA acceptance.
	 */
	public static final String EULA_ACCEPTED_PROPERTY = "anvil.eula.accepted";

	/**
	 * Shared cache directory.
	 */
	public static final String CACHE_DIRECTORY_PROPERTY = "anvil.cacheDir";

	/**
	 * Scenario workspace directory.
	 */
	public static final String WORK_DIRECTORY_PROPERTY = "anvil.workDir";

	/**
	 * Whether unsuccessful workspaces are retained.
	 */
	public static final String KEEP_FAILED_WORKSPACES_PROPERTY = "anvil.keepFailedWorkspaces";

	/**
	 * Whether missing Java runtimes may be downloaded.
	 */
	public static final String AUTO_DOWNLOAD_JAVA_PROPERTY = "anvil.autoDownloadJavaRuntimes";

	/**
	 * Process shutdown timeout as an ISO-8601 duration.
	 */
	public static final String STOP_TIMEOUT_PROPERTY = "anvil.stopTimeout";

	/**
	 * Executable used for the current Java installation.
	 */
	public static final String DEFAULT_JAVA_PROPERTY = "anvil.defaultJavaExecutable";

	private static final String JAVA_PREFIX = "anvil.java.";
	private static final String ARTIFACT_PREFIX = "anvil.artifact.";

	/**
	 * Reads a snapshot of the current process properties.
	 *
	 * @return immutable engine options
	 */
	public static @NotNull EngineOptions fromSystemProperties() {
		Properties snapshot = new Properties();
		snapshot.putAll(System.getProperties());
		return from(snapshot);
	}

	/**
	 * Decodes supplied properties without changing global process state.
	 * Java executable overrides are read from their supplied feature-version keys, without a fixed range.
	 *
	 * @param properties property representation
	 * @return immutable engine options
	 * @throws IllegalArgumentException if a Java version, boolean, or timeout is invalid
	 */
	public static @NotNull EngineOptions from(@NotNull Properties properties) {
		EngineOptions defaults = EngineDefaults.resolve(EngineOptions.builder().build());
		var builder = EngineOptions.builder()
				.protocolId(properties.getProperty(PROTOCOL_PROPERTY))
				.eulaAccepted(booleanValue(properties, EULA_ACCEPTED_PROPERTY, defaults.isEulaAccepted()))
				.keepFailedWorkspaces(booleanValue(properties, KEEP_FAILED_WORKSPACES_PROPERTY, defaults.isKeepFailedWorkspaces()))
				.autoDownloadJavaRuntimes(booleanValue(properties, AUTO_DOWNLOAD_JAVA_PROPERTY, defaults.isAutoDownloadJavaRuntimes()))
				.cacheDirectory(pathValue(properties, CACHE_DIRECTORY_PROPERTY, defaults.getCacheDirectory()))
				.workDirectory(pathValue(properties, WORK_DIRECTORY_PROPERTY, defaults.getWorkDirectory()))
				.defaultJavaExecutable(pathValue(properties, DEFAULT_JAVA_PROPERTY, defaults.getDefaultJavaExecutable()))
				.stopTimeout(stopTimeout(properties, defaults.getStopTimeout()));

		for (String name : properties.stringPropertyNames().stream().sorted().toList()) {
			if (name.startsWith(JAVA_PREFIX)) {
				int version = Integer.parseInt(name.substring(JAVA_PREFIX.length()));
				if (version <= 0)
					throw new IllegalArgumentException("Java feature version must be positive: " + version);
				builder.javaExecutable(version, Path.of(properties.getProperty(name)));
			}

			if (name.startsWith(ARTIFACT_PREFIX)) {
				String artifact = name.substring(ARTIFACT_PREFIX.length());
				if (artifact.isBlank()) throw new IllegalArgumentException("Artifact name must not be blank");

				builder.artifact(artifact, Path.of(properties.getProperty(name)));
			}
		}

		EngineOptions options = builder.build();
		if (options.getStopTimeout().isNegative() || options.getStopTimeout().isZero())
			throw new IllegalArgumentException("Stop timeout must be positive");

		return options;
	}

	/**
	 * Returns the property key for a Java feature version.
	 *
	 * @param version positive Java feature version
	 * @return property key
	 */
	public static @NotNull String javaExecutableProperty(int version) {
		if (version <= 0) throw new IllegalArgumentException("Java feature version must be positive: " + version);
		return JAVA_PREFIX + version;
	}

	/**
	 * Returns the property key for a named artifact.
	 *
	 * @param name non-blank artifact name
	 * @return property key
	 */
	public static @NotNull String artifactProperty(@NotNull String name) {
		if (name.isBlank()) throw new IllegalArgumentException("Artifact name must not be blank");
		return ARTIFACT_PREFIX + name;
	}

	private static Duration stopTimeout(Properties properties, Duration fallback) {
		String value = properties.getProperty(STOP_TIMEOUT_PROPERTY);
		if (value == null) return fallback;

		try {
			return Duration.parse(value);
		} catch (DateTimeParseException failure) {
			throw new IllegalArgumentException(STOP_TIMEOUT_PROPERTY + " must be an ISO-8601 duration", failure);
		}
	}

	private static Path pathValue(Properties properties, String key, Path fallback) {
		String value = properties.getProperty(key);
		return value == null ? fallback : Path.of(value);
	}

	private static boolean booleanValue(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		if (value == null) return fallback;
		if (value.equalsIgnoreCase("true")) return true;
		if (value.equalsIgnoreCase("false")) return false;

		throw new IllegalArgumentException(key + " must be true or false");
	}
}
