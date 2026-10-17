package me.whereareiam.anvil.capability.players.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * One stay of a player on a server or proxy, as that process reported it: the profile it saw, when the player joined,
 * and when it left again.
 */
@Value
@Builder(toBuilder = true)
public class PlayerVisit {
	/**
	 * The player as the process reports it, which a proxy or plugin may have changed from what the client asked for.
	 */
	@NotNull PlayerProfile profile;

	/**
	 * When the player joined the process.
	 */
	@NotNull Instant joinedAt;

	@Getter(AccessLevel.NONE)
	@Nullable Instant leftAt;

	/**
	 * Returns when the player left the process.
	 *
	 * @return when it left, or empty while the player is still there
	 */
	public @NotNull Optional<Instant> getLeftAt() {
		return Optional.ofNullable(leftAt);
	}
}
