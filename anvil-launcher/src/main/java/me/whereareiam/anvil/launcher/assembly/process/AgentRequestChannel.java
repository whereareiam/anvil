package me.whereareiam.anvil.launcher.assembly.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sends capability requests through a borrowed process connection without exposing its lifecycle.
 */
@RequiredArgsConstructor
public final class AgentRequestChannel implements RequestChannel {
	private final @NotNull AgentConnection connection;

	@Override
	public @Nullable <Q, R> R request(@NotNull ChannelOperation<Q, R> operation, @Nullable Q request) {
		Q checked = operation.getRequestType().cast(request);
		if (checked == null && operation.getRequestType() != Void.class)
			throw new IllegalArgumentException("Missing request for capability operation '" + operation.getId() + "'");

		return connection.request(operation.getId(), checked, operation.getResponseType());
	}
}
