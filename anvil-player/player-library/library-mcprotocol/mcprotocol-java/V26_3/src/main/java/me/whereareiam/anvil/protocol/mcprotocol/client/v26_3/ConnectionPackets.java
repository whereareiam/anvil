package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionTranslation;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The moments the MCProtocolLib 26.3 release joins a level, respawns and returns to the configuration phase. The
 * release types each registry entry's data as any NBT tag; a dimension type writes its entry as a compound, and an
 * entry without data, or with data of another tag, gives no height range.
 */
final class ConnectionPackets extends ConnectionTranslation {
	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, which the translation records from each login and respawn
	 */
	ConnectionPackets(@NotNull ConnectionLevel level) {
		super(level);
	}

	@Override
	protected @Nullable NbtMap data(@NotNull RegistryEntry entry) {
		return entry.getData() instanceof NbtMap compound ? compound : null;
	}
}
