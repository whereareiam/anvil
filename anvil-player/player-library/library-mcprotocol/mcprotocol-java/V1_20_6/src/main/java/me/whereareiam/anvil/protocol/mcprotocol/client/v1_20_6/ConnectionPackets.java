package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import org.jetbrains.annotations.NotNull;

/**
 * The connection moments of the MCProtocolLib releases from 1.20.5 until 26.1, whose dimension types name no world
 * clock.
 */
public final class ConnectionPackets extends ConnectionTranslation {
	/**
	 * Creates the connection translation of one connection.
	 *
	 * @param registries the connection's registries
	 */
	public ConnectionPackets(@NotNull SynchronizedRegistries registries) {
		super(registries);
	}
}
