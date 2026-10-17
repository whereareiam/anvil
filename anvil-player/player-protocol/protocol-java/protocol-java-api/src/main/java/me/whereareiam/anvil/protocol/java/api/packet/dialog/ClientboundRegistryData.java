package me.whereareiam.anvil.protocol.java.api.packet.dialog;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.dialog.RegistryDialog;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entries of the dialog registry, which the server sends during configuration. The game's registry data packet
 * carries every synchronized registry, one per packet; the protocol carries only the {@code minecraft:dialog}
 * registry's, so the packets of other registries are not delivered. Releases before Minecraft 1.21.6 have no dialog
 * registry.
 */
@Value
public class ClientboundRegistryData implements ClientboundPacket {
	/**
	 * The registry's entries in registry order, so the index of an entry is the registry ID a show-dialog packet
	 * refers to it by.
	 */
	@NotNull List<RegistryDialog> dialogs;
}
