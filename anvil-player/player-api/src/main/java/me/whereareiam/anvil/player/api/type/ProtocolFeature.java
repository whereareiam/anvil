package me.whereareiam.anvil.player.api.type;

/**
 * Release-specific behavior that a protocol library guarantees for its clients.
 * Features describe the native client itself and never select player capability providers.
 */
public enum ProtocolFeature {
	/**
	 * Licensed Java Edition online-mode authentication.
	 */
	ONLINE_AUTHENTICATION,
	/**
	 * A client that presents itself as NeoForge: it claims a player's payload channels and answers NeoForge's
	 * configuration.
	 */
	NEOFORGE_CLIENT
}
