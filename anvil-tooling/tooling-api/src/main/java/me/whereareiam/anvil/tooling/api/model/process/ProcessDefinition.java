package me.whereareiam.anvil.tooling.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable process declaration available before an environment starts.
 * Values describe requested configuration; no distribution or Java installation is resolved.
 * Match the name with a live {@link ProcessSnapshot} after startup to inspect allocated endpoints.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ProcessDefinition {
	/**
	 * Scenario-unique name used for routing and live process lookup.
	 */
	@NotNull String name;

	/**
	 * Resolved presentation label, derived from the name when no explicit label is supplied.
	 */
	@NotNull String displayName;

	/**
	 * Optional explanation supplied by the process declaration.
	 */
	@Nullable String description;

	/**
	 * Server or proxy role, independent of the selected platform implementation.
	 */
	@NotNull ProcessRole role;

	/**
	 * Configured platform-provider identifier.
	 */
	@NotNull String platform;

	/**
	 * Readable declared distribution selector, including a build, checksum, local path, or artifact name.
	 */
	@NotNull String distribution;

	/**
	 * Version from the distribution declaration; a proxy version is not a Minecraft client version.
	 */
	@Nullable String distributionVersion;

	/**
	 * Provider build selector when supplied, without resolving moving selectors.
	 */
	@Nullable String distributionBuild;

	/**
	 * Explicit server Minecraft-version override, or null when the declaration omits it.
	 */
	@Nullable String minecraftVersion;

	/**
	 * Configured maximum heap size in MiB.
	 */
	int memoryMegabytes;

	/**
	 * Whether this process is configured to authenticate players with Mojang services.
	 */
	boolean onlineMode;

	/**
	 * Readable Java requirement and explicit source, identifying inherited scenario or project settings.
	 */
	@NotNull String javaRequirement;

	/**
	 * Immutable backend-name references for a proxy; empty for a server.
	 */
	@NotNull
	@Singular("backendName")
	List<String> backendNames;

	/**
	 * Default backend name for a proxy, or null for a server.
	 */
	@Nullable String defaultBackend;
}
