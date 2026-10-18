package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The moments the MCProtocolLib releases from 1.20.5 until 26.2 join a level, respawn and return to the configuration
 * phase; their registry entries carry their data as a compound.
 */
public final class ConnectionPackets extends ConnectionTranslation {
	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, which the translation records from each login and respawn
	 */
	public ConnectionPackets(@NotNull ConnectionLevel level) {
		super(level);
	}

	@Override
	protected @Nullable NbtMap data(@NotNull RegistryEntry entry) {
		return entry.getData();
	}
}
