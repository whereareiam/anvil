package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One private stdin request. Arguments may contain credentials and must not be logged.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkerRequest {
	long id;
	@NotNull String operation;
	@Nullable String player;
	@ToString.Exclude
	@NotNull JsonNode arguments;
}
