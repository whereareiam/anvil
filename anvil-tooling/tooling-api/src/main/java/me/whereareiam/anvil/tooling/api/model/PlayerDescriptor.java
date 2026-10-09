package me.whereareiam.anvil.tooling.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registered player identity. Contributed actions and observations live on the session snapshot.
 */
@Value
@Builder
@Jacksonized
public class PlayerDescriptor {
	/**
	 * Registered player name used to route actions to this player.
	 */
	@NotNull String name;
	/**
	 * Human-readable label, derived from the name when metadata is omitted.
	 */
	@NotNull String displayName;
}
