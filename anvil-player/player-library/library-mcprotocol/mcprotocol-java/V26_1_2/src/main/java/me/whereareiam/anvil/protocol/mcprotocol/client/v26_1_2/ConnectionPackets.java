package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SynchronizedRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The connection moments of the MCProtocolLib releases from 26.1 on, whose dimension types name the world clock their
 * day follows and tell fast lava by their {@code minecraft:gameplay/fast_lava} environment attribute.
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
	protected @Nullable String knownClock(@NotNull String type) {
		return vanillaClock(type);
	}

	@Override
	protected boolean fastLava(@NotNull String key, @NotNull NbtCompound description) {
		return fastLavaAttribute(key, description);
	}
}
