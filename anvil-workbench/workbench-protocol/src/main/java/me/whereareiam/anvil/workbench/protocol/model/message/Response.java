package me.whereareiam.anvil.workbench.protocol.model.message;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * The answer to one request: its result, or its failure.
 */
@Value
public class Response implements ServerMessage {
	/**
	 * Number of the request.
	 */
	long id;

	/**
	 * The result in the request's answer type, or null on failure.
	 */
	@Nullable JsonNode result;

	/**
	 * The failure, or null on success.
	 */
	@Nullable Failure failure;
}
