package me.whereareiam.anvil.capability.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.CapabilitySet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Owns one logical process's capabilities across process and connection generations.
 */
@RequiredArgsConstructor
final class ProcessCapabilities implements CapabilityOwner<ProcessCapability>, AutoCloseable {
	private final @NotNull String name;
	private final @NotNull Supplier<CapabilitySet<ProcessCapability>> factory;
	private @Nullable CapabilitySet<ProcessCapability> capabilities;

	private boolean closed;
	private volatile boolean failed;

	synchronized void initialize() {
		requireOpen();
		if (capabilities != null) return;

		try {
			capabilities = factory.get();
		} catch (RuntimeException | Error failure) {
			failed = true;
			throw failure;
		}
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
