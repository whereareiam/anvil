package me.whereareiam.anvil.launcher.config;

import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import me.whereareiam.anvil.api.type.SupportPolicy;
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
	 * Default execution-provider identifier.
	 */
	public static final String EXECUTION_PROPERTY = "anvil.execution";

	/**
	 * Default protocol-library identifier for simulated players.
	 */
	public static final String PROTOCOL_LIBRARY_PROPERTY = "anvil.protocolLibrary";

	/**
	 * Prefix for additional protocol-library release data files, followed by the library identifier.
	 */
	public static final String PROTOCOL_RELEASES_PROPERTY_PREFIX = "anvil.protocolReleases.";

	/**
	 * Default support policy: {@code lenient} or {@code strict}, case-insensitive.
	 */
	public static final String SUPPORT_POLICY_PROPERTY = "anvil.supportPolicy";

	/**
	 * Requested Java feature version.
	 */
	public static final String JAVA_VERSION_PROPERTY = "anvil.java.version";

	/**
	 * Requested Java vendor distribution.
	 */
	public static final String JAVA_DISTRIBUTION_PROPERTY = "anvil.java.distribution";

	/**
	 * Requested Java release.
	 */
	public static final String JAVA_RELEASE_PROPERTY = "anvil.java.release";

	/**
	 * Explicit local Java installation directory.
	 */
	public static final String JAVA_HOME_PROPERTY = "anvil.java.home";

	/**
	 * Explicit local Java executable.
	 */
	public static final String JAVA_EXECUTABLE_PROPERTY = "anvil.java.executable";

	/**
	 * URI of an explicit Java runtime archive.
	 */
	public static final String JAVA_ARCHIVE_URI_PROPERTY = "anvil.java.archive.uri";

	/**
	 * Required SHA-256 checksum of the explicit Java archive.
	 */
	public static final String JAVA_ARCHIVE_SHA256_PROPERTY = "anvil.java.archive.sha256";

	/**
	 * Whether missing Java runtimes may be downloaded.
	 */
	public static final String AUTO_DOWNLOAD_JAVA_PROPERTY = "anvil.java.download";

	/**
	 * Shared cache directory.
	 */
	public static final String CACHE_DIRECTORY_PROPERTY = "anvil.cacheDir";

	/** Local account store directory. */
	public static final String ACCOUNTS_DIRECTORY_PROPERTY = "anvil.accountsDir";

	/**
	 * Scenario workspace directory.
	 */
	public static final String WORK_DIRECTORY_PROPERTY = "anvil.workDir";

	/**
	 * Whether unsuccessful workspaces are retained.
	 */
	public static final String KEEP_FAILED_WORKSPACES_PROPERTY = "anvil.keepFailedWorkspaces";

	/**
	 * Prefix for named artifact paths supplied to the engine through JVM properties.
	 */
	public static final String ARTIFACT_PROPERTY_PREFIX = "anvil.artifact.";

	/**
	 * Restricts artifact resolution to previously acquired content and metadata.
	 */
	public static final String OFFLINE_PROPERTY = "anvil.offline";

	/**
	 * Resolves moving artifact selectors again.
	 */
	public static final String REFRESH_PROPERTY = "anvil.refresh";

	/**
	 * Maximum simultaneous artifact transfers.
	 */
	public static final String DOWNLOAD_PARALLELISM_PROPERTY = "anvil.downloadParallelism";

	/**
	 * Explicit Minecraft EULA acceptance.
	 */
	public static final String EULA_ACCEPTED_PROPERTY = "anvil.eula.accepted";

	/**
	 * Maximum concurrent preparation or startup operations.
	 */
	public static final String PARALLELISM_PROPERTY = "anvil.parallelism";

	/**
	 * Combined declared heaps permitted to start concurrently, in MiB.
	 */
	public static final String STARTUP_MEMORY_PROPERTY = "anvil.startupMemoryMegabytes";

	/**
	 * Default process startup timeout as an ISO-8601 duration.
	 */
	public static final String STARTUP_TIMEOUT_PROPERTY = "anvil.startupTimeout";

	/**
	 * Process shutdown timeout as an ISO-8601 duration.
	 */
	public static final String STOP_TIMEOUT_PROPERTY = "anvil.stopTimeout";

	/**
	 * Requests ANSI output from supporting platforms; false leaves their existing defaults unchanged.
	 */
	public static final String CONSOLE_COLORS_PROPERTY = "anvil.console.colors";

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
	 * @throws IllegalArgumentException if a Java version, boolean, timeout, or support policy is invalid
	 */
	public static @NotNull EngineOptions from(@NotNull Properties properties) {
		EngineOptions defaults = EngineDefaults.resolve(EngineOptions.builder().build());
		var builder = EngineOptions.builder()
				.executionProviderId(properties.getProperty(EXECUTION_PROPERTY, defaults.getExecutionProviderId()))
				.protocolLibrary(properties.getProperty(PROTOCOL_LIBRARY_PROPERTY))
				.supportPolicy(supportPolicy(properties, defaults.getSupportPolicy()))
				.javaSelection(javaSelection(properties))
				.downloadJava(booleanValue(properties, AUTO_DOWNLOAD_JAVA_PROPERTY, defaults.isDownloadJava()))
				.cacheDirectory(pathValue(properties, CACHE_DIRECTORY_PROPERTY, defaults.getCacheDirectory()))
				.accountsDirectory(pathValue(properties, ACCOUNTS_DIRECTORY_PROPERTY, defaults.getAccountsDirectory()))
				.workDirectory(pathValue(properties, WORK_DIRECTORY_PROPERTY, defaults.getWorkDirectory()))
				.keepFailedWorkspaces(booleanValue(properties, KEEP_FAILED_WORKSPACES_PROPERTY, defaults.isKeepFailedWorkspaces()))
				.offline(booleanValue(properties, OFFLINE_PROPERTY, false))
				.refresh(booleanValue(properties, REFRESH_PROPERTY, false))
				.downloadParallelism(positive(properties, DOWNLOAD_PARALLELISM_PROPERTY, defaults.getDownloadParallelism()))
				.eulaAccepted(booleanValue(properties, EULA_ACCEPTED_PROPERTY, defaults.isEulaAccepted()))
				.processScheduling(ProcessScheduling.builder()
						.parallelism(positive(properties, PARALLELISM_PROPERTY, defaults.getProcessScheduling().getParallelism()))
						.startupMemoryMegabytes(positive(properties, STARTUP_MEMORY_PROPERTY, defaults.getProcessScheduling().getStartupMemoryMegabytes()))
						.build())
				.processTimeouts(ProcessTimeouts.builder()
						.startup(durationValue(properties, STARTUP_TIMEOUT_PROPERTY, defaults.getProcessTimeouts().getStartup()))
						.shutdown(durationValue(properties, STOP_TIMEOUT_PROPERTY, defaults.getProcessTimeouts().getShutdown()))
						.build())
				.consoleColors(booleanValue(properties, CONSOLE_COLORS_PROPERTY, defaults.isConsoleColors()));

		for (String name : properties.stringPropertyNames().stream().sorted().toList()) {
			if (name.startsWith(ARTIFACT_PROPERTY_PREFIX)) {
				String artifact = name.substring(ARTIFACT_PROPERTY_PREFIX.length());
				if (artifact.isBlank()) throw new IllegalArgumentException("Artifact name must not be blank");

				builder.artifact(artifact, Path.of(properties.getProperty(name)));
				continue;
			}
			if (!name.startsWith(PROTOCOL_RELEASES_PROPERTY_PREFIX)) continue;

			String library = name.substring(PROTOCOL_RELEASES_PROPERTY_PREFIX.length());
			if (library.isBlank()) throw new IllegalArgumentException("Protocol library must not be blank");
			builder.protocolRelease(library, Path.of(properties.getProperty(name)));
		}

		return builder.build();
	}

	/**
	 * Returns the property key for a protocol library's additional release data.
	 *
	 * @param library non-blank protocol-library identifier
	 * @return property key
	 */
	public static @NotNull String protocolReleasesProperty(@NotNull String library) {
		if (library.isBlank()) throw new IllegalArgumentException("Protocol library must not be blank");
		return PROTOCOL_RELEASES_PROPERTY_PREFIX + library;
	}

	/**
	 * Returns the property key for a named artifact.
	 *
	 * @param name non-blank artifact name
	 * @return property key
	 */
	public static @NotNull String artifactProperty(@NotNull String name) {
		if (name.isBlank()) throw new IllegalArgumentException("Artifact name must not be blank");
		return ARTIFACT_PROPERTY_PREFIX + name;
	}

	private static JavaSelection javaSelection(Properties properties) {
		return JavaSelection.builder()
				.requirement(JavaRequirement.builder()
						.featureVersion(optionalPositive(properties))
						.distribution(properties.getProperty(JAVA_DISTRIBUTION_PROPERTY))
						.release(properties.getProperty(JAVA_RELEASE_PROPERTY))
						.build())
				.source(javaSource(properties))
				.build();
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

	private static SupportPolicy supportPolicy(Properties properties, SupportPolicy fallback) {
		String value = properties.getProperty(SUPPORT_POLICY_PROPERTY);
		if (value == null) return fallback;

		for (SupportPolicy policy : SupportPolicy.values())
			if (policy.name().equalsIgnoreCase(value)) return policy;
		throw new IllegalArgumentException(SUPPORT_POLICY_PROPERTY + " must be lenient or strict");
	}

	private static int positive(Properties properties, String key, int fallback) {
		String value = properties.getProperty(key);
		int result = value == null ? fallback : Integer.parseInt(value);
		if (result < 1) throw new IllegalArgumentException(key + " must be positive");
		return result;
	}

	private static Duration durationValue(Properties properties, String key, Duration fallback) {
		String value = properties.getProperty(key);
		if (value == null) return fallback;

		try {
			Duration duration = Duration.parse(value);
			if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException(key + " must be positive");

			return duration;
		} catch (DateTimeParseException failure) {
			throw new IllegalArgumentException(key + " must be an ISO-8601 duration", failure);
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
