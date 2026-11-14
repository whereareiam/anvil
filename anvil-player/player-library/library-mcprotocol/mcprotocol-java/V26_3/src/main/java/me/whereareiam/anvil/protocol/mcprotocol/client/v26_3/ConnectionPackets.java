package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionTranslation;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The connection moments of the MCProtocolLib 26.3 release, whose registry entries carry any tag as their data, of
 * which a dimension type's description is a compound.
 */
final class ConnectionPackets extends ConnectionTranslation {
	@Override
	protected @Nullable NbtMap description(@NotNull RegistryEntry entry) {
		return entry.getData() instanceof NbtMap description ? description : null;
	}
}
