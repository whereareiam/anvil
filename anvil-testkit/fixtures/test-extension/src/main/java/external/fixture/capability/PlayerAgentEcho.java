package external.fixture.capability;

import me.whereareiam.anvil.api.player.PlayerCapability;
import org.jetbrains.annotations.NotNull;

/**
 * Lets a player capability call a native operation independently of the selected packet backend.
 */
public interface PlayerAgentEcho extends PlayerCapability {
	/**
	 * Returns the identity supplied by this player's shared capability dependency.
	 *
	 * @return player name and selected client version
	 */
	@NotNull String label();

	/**
	 * Returns the response from the named process's fixture handler.
	 *
	 * @param process scenario process name
	 * @param value request text
	 * @return handler response
	 */
	@NotNull String echo(@NotNull String process, @NotNull String value);
}
