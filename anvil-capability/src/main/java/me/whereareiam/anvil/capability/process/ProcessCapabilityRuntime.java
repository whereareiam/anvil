package me.whereareiam.anvil.capability.process;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.capability.CapabilityRuntime;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Validates each logical process's capability graph and owns composition and cleanup across generations.
 */
public final class ProcessCapabilityRuntime {
	private final Map<String, CapabilityRuntime<ProcessCapability, CapabilityContext<ProcessCapability>>> owners = new LinkedHashMap<>();

	/**
	 * Validates independently supplied process providers without creating capabilities.
	 *
	 * @param providers process names and their already adapted providers, in preparation order
	 */
	public ProcessCapabilityRuntime(
			@NotNull Map<String, ? extends Collection<? extends CapabilityProvider<? extends ProcessCapability, CapabilityContext<ProcessCapability>>>> providers
	) {
		providers.forEach((name, supplied) -> owners.put(name, new CapabilityRuntime<>(ProcessCapability.class, supplied)));
	}

	/**
	 * Creates capabilities using the prepared provider contexts and transfers their lifetime to the group.
	 * If composition fails, releases all created capabilities while leaving process rollback to the caller.
	 *
	 * @param processes ready process group whose provider inputs are available
	 * @return process views owning the composed process capabilities
	 */
	public @NotNull ProcessGroup bind(@NotNull ProcessGroup processes) {
		if (owners.isEmpty()) return processes;

		Map<String, ProcessCapabilities> capabilities = new LinkedHashMap<>();
		try {
			owners.forEach((name, runtime) -> capabilities.put(name,
					new ProcessCapabilities(name, runtime.compose("Process '" + name + "'", Function.identity()))));

			return new CapabilityProcessGroup(processes, capabilities);
		} catch (RuntimeException | Error failure) {
			close(capabilities.values(), failure);
			throw failure;
		}
	}

	static @Nullable Throwable close(@NotNull Collection<ProcessCapabilities> capabilities, @Nullable Throwable first) {
		for (ProcessCapabilities owner : new ArrayList<>(capabilities).reversed())
			try {
				owner.close();
			} catch (RuntimeException | Error failure) {
				if (first == null) first = failure;
				else if (first != failure) first.addSuppressed(failure);
			}

		return first;
	}

}
