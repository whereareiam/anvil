package me.whereareiam.anvil.integration.intellij.model.source;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.exception.ProjectSyncException;
import me.whereareiam.anvil.integration.intellij.type.source.DiscoveryState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable source selection and discovery progress, independent of any UI component.
 */
@Value
@Builder(toBuilder = true)
public class DiscoverySnapshot {
	/**
	 * Imported source metadata from the latest completed discovery.
	 */
	@NotNull SourceListing listing;
	/**
	 * Selected source, or null when no supported source is available.
	 */
	@Nullable ScenarioSource selectedSource;
	/**
	 * Operation currently inspecting or syncing the project.
	 */
	@NotNull DiscoveryState state;
	/**
	 * Whether the installed build integration supports native sync.
	 */
	boolean canSync;
	/**
	 * Latest native sync failure and its details, if any.
	 */
	@Nullable ProjectSyncException syncFailure;
	/**
	 * Latest source inspection or catalog-submission diagnostic, if any.
	 */
	@Nullable String failure;
}
