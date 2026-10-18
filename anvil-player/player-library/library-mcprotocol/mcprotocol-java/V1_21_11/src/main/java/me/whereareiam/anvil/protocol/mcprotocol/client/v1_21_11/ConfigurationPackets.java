package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration of the MCProtocolLib 1.21.11 release, whose registry entries carry their data as a compound.
 */
public final class ConfigurationPackets extends ConfigurationTranslation {
	@Override
	protected @Nullable NbtCompound data(@NotNull RegistryEntry entry) {
		NbtMap data = entry.getData();
		return data == null ? null : compound(data);
	}
}
