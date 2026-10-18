package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The packets a client of one MCProtocolLib release from 1.16.5 until 1.17.1 sends on its own where these releases
 * differ: the client information it sends when the game starts, whose fields changed in Minecraft 1.17, and the
 * answers to packets the release must acknowledge, such as a rejected container transaction before Minecraft 1.17.
 */
public interface ClientAnswers {
	/**
	 * Builds the client information the client sends when the game starts.
	 *
	 * @param settings the client's settings
	 * @return the release's client information packet
	 */
	@NotNull Packet clientInformation(@NotNull ClientSettings settings);

	/**
	 * Answers a received packet that the release must acknowledge.
	 *
	 * @param packet a packet the session received
	 * @return the answer to send, or {@code null} when the packet needs none
	 */
	@Nullable Packet answer(@NotNull Packet packet);
}
