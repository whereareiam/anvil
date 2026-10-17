package me.whereareiam.anvil.agent.server.operation;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsRequest;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.server.event.PlatformEventLog;
import org.jetbrains.annotations.NotNull;

/**
 * Answers the players' joins and leaves the endpoint recorded after a position.
 */
@RequiredArgsConstructor
public final class PlayerEventsHandler implements AgentOperationHandler<AgentPlayerEventsRequest, AgentPlayerEventsResponse> {
	private final @NotNull PlatformEventLog events;

	@Override
	public @NotNull AgentPlayerEventsResponse execute(@NotNull PlatformAgent platform, @NotNull AgentPlayerEventsRequest request) {
		return events.after(request.getAfter());
	}
}
