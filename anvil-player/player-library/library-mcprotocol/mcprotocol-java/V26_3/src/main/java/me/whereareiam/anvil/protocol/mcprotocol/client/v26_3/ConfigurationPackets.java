package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ConfigurationTranslation;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration of the MCProtocolLib 26.3 release, which types each registry entry's data as any NBT tag. Every
 * registry the game synchronizes writes its entries as compounds; an entry without data, or with data of another tag,
 * carries none.
 */
final class ConfigurationPackets extends ConfigurationTranslation {
	@Override
	protected @Nullable NbtCompound data(@NotNull RegistryEntry entry) {
		return entry.getData() instanceof NbtMap compound ? compound(compound) : null;
	}
}
