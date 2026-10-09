package me.whereareiam.anvil.tooling.api.model.scenario;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Discovered environment identity, optional presentation, and declared process topology.
 * Discovery does not start the environment or evaluate its setup hook.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ScenarioDescriptor {
	/**
	 * Fully qualified definition class that owns this scenario.
	 * The class name is the stable selection identity within the prepared project runtime.
	 */
	@NotNull String definition;

	/**
	 * Stable scenario name returned by the definition.
	 */
	@NotNull String name;

	/**
	 * Resolved presentation label, derived from the stable name when metadata is omitted.
	 */
	@NotNull String displayName;

	/**
	 * Optional explanation supplied by the scenario author.
	 */
	@Nullable String description;

	/**
	 * Optional presentation category; it does not merge independent environments.
	 */
	@Nullable String category;

	/**
	 * Immutable optional labels for filtering and presentation.
	 */
	@NotNull
	@Singular("tag")
	List<String> tags;

	/**
	 * Declared default connection target, or null while only the scenario identity is known.
	 * Saved IDE selections may precede scenario loading; an exported declaration supplies this value.
	 */
	@Nullable String entrypoint;

	/**
	 * Immutable server and proxy declarations in their declared order, with servers first.
	 */
	@NotNull
	@Singular("process")
	List<ProcessDefinition> processes;

	/**
	 * Whether the declaration enables manual-scenario policies such as explicitly requested LAN access.
	 */
	boolean manual;

	/**
	 * Whether the scenario declares setup behavior beyond starting its processes.
	 */
	boolean setupAvailable;

	/**
	 * Declared startup deadline in milliseconds, or zero when inherited or not loaded.
	 */
	long startupTimeoutMillis;

	/**
	 * Declared shutdown grace period in milliseconds, or zero when inherited or not loaded.
	 */
	long shutdownTimeoutMillis;

	/**
	 * Declared execution-provider identifier. Null means the engine default is inherited
	 * or the declaration has not been loaded yet.
	 */
	@Nullable String executionProviderId;

	/**
	 * Readable declared Java requirement and source; project inheritance remains explicit.
	 * Null means the declaration has not been loaded yet.
	 */
	@Nullable String javaRequirement;

}
