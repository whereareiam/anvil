package me.whereareiam.anvil.protocol.worker.host;

import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryHost;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.worker.WorkerLibraryProvider;
import me.whereareiam.anvil.protocol.worker.account.HostedAccounts;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Installs every library that plugs into the native worker: the worker's one protocol library registration, which
 * finds each {@link WorkerLibraryProvider} through {@link ServiceLoader} and exposes it as a protocol library of its
 * own. The libraries share the worker's stored accounts, which each of them derives its own session from.
 */
public final class WorkerLibraryHost implements ProtocolLibraryHost {
	@Override
	public @NotNull List<ProtocolLibraryProvider> libraries(@NotNull ClassLoader loader) {
		return List.copyOf(hosted(loader));
	}

	/**
	 * Returns the worker's stored accounts when at least one of its libraries plays online.
	 *
	 * @param loader class loader the registry discovers libraries from
	 * @param accountsDirectory directory of the account files
	 * @return the accounts shared by the hosted libraries that use them, or empty when none does
	 */
	@Override
	public @NotNull Optional<ProtocolAuthentication> authentication(@NotNull ClassLoader loader, @NotNull Path accountsDirectory) {
		List<HostedAccounts.Library> libraries = hosted(loader).stream()
				.flatMap(library -> library.accountLibrary().stream())
				.toList();
		if (libraries.isEmpty()) return Optional.empty();

		return Optional.of(new HostedAccounts(libraries, accountsDirectory));
	}

	/**
	 * Wraps every installed library; each knows all libraries that use stored accounts, which share the account files.
	 */
	private static List<HostedLibraryProvider> hosted(ClassLoader loader) {
		List<HostedLibraryProvider> hosted = new ArrayList<>();
		Supplier<List<HostedAccounts.Library>> accountLibraries = () -> hosted.stream()
				.flatMap(library -> library.accountLibrary().stream())
				.toList();
		for (WorkerLibraryProvider provider : ServiceLoader.load(WorkerLibraryProvider.class, loader))
			hosted.add(new HostedLibraryProvider(provider, accountLibraries));

		return List.copyOf(hosted);
	}
}
