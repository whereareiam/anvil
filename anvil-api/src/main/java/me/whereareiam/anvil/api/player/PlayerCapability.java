package me.whereareiam.anvil.api.player;

/**
 * Marker for a typed, player-scoped behavior exposed by Anvil.
 *
 * <p>Capability APIs depend only on the global Anvil API. Runtime provider and protocol modules
 * supply implementations without becoming part of this contract.</p>
 */
public interface PlayerCapability { }
