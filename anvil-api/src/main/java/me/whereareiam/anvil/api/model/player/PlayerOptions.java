package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
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
	@Nullable String clientVersion;
	@Nullable String connectTo;

	@NotNull
	@Builder.Default
	AuthenticationMode authentication = AuthenticationMode.OFFLINE;

	@Nullable String authenticationProfile;
}
