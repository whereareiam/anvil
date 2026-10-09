package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional overrides used when creating one simulated player in a running scenario.
 */
@Value
@Builder(toBuilder = true)
public class PlayerOptions {
	@NotNull String name;

	/**
	 * Optional labels for tooling; the player name remains its connection identity.
	 */
	@Nullable PresentationMetadata metadata;

	/**
	 * Native Minecraft version; when set it must equal the version of every server the player can reach.
	 */
	@Nullable String clientVersion;
	@Nullable String connectTo;

	/**
	 * Protocol library for this player, overriding the scenario and engine defaults.
	 */
	@Nullable String protocolLibrary;

	@NotNull
	@Builder.Default
	AuthenticationMode authentication = AuthenticationMode.OFFLINE;

	/** Local account identifier resolved by the selected protocol library. */
	@Nullable String accountId;
}
