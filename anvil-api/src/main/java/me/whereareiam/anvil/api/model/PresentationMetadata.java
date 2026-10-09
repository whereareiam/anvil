package me.whereareiam.anvil.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Optional presentation details shared by scenarios, groups, processes, and players.
 * These values never replace the owning component's technical name or affect execution.
 * Clients may derive a label from that name when no display name is supplied.
 *
 * <pre>{@code
 * PresentationMetadata.builder()
 *     .displayName("Player Registration")
 *     .category("Authentication")
 *     .tag("proxy")
 *     .build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class PresentationMetadata {
	/**
	 * Human-readable label, or null to use the component's technical name.
	 */
	@Nullable String displayName;

	/**
	 * Explanation of the component's purpose, or null when none is supplied.
	 */
	@Nullable String description;

	/**
	 * Optional presentation group, independent of scenario execution groups.
	 */
	@Nullable String category;

	/**
	 * Immutable labels for filtering; empty when no tags are supplied.
	 */
	@NotNull
	@Singular
	Set<String> tags;
}
