package me.whereareiam.anvil.capability.api.model.platform;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.capability.api.type.PlatformEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A player joining or leaving, as the platform agent of one scenario process reported it.
 */
@Value
@Builder
public class PlatformEvent {
	/**
	 * Scenario name of the process that reported the event.
	 */
	@NotNull String process;

	/**
	 * Whether the process is a proxy, whose events name the backend in {@link #getServer()}.
	 */
	boolean proxy;

	/**
	 * Position of the event among the process's events, increasing with every event, also across restarts of the
	 * process.
	 */
	long position;

	/**
	 * When the process recorded the event.
	 */
	@NotNull Instant at;

	/**
	 * Whether the player joined or left.
	 */
	@NotNull PlatformEventType type;

	/**
	 * The player as this process reports it, which a proxy or login plugin may have changed from what the client
	 * asked for.
	 */
	@NotNull PlayerProfile profile;

	/**
	 * On a proxy, the backend the player joined or left, as the proxy names it, or null for the player joining and
	 * leaving the proxy itself; null on a server, whose events are about the player joining and leaving it.
	 */
	@Nullable String server;
}
