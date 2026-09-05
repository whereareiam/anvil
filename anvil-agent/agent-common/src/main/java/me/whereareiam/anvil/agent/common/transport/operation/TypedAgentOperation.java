package me.whereareiam.anvil.agent.common.transport.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import org.jetbrains.annotations.NotNull;

/**
 * Converts transport values at the boundary of an external typed operation.
 */
@RequiredArgsConstructor
public final class TypedAgentOperation<Q, R> implements AgentRequestHandler {
	private final AgentOperation<Q, R> operation;
	private final AgentOperationHandler<Q, R> handler;
	private final ObjectMapper mapper;

	@Override
	public @NotNull String wireName() {
		return operation.getName();
	}

	@Override
	public @NotNull JsonNode handle(@NotNull PlatformAgent platform, @NotNull JsonNode arguments) throws Exception {
		Q request = mapper.treeToValue(arguments, operation.getRequestType());
		if (request == null)
			throw new IllegalArgumentException("Missing request for agent operation '" + wireName() + "'");
		return mapper.valueToTree(handler.execute(platform, request));
	}
}
