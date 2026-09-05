package me.whereareiam.anvil.agent.api.model.transport.command;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Wire response for a platform console command.
 */
@Value
@Builder
@Jacksonized
public class AgentCommandResponse {
	/**
	 * Whether the platform accepted the command.
	 */
	boolean accepted;
}
