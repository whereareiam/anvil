package external.tooling;

import me.whereareiam.anvil.api.player.PlayerCapability;

/**
 * Example capability owned entirely by an external project.
 */
public interface Counter extends PlayerCapability {
	/**
	 * Adds a delta to this player's counter.
	 * @param amount signed increment
	 * @return updated value
	 */
	long add(long amount);

	/**
	 * Returns the current value.
	 * @return player-owned counter
	 */
	long value();
}
