package me.whereareiam.anvil.agent.api.model.transport.command;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Wire request for executing a platform console command.
 */
@Value
@Builder
@Jacksonized
public class AgentCommandRequest {
	/**
	 * Command without a leading slash.
	 */
	@NotNull String command;
}
