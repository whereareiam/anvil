package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientSettings;
import org.jetbrains.annotations.NotNull;

/**
 * The client information a client of one MCProtocolLib release from 1.16.5 until 1.17.1 sends when the game starts,
 * whose fields changed in Minecraft 1.17.
 */
public interface ClientInformation {
	/**
	 * Builds the client information the client sends when the game starts.
	 *
	 * @param settings the client's settings
	 * @return the release's client information packet
	 */
	@NotNull Packet packet(@NotNull ClientSettings settings);
}
