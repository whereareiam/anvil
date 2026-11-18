package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration of the MCProtocolLib releases from 1.20.5 until 26.3, whose registry entries carry their data as a
 * compound.
 */
public final class ConfigurationPackets extends ConfigurationTranslation {
	/**
	 * Creates the configuration translation of one connection.
	 *
	 * @param registries the connection's registries
	 */
	public ConfigurationPackets(@NotNull SynchronizedRegistries registries) {
		super(registries);
	}

	@Override
	protected @Nullable NbtCompound data(@NotNull RegistryEntry entry) {
		NbtMap data = entry.getData();
		return data == null ? null : compound(data);
	}
}
