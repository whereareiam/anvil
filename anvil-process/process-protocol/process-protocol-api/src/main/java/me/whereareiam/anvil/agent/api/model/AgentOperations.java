package me.whereareiam.anvil.agent.api.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.model.transport.AgentPingResponse;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsRequest;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import org.jetbrains.annotations.NotNull;

/**
 * Built-in wire contracts shared by agent clients and embedded operation handlers: the few general facts every
 * platform agent provides once, so capabilities and tests do not each reimplement them. The vocabulary stays small on
 * purpose, readiness, console commands and the joins and leaves of players' connections; anything else belongs to an
 * agent extension. These reserved names remain stable across agent transports and platform implementations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentOperations {
	/**
	 * Reports platform identity during connection readiness checks. The request is an empty object.
	 */
	public static final @NotNull AgentOperation<Object, AgentPingResponse> PING =
			AgentOperation.<Object, AgentPingResponse>builder()
					.name("ping")
					.requestType(Object.class)
					.responseType(AgentPingResponse.class)
					.build();

	/**
	 * Executes a command through the platform console and reports whether it was accepted.
	 */
	public static final @NotNull AgentOperation<AgentCommandRequest, AgentCommandResponse> COMMAND =
			AgentOperation.<AgentCommandRequest, AgentCommandResponse>builder()
					.name("command")
					.requestType(AgentCommandRequest.class)
					.responseType(AgentCommandResponse.class)
					.build();

	/**
	 * Lists the players' joins and leaves the platform agent recorded after a position.
	 */
	public static final @NotNull AgentOperation<AgentPlayerEventsRequest, AgentPlayerEventsResponse> PLAYER_EVENTS =
			AgentOperation.<AgentPlayerEventsRequest, AgentPlayerEventsResponse>builder()
					.name("player-events")
					.requestType(AgentPlayerEventsRequest.class)
					.responseType(AgentPlayerEventsResponse.class)
					.build();
}
