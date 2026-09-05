package me.whereareiam.anvil.protocol.adapter.api.capability;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolWorkerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Observes inbound backend packets for capability-owned state and events.
 */
public interface ProtocolPacketListener {
	/**
	 * Handles one inbound packet.
	 *
	 * @param player worker player
	 * @param packet opaque backend packet
	 */
	void received(@NotNull ProtocolWorkerPlayer player, @NotNull Object packet);
}
