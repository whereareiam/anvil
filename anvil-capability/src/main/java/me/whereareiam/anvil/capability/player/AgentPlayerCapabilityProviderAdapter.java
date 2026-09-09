package me.whereareiam.anvil.capability.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

/**
 * Adapts an agent capability factory to shared player composition using borrowed process request channels.
 */
@RequiredArgsConstructor
public final class AgentPlayerCapabilityProviderAdapter<C extends PlayerCapability> implements PlayerCapabilityProvider<C> {
	private final @NotNull AgentPlayerCapabilityProvider<C> provider;
	private final @NotNull Function<String, RequestChannel> channels;

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return provider.descriptor();
	}

	@Override
	public @NotNull Class<C> capability() {
		return provider.capability();
	}

	@Override
	public @NotNull C create(@NotNull PlayerCapabilityContext context) {
		return provider.create(new Context(context, channels));
	}

	@RequiredArgsConstructor
	private static final class Context implements AgentPlayerCapabilityContext {
		private final @NotNull PlayerCapabilityContext player;
		private final @NotNull Function<String, RequestChannel> channels;

		@Override
		public @NotNull String playerName() {
			return player.playerName();
		}

		@Override
		public @NotNull String clientVersion() {
			return player.clientVersion();
		}

		@Override
		public @NotNull PlayerObservation observation() {
			return player.observation();
		}

		@Override
		public @NotNull RequestChannel channel(@NotNull String processName) {
			return channels.apply(processName);
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return player.findCapability(type);
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			player.onDestroy(action);
		}
	}
}
