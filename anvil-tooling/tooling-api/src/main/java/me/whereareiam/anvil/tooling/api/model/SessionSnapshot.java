package me.whereareiam.anvil.tooling.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDescriptor;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable state of one runner session; retained after stop or failure.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class SessionSnapshot {
	/**
	 * Identity allocated for this environment, or null before the first start.
	 */
	@Nullable String sessionId;
	/**
	 * Fully qualified definition class that owns the selected scenario, or null before selection.
	 */
	@Nullable String definition;
	/**
	 * Selected scenario's stable name, or null before selection.
	 */
	@Nullable String scenario;
	/**
	 * Declared default process used for joining the environment.
	 */
	@Nullable String entrypoint;
	/**
	 * Selected scenario's resolved presentation label.
	 */
	@Nullable String displayName;
	/**
	 * Foreground environment lifecycle, distinct from an automated test's result.
	 */
	@NotNull SessionState state;
	/**
	 * Whether whole-scenario startup and its setup hook have completed for this run.
	 * Individual process starts leave this false until the scenario is started as a whole.
	 */
	boolean setupComplete;
	/**
	 * Observed process generations, including retained final states after cleanup.
	 */
	@NotNull
	@Builder.Default
	List<ProcessSnapshot> processes = List.of();
	/**
	 * Currently registered player identities.
	 */
	@NotNull
	@Builder.Default
	List<PlayerDescriptor> players = List.of();
	/**
	 * Contributed actions bound to supported runtime targets with their current availability.
	 */
	@NotNull
	@Builder.Default
	List<ActionDescriptor> actions = List.of();

	/**
	 * Portable contributed values for scenarios, processes, and players.
	 */
	@NotNull
	@Builder.Default
	List<ObservationDescriptor> observations = List.of();

	/**
	 * Retained lifecycle failure diagnostics, or null when no failure has been recorded.
	 */
	@Nullable String failure;

}
