package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Configuration of the MCProtocolLib releases from 1.20.5 on: the registry data of every synchronized registry, with
 * each entry's data translated by {@link NbtTranslator}, which the translation also records in the connection's
 * {@link SynchronizedRegistries} for the packets that name an entry by ID. Each release's {@code ConfigurationPackets}
 * reads an entry's data, whose type Minecraft 26.3 widened to any tag.
 */
public abstract class ConfigurationTranslation implements PacketTranslation<Packet> {
	private final SynchronizedRegistries registries;

	/**
	 * Creates the configuration translation of one connection.
	 *
	 * @param registries the connection's registries, which the translation records each registry's entries in
	 */
	protected ConfigurationTranslation(@NotNull SynchronizedRegistries registries) {
		this.registries = registries;
	}

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundRegistryData.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundRegistryDataPacket registry)) return null;

		List<me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry> entries = registry.getEntries()
				.stream()
				.map(entry -> new me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry(
						entry.getId().asString(), data(entry)))
				.toList();
		ClientboundRegistryData decoded = new ClientboundRegistryData(registry.getRegistry().asString(), entries);
		registries.record(decoded);

		return decoded;
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
