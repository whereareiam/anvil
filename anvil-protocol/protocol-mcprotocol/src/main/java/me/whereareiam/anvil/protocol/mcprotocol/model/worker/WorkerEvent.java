package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Namespaced observation emitted for one player.
 */
@Value
@Builder
@Jacksonized
public class WorkerEvent implements WorkerMessage {
	@NotNull String event;
	@NotNull String player;
	@NotNull JsonNode payload;
}
