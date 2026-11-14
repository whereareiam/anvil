package me.whereareiam.anvil.capability.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.CapabilitySet;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;
import me.whereareiam.anvil.capability.api.event.lifecycle.ProcessRestarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Owns one logical process's capabilities across process and connection generations, and reports the process's
 * lifecycle events to them.
 */
@RequiredArgsConstructor
final class ProcessCapabilities implements CapabilityOwner<ProcessCapability>, AutoCloseable {
	private final @NotNull String name;
	private final @NotNull Supplier<CapabilitySet<ProcessCapability>> factory;
	private @Nullable CapabilitySet<ProcessCapability> capabilities;

	private boolean closed;
	private volatile boolean failed;

	/**
	 * Creates the capabilities when the process's first generation is ready, and reports {@link ProcessRestarted}
	 * to them when a later one is.
	 *
	 * @throws RuntimeException when creating the capabilities or a restart action failed
	 */
	void initialize() {
		CapabilitySet<ProcessCapability> restarted;
		synchronized (this) {
			requireOpen();
			restarted = capabilities;
			if (restarted == null) {
				try {
					capabilities = factory.get();
				} catch (RuntimeException | Error failure) {
					failed = true;
					throw failure;
				}
				return;
			}
		}

		Throwable failure = restarted.report(ProcessRestarted.INSTANCE, null);
		if (failure instanceof RuntimeException runtime) throw runtime;
		if (failure instanceof Error error) throw error;
	}

	/**
	 * Reports an event to the capabilities, unless they were never created or are already closed.
	 *
	 * @param event event the process reports
	 * @param first failure already seen, to which later failures are added as suppressed
	 * @return the first failure, or null when every action completed and none was seen before
	 */
	@Nullable Throwable report(@NotNull CapabilityEvent event, @Nullable Throwable first) {
		CapabilitySet<ProcessCapability> created;
		synchronized (this) {
			if (closed || capabilities == null) return first;
			created = capabilities;
		}

		return created.report(event, first);
	}

	boolean failed() {
		return failed;
	}

	@Override
	public synchronized @NotNull <C extends ProcessCapability> C capability(@NotNull Class<C> type) {
		requireOpen();
		if (capabilities == null) {
			throw new CapabilityUnavailableException("Process '" + name + "' has not reached initial readiness");
		}

		return capabilities.capability(type);
	}

	@Override
	public synchronized boolean hasCapability(@NotNull Class<? extends ProcessCapability> type) {
		return !closed && capabilities != null && capabilities.hasCapability(type);
	}

	@Override
	public void close() {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		if (capabilities != null) capabilities.close();
	}

	private void requireOpen() {
		if (closed) throw new CapabilityUnavailableException("Process '" + name + "' capabilities are closed");
	}
}
