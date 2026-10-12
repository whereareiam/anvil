package me.whereareiam.anvil.protocol.worker.host;

import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryHost;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.worker.WorkerLibraryProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Installs every library that plugs into the native worker: the worker's one protocol library registration, which
 * finds each {@link WorkerLibraryProvider} through {@link ServiceLoader} and exposes it as a protocol library of its
 * own.
 */
public final class WorkerLibraryHost implements ProtocolLibraryHost {
	@Override
	public @NotNull List<ProtocolLibraryProvider> libraries(@NotNull ClassLoader loader) {
		return ServiceLoader.load(WorkerLibraryProvider.class, loader).stream()
				.map(provider -> (ProtocolLibraryProvider) new HostedLibraryProvider(provider.get()))
				.toList();
	}
}
