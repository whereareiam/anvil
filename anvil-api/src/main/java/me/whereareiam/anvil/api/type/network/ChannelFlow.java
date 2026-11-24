package me.whereareiam.anvil.api.type.network;

/**
 * The one direction a mod's payload channel carries payloads in.
 */
public enum ChannelFlow {
	/**
	 * From the server to the client.
	 */
	CLIENTBOUND,
	/**
	 * From the client to the server.
	 */
	SERVERBOUND
}
