package me.whereareiam.anvil.protocol.adapter.api.capability;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolWorkerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Handles one capability-owned request inside an isolated protocol worker.
 */
public interface ProtocolWorkerOperation {
	/**
	 * Executes an operation for one worker player.
	 *
	 * @param player worker player
	 * @param arguments JSON request arguments
	 * @return JSON operation result
	 * @throws Exception when the request cannot be completed
	 */
	@NotNull JsonNode execute(
			@NotNull ProtocolWorkerPlayer player,
			@NotNull JsonNode arguments
	) throws Exception;
}
