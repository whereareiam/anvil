package me.whereareiam.anvil.agent.api.model.transport.event;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Wire request for the events an agent recorded after a position.
 */
@Value
@Builder
@Jacksonized
public class AgentPlayerEventsRequest {
	/**
	 * Position of the last event the caller already has, or zero for every kept event.
	 */
	long after;
}
