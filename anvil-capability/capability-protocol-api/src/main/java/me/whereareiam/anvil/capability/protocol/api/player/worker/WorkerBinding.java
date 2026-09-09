package me.whereareiam.anvil.capability.protocol.api.player.worker;

/**
 * Owns one player's capability state and native listener registrations across reconnects.
 */
public interface WorkerBinding extends AutoCloseable {
	/**
	 * Releases the capability's listeners and local state when the player is destroyed or a
	 * later binding fails during installation.
	 */
	@Override
	void close();
}
