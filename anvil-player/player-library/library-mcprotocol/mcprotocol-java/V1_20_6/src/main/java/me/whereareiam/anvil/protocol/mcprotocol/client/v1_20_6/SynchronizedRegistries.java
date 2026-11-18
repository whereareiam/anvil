package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The registries the configuration of one connection synchronized, from Minecraft 1.20.2 on, which name their entries
 * by ID in later packets: a login names its dimension type, and from Minecraft 26.1 the time names its world clocks.
 * The release's {@link ConfigurationTranslation} records each registry's entries as its data arrives, and replaces
 * them when a later configuration sends the registry again; the translations of the packets that refer to an entry
 * look it up here, so no translation reads another one's packets. One record belongs to one connection's packet table
 * and is used only on the thread that reads its packets.
 *
 * <pre>{@code
 * SynchronizedRegistries registries = new SynchronizedRegistries();
 * PacketTable<Packet> packets = new PacketTable<>(List.of(
 *         new ConfigurationPackets(registries),
 *         new ConnectionPackets(registries)));
 * }</pre>
 */
public final class SynchronizedRegistries {
	private final Map<String, List<RegistryEntry>> registries = new HashMap<>();

	/**
	 * Records the entries of one registry, in registry order.
	 *
	 * @param registry the registry data the server sent
	 */
	void record(@NotNull ClientboundRegistryData registry) {
		registries.put(registry.getRegistry(), registry.getEntries());
	}

	/**
	 * Looks up the entry a packet refers to by its registry ID.
	 *
	 * @param registry the registry's key with its namespace, such as {@code minecraft:dimension_type}
	 * @param id the entry's index in the registry's order
	 * @return the entry, or {@code null} when the configuration did not synchronize the registry or listed fewer
	 * entries
	 */
	public @Nullable RegistryEntry entry(@NotNull String registry, int id) {
		List<RegistryEntry> entries = registries.getOrDefault(registry, List.of());
		return id < 0 || id >= entries.size() ? null : entries.get(id);
	}
}
