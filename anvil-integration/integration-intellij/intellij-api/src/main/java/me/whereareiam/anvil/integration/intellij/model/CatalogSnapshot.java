package me.whereareiam.anvil.integration.intellij.model;

import java.util.List;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable state of one scenario catalog load, read atomically by the IDE.
 * <p>
 * The source, scenarios, state, and failure always describe the same load, so a reader never combines
 * one source with another source's scenarios.
 */
@Value
@Builder(toBuilder = true)
public class CatalogSnapshot {
	/**
	 * Source of the latest load, or null before the first load.
	 */
	@Nullable ScenarioSource source;
	/**
	 * Immutable descriptors discovered for the source; empty until a load succeeds.
	 */
	@NotNull
	@Builder.Default
	List<ScenarioDescriptor> scenarios = List.of();
	/**
	 * Lifecycle of the latest load.
	 */
	@NotNull CatalogState state;
	/**
	 * Preparation or discovery failure of the latest load, if one occurred.
	 */
	@Nullable String failure;

	/**
	 * Reports whether this snapshot holds a successful load of the given source.
	 *
	 * @param candidate source to compare by stable identity
	 * @return true when the catalog is ready and was loaded from that source
	 */
	public boolean isReadyFor(@NotNull ScenarioSource candidate) {
		return state == CatalogState.READY && isFor(candidate);
	}

	/**
	 * Reports whether this snapshot describes a load of the given source, in any state.
	 *
	 * @param candidate source to compare by stable identity
	 * @return true when the latest load used that source
	 */
	public boolean isFor(@NotNull ScenarioSource candidate) {
		return source != null && source.getId().equals(candidate.getId());
	}
}
