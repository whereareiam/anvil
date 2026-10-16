package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identity of a simulated player as its client requested it, as the process it joined granted it at login, and
 * as the server it is on reports it.
 *
 * <p>The three profiles differ when a proxy or a login plugin changes the profile: a proxy that renames
 * {@code Steve} to {@code Steve_Premium} grants the new name, and a server behind it reports what the proxy
 * forwarded. A server that keeps another UUID for a player than the one it shows to the player's client reports
 * its own UUID in the observed profile. Anvil finds the player on each process by the UUID it was granted, so
 * players whose names differ only in letter case are never mistaken for one another.</p>
 */
@Value
@Builder(toBuilder = true)
public class PlayerIdentity {
	/**
	 * Profile the client asked for in its login request.
	 */
	@NotNull PlayerProfile requested;

	/**
	 * Profile the joined server or proxy granted at the latest login, or null before the player logged in and
	 * after it disconnected.
	 */
	@Nullable PlayerProfile granted;

	/**
	 * Profile the server the player is on reports, or the proxy's while no server reports the player; null when
	 * no agent observes the player.
	 */
	@Nullable PlayerProfile observed;

	/**
	 * Proxy and server the player is connected through.
	 */
	@NotNull
	@Builder.Default
	PlayerRoute route = PlayerRoute.builder().build();
}
