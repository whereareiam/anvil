package me.whereareiam.anvil.capability;

import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Collects the agent extensions that the installed agent-backed process providers declare, so that
 * platform planning installs them into the processes of their platforms.
 */
public final class AgentExtensions {
	private final Set<AgentExtension> extensions;

	private AgentExtensions(@NotNull Set<AgentExtension> extensions) {
		this.extensions = Set.copyOf(extensions);
	}

	/**
	 * Holds a fixed set of extensions, such as those an assembly declares itself.
	 *
	 * @param extensions agent extensions
	 * @return the extensions
	 */
	public static @NotNull AgentExtensions of(@NotNull Collection<AgentExtension> extensions) {
		return new AgentExtensions(new LinkedHashSet<>(extensions));
	}

	/**
	 * Discovers the agent-backed providers installed on the current context class loader, or on this class's loader
	 * when the thread has none.
	 *
	 * @return the extensions the providers declare
	 */
	public static @NotNull AgentExtensions discover() {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		return discover(context == null ? AgentExtensions.class.getClassLoader() : context);
	}

	/**
	 * Discovers the agent-backed providers installed on a class loader.
	 *
	 * @param loader class loader to search
	 * @return the extensions the providers declare
	 */
	public static @NotNull AgentExtensions discover(@NotNull ClassLoader loader) {
		Set<AgentExtension> extensions = new LinkedHashSet<>();
		for (AgentProcessCapabilityProvider<?> provider : ServiceLoader.load(AgentProcessCapabilityProvider.class, loader))
			extensions.addAll(provider.agentExtensions());

		return new AgentExtensions(extensions);
	}

	/**
	 * Returns the extensions the processes of one platform install.
	 *
	 * @param platformId platform provider ID declared by the process, such as {@code paper}
	 * @return extensions for the platform
	 */
	public @NotNull List<AgentExtension> forPlatform(@NotNull String platformId) {
		return extensions.stream().filter(extension -> extension.getPlatforms().contains(platformId)).toList();
	}
}
