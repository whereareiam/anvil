package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Identity observed for a simulated player by its client, proxy, and Minecraft server.
 */
@Value
@Builder(toBuilder = true)
public class PlayerIdentity {
	@NotNull String username;
	@NotNull UUID clientUniqueId;
	@Nullable String observedUsername;
	@Nullable UUID observedUniqueId;
	@NotNull
	@Builder.Default
	PlayerRoute route = PlayerRoute.builder().build();

}
