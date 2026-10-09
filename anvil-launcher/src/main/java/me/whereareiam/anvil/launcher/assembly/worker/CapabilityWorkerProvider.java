package me.whereareiam.anvil.launcher.assembly.worker;

import me.whereareiam.anvil.capability.binding.WorkerCapabilities;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Connects capability extension discovery to the native worker of any protocol library.
 * This provider is loaded in the worker JVM, where the selected library release is available; the
 * worker's context names the library and native session type each extension must accept.
 */
public final class CapabilityWorkerProvider implements NativeWorkerProvider {
	@Override
	public @NotNull String id() {
		return "anvil.capabilities";
	}

	@Override
	public @NotNull NativeWorkerExtension<Object> create(@NotNull NativeWorkerContext context) {
		return new CapabilityWorkerExtension(WorkerCapabilities.discover(context.getLibraryId(), context.getNativeSessionType()));
	}
}
