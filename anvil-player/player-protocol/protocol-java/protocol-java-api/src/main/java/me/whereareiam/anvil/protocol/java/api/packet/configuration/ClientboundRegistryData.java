package me.whereareiam.anvil.protocol.java.api.packet.configuration;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entries of one synchronized registry, such as {@code minecraft:dialog} or {@code minecraft:dimension_type},
 * which the server sends during configuration, one packet per registry. Releases before Minecraft 1.20.2 have no
 * configuration phase.
 */
@Value
public class ClientboundRegistryData implements ClientboundPacket {
	/**
	 * The registry's key with its namespace, such as {@code minecraft:dialog}.
	 */
	@NotNull String registry;
	/**
	 * The registry's entries in registry order, so the index of an entry is the registry ID a packet refers to it by,
	 * such as a show-dialog packet.
	 */
	@NotNull List<RegistryEntry> entries;
}
