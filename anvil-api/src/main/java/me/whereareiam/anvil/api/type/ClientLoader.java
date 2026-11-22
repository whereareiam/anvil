package me.whereareiam.anvil.api.type;

/**
 * The mod loader a simulated player's client presents itself as to the server it joins.
 */
public enum ClientLoader {
	/**
	 * A vanilla client: it claims no mod channels, so a server whose mods need a matching mod on the client refuses
	 * it.
	 */
	VANILLA,
	/**
	 * A NeoForge client: it claims the payload channels of the mods it is said to have and answers NeoForge's
	 * configuration as NeoForge does, so it joins a NeoForge server whose mods need their client counterparts.
	 */
	NEOFORGE
}
