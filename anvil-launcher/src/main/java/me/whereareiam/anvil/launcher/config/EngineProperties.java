package me.whereareiam.anvil.launcher.config;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import me.whereareiam.anvil.engine.EngineDefaults;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
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
    public static final String AUTO_DOWNLOAD_JAVA_PROPERTY = "anvil.java.download";

    /**
     * Process shutdown timeout as an ISO-8601 duration.
     */
    public static final String STOP_TIMEOUT_PROPERTY = "anvil.stopTimeout";

    /**
     * Requested Java feature version.
     */
    public static final String JAVA_VERSION_PROPERTY = "anvil.java.version";
    public static final String JAVA_DISTRIBUTION_PROPERTY = "anvil.java.distribution";
    public static final String JAVA_RELEASE_PROPERTY = "anvil.java.release";
    public static final String JAVA_HOME_PROPERTY = "anvil.java.home";
    public static final String JAVA_EXECUTABLE_PROPERTY = "anvil.java.executable";
    public static final String JAVA_ARCHIVE_URI_PROPERTY = "anvil.java.archive.uri";
    public static final String JAVA_ARCHIVE_SHA256_PROPERTY = "anvil.java.archive.sha256";
    public static final String EXECUTION_PROPERTY = "anvil.execution";
    public static final String OFFLINE_PROPERTY = "anvil.offline";
    public static final String REFRESH_PROPERTY = "anvil.refresh";
    public static final String PARALLELISM_PROPERTY = "anvil.parallelism";
    public static final String STARTUP_MEMORY_PROPERTY = "anvil.startupMemoryMegabytes";
    public static final String DOWNLOAD_PARALLELISM_PROPERTY = "anvil.downloadParallelism";

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
     * Java distribution, feature version, release, and installation are decoded as one selection.
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
                .downloadJava(booleanValue(properties, AUTO_DOWNLOAD_JAVA_PROPERTY, defaults.isDownloadJava()))
                .cacheDirectory(pathValue(properties, CACHE_DIRECTORY_PROPERTY, defaults.getCacheDirectory()))
                .workDirectory(pathValue(properties, WORK_DIRECTORY_PROPERTY, defaults.getWorkDirectory()))
                .javaRequirement(JavaRequirement.builder()
                        .featureVersion(optionalPositive(properties))
                        .distribution(properties.getProperty(JAVA_DISTRIBUTION_PROPERTY))
                        .release(properties.getProperty(JAVA_RELEASE_PROPERTY))
                        .build()
				)
				.javaSource(javaSource(properties))
                .executionId(properties.getProperty(EXECUTION_PROPERTY, defaults.getExecutionId()))
                .offline(booleanValue(properties, OFFLINE_PROPERTY, false))
                .refresh(booleanValue(properties, REFRESH_PROPERTY, false))
                .parallelism(positive(properties, PARALLELISM_PROPERTY, defaults.getParallelism()))
                .startupMemoryMegabytes(positive(properties, STARTUP_MEMORY_PROPERTY, defaults.getStartupMemoryMegabytes()))
                .downloadParallelism(positive(properties, DOWNLOAD_PARALLELISM_PROPERTY, defaults.getDownloadParallelism()))
                .stopTimeout(stopTimeout(properties, defaults.getStopTimeout()));

        for (String name : properties.stringPropertyNames().stream().sorted().toList()) {
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
     * Returns the property key for a named artifact.
     *
     * @param name non-blank artifact name
     * @return property key
     */
    public static @NotNull String artifactProperty(@NotNull String name) {
        if (name.isBlank()) throw new IllegalArgumentException("Artifact name must not be blank");
        return ARTIFACT_PREFIX + name;
    }

    private static Integer optionalPositive(Properties properties) {
        if (properties.getProperty(EngineProperties.JAVA_VERSION_PROPERTY) == null) return null;
        return positive(properties, EngineProperties.JAVA_VERSION_PROPERTY, 1);
    }

	private static JavaSource javaSource(Properties properties) {
		String home = properties.getProperty(JAVA_HOME_PROPERTY);
		String executable = properties.getProperty(JAVA_EXECUTABLE_PROPERTY);
		String archiveUri = properties.getProperty(JAVA_ARCHIVE_URI_PROPERTY);
		String archiveSha256 = properties.getProperty(JAVA_ARCHIVE_SHA256_PROPERTY);
		int selected = (home != null ? 1 : 0) + (executable != null ? 1 : 0) + (archiveUri != null || archiveSha256 != null ? 1 : 0);
		if (selected > 1) throw new IllegalArgumentException("Configure only one explicit Java source");
		if (home != null) return new LocalJavaHome(Path.of(home));
		if (executable != null) return new LocalJavaExecutable(Path.of(executable));
		if (archiveUri == null && archiveSha256 == null) return null;
		if (archiveUri == null || archiveSha256 == null)
			throw new IllegalArgumentException("Java archive URI and SHA-256 must be configured together");
		if (!archiveSha256.matches("[a-fA-F0-9]{64}"))
			throw new IllegalArgumentException("Java archive SHA-256 must contain 64 hexadecimal characters");
		return JavaArchive.builder().uri(URI.create(archiveUri)).sha256(archiveSha256).build();
	}

    private static int positive(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        int result = value == null ? fallback : Integer.parseInt(value);
        if (result < 1) throw new IllegalArgumentException(key + " must be positive");
        return result;
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
        return value == null
				? fallback
				: Path.of(value);
    }

    private static boolean booleanValue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;

        throw new IllegalArgumentException(key + " must be true or false");
    }
}
