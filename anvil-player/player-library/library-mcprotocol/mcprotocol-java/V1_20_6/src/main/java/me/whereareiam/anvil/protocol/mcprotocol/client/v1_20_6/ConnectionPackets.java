package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import org.jetbrains.annotations.NotNull;

/**
 * The connection moments of the MCProtocolLib releases from 1.20.5 until 1.21.11, whose dimension types name no world
 * clock and tell fast lava by their {@code ultrawarm} flag.
 */
public final class ConnectionPackets extends ConnectionTranslation {
	/**
	 * Creates the connection translation of one connection.
	 *
	 * @param registries the connection's registries
	 * @param level the connection's level
	 */
	public ConnectionPackets(@NotNull SynchronizedRegistries registries, @NotNull ConnectionLevel level) {
		super(registries, level);
	}

	@Override
	protected boolean fastLava(@NotNull String key, @NotNull NbtCompound description) {
		return ultrawarm(key, description);
	}
}
