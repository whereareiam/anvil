package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Configuration of the MCProtocolLib releases from 1.21.11 on: the registry data of every synchronized registry, with
 * each entry's data translated by {@link NbtTranslator}. Each release's {@code ConfigurationPackets} reads an entry's
 * data, whose type Minecraft 26.3 widened to any tag.
 */
public abstract class ConfigurationTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundRegistryData.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundRegistryDataPacket registry)) return null;

		List<me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry> entries = registry.getEntries()
				.stream()
				.map(entry -> new me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry(
						entry.getId().asString(), data(entry)))
				.toList();
		return new ClientboundRegistryData(registry.getRegistry().asString(), entries);
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads the data of a registry entry.
	 *
	 * @param entry the release's registry entry
	 * @return the entry's data, or {@code null} when the server left it out
	 */
	protected abstract @Nullable NbtCompound data(@NotNull RegistryEntry entry);

	/**
	 * Translates a compound of an entry's data.
	 *
	 * @param compound the library's compound
	 * @return the protocol's compound
	 */
	protected static @NotNull NbtCompound compound(@NotNull NbtMap compound) {
		return NbtTranslator.compound(compound);
	}
}
