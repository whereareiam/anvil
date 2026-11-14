package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The connection moments of the MCProtocolLib releases from 1.20.5 until 26.3, whose registry entries carry their
 * data as a compound.
 */
public final class ConnectionPackets extends ConnectionTranslation {
	@Override
	protected @Nullable NbtMap description(@NotNull RegistryEntry entry) {
		return entry.getData();
	}
}
