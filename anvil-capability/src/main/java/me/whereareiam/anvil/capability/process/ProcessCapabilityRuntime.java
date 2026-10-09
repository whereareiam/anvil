package me.whereareiam.anvil.capability.process;

import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.capability.CapabilityRuntime;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Validates each logical process's capability graph and owns composition and cleanup across generations.
 */
public final class ProcessCapabilityRuntime {
	private final Map<String, ProcessCapabilities> owners = new LinkedHashMap<>();
	private boolean bound;

	/**
	 * Validates independently supplied process providers without creating capabilities.
	 *
	 * @param providers process names and their already adapted providers, in preparation order
	 */
	public ProcessCapabilityRuntime(
			@NotNull Map<String, ? extends Collection<? extends CapabilityProvider<? extends ProcessCapability, CapabilityContext<ProcessCapability>>>> providers
	) {
		providers.forEach((name, supplied) -> {
			var runtime = new CapabilityRuntime<ProcessCapability, CapabilityContext<ProcessCapability>>(ProcessCapability.class, supplied);
			owners.put(name, new ProcessCapabilities(name, () -> runtime.compose("Process '" + name + "'", Function.identity())));
		});
	}

	/**
	 * Supplies a borrowed logical capability owner to the actual execution generation handles.
	 * Lookup remains unavailable until the managed group's first successful start of that process.
	 *
	 * @param name declared process identity
	 * @return logical owner, or null when no capabilities are installed for this process
	 */
	public @Nullable CapabilityOwner<ProcessCapability> owner(@NotNull String name) {
		return owners.get(name);
	}

	/**
	 * Initializes a logical process owner after its transport first becomes ready.
	 * Repeated readiness notifications preserve previously created capabilities.
	 *
	 * @param name declared process identity
	 */
	public void initialize(@NotNull String name) {
		synchronized (this) {
			if (!bound) throw new IllegalStateException("Bind process capability ownership before startup");
		}

		ProcessCapabilities owner = owners.get(name);
		if (owner != null) owner.initialize();
	}

	/**
	 * Transfers this composition's lifecycle to one prepared process group. Execution handles must
	 * already delegate capability lookup to the corresponding owners returned by owner(name).
	 * Capabilities are initialized after readiness and closed before underlying process finalization.
	 *
	 * @param processes prepared execution group
	 * @return group coordinating readiness and capability lifetime without replacing its handles
	 */
	public synchronized @NotNull ProcessGroup bind(@NotNull ProcessGroup processes) {
		if (bound) throw new IllegalStateException("Process capability ownership was already transferred");

		bound = true;
		return owners.isEmpty()
				? processes
				: new CapabilityProcessGroup(processes, Collections.unmodifiableMap(new LinkedHashMap<>(owners)));
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
