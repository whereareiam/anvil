package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionTranslation;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The connection moments of the MCProtocolLib 26.1 release, whose registry entries carry their data as a compound and
 * whose dimension types name the world clock their day follows.
 */
public final class ConnectionPackets extends ConnectionTranslation {
	@Override
	protected @Nullable NbtMap description(@NotNull RegistryEntry entry) {
		return entry.getData();
	}

	@Override
	protected @Nullable String knownClock(@NotNull String type) {
		return vanillaClock(type);
	}
}
