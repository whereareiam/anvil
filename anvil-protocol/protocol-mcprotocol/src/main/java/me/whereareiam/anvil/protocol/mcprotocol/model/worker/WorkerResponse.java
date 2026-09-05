package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

/**
 * Result or failure correlated with exactly one host request.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkerResponse implements WorkerMessage {
	long id;
	boolean success;
	@Nullable JsonNode result;
	@Nullable String error;
}
