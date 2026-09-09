package me.whereareiam.anvil.agent.api.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.model.transport.AgentPingResponse;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityRequest;
import me.whereareiam.anvil.agent.api.model.transport.identity.AgentIdentityResponse;
import org.jetbrains.annotations.NotNull;

/**
 * Built-in wire contracts shared by agent clients and embedded operation handlers.
 * These reserved names remain stable across agent transports and platform implementations.
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
	 * Looks up the platform's observed player identity. An absent player produces a null response.
	 */
	public static final @NotNull AgentOperation<AgentIdentityRequest, AgentIdentityResponse> IDENTITY =
			AgentOperation.<AgentIdentityRequest, AgentIdentityResponse>builder()
					.name("identity")
					.requestType(AgentIdentityRequest.class)
					.responseType(AgentIdentityResponse.class)
					.build();
}
