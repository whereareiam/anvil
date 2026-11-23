package me.whereareiam.anvil.agent.server.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Converts transport values at the boundary of an external typed operation.
 */
@RequiredArgsConstructor
public final class TypedAgentOperation<Q, R> {
	private final @NotNull AgentOperation<Q, R> operation;
	private final @NotNull AgentOperationHandler<Q, R> handler;
	private final @NotNull ObjectMapper mapper;

	public @NotNull String wireName() {
		return operation.name();
	}

	public @NotNull JsonNode handle(@NotNull PlatformAgent platform, @NotNull JsonNode arguments) throws Exception {
		Q request = arguments.isNull() || arguments.isMissingNode()
				? null : mapper.treeToValue(arguments, operation.requestType());
		if (request == null && operation.requestType() != Void.class)
			throw new IllegalArgumentException("Missing request for agent operation '" + wireName() + "'");
		return mapper.valueToTree(handler.execute(platform, request));
	}
}
