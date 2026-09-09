package me.whereareiam.anvil.capability.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Adapts an agent process factory to shared dependency ordering and cleanup using a borrowed request channel.
 */
@RequiredArgsConstructor
public final class AgentProcessCapabilityProviderAdapter<C extends ProcessCapability> implements CapabilityProvider<C, CapabilityContext<ProcessCapability>> {
	private final @NotNull AgentProcessCapabilityProvider<C> provider;
	private final @NotNull String processName;
	private final @NotNull String platformId;
	private final @NotNull RequestChannel channel;

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return provider.descriptor();
	}

	@Override
	public @NotNull Class<C> capability() {
		return provider.capability();
	}

	@Override
	public @NotNull C create(@NotNull CapabilityContext<ProcessCapability> context) {
		return provider.create(new Context(context));
	}

	@RequiredArgsConstructor
	private final class Context implements AgentProcessCapabilityContext {
		private final @NotNull CapabilityContext<ProcessCapability> capabilities;

		@Override
		public @NotNull String processName() {
			return processName;
		}

		@Override
		public @NotNull String platformId() {
			return platformId;
		}

		@Override
		public @NotNull RequestChannel channel() {
			return channel;
		}

		@Override
		public @NotNull <T extends ProcessCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return capabilities.findCapability(type);
		}

		@Override
		public void onClose(@NotNull Runnable action) {
			capabilities.onClose(action);
		}
	}
}
