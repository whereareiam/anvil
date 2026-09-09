package me.whereareiam.anvil.capability.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.capability.CapabilitySet;
import org.jetbrains.annotations.NotNull;

/**
 * Owns one logical process's capabilities across process and connection generations.
 */
@RequiredArgsConstructor
final class ProcessCapabilities implements CapabilityOwner<ProcessCapability>, AutoCloseable {
	private final @NotNull String name;
	private final @NotNull CapabilitySet<ProcessCapability> capabilities;

	private boolean closed;

	@Override
	public synchronized @NotNull <C extends ProcessCapability> C capability(@NotNull Class<C> type) {
		requireOpen();
		return capabilities.capability(type);
	}

	@Override
	public synchronized boolean hasCapability(@NotNull Class<? extends ProcessCapability> type) {
		return !closed && capabilities.hasCapability(type);
	}

	@Override
	public void close() {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		capabilities.close();
	}

	private void requireOpen() {
		if (closed) throw new CapabilityUnavailableException("Process '" + name + "' capabilities are closed");
	}
}
