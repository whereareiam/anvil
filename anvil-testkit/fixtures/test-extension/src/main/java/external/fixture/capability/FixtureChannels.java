package external.fixture.capability;

import external.fixture.agent.FixtureOperations;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;

final class FixtureChannels {
	static final ChannelOperation<String, String> ECHO = channel(FixtureOperations.ECHO);
	static final ChannelOperation<String, Void> SET_PREFIX = channel(FixtureOperations.SET_PREFIX);
	static final ChannelOperation<Void, String> GET_PREFIX = channel(FixtureOperations.GET_PREFIX);

	private static <Q, R> @NotNull ChannelOperation<Q, R> channel(@NotNull AgentOperation<Q, R> operation) {
		return new ChannelOperation<>(operation.getName(), operation.getRequestType(), operation.getResponseType());
	}
}
