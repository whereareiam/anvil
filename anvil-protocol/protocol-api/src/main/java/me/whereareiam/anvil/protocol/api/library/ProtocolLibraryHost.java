package me.whereareiam.anvil.protocol.api.library;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Service-provider interface of a runtime that hosts several protocol libraries behind one installation, such as
 * the native worker, which runs every library that plugs into it in worker JVMs of its own.
 *
 * <p>{@link ProtocolLibraryRegistry#discover()} finds hosts through {@link ServiceLoader} from
 * {@code META-INF/services/me.whereareiam.anvil.protocol.api.library.ProtocolLibraryHost}, next to the directly
 * registered {@link ProtocolLibraryProvider} implementations, and indexes every library a host returns like a
 * directly registered one. A library is registered either directly or through a host, never both.</p>
 *
 * <pre>{@code
 * public final class ExampleLibraryHost implements ProtocolLibraryHost {
 *     public List<ProtocolLibraryProvider> libraries(ClassLoader loader) {
 *         return ServiceLoader.load(ExampleLibrary.class, loader).stream()
 *                 .map(library -> (ProtocolLibraryProvider) new HostedLibrary(library.get()))
 *                 .toList();
 *     }
 * }
 * }</pre>
 */
public interface ProtocolLibraryHost {
	/**
	 * Returns the libraries this host runs that are installed for a class loader, without creating them.
	 *
	 * @param loader class loader the registry discovers libraries from
	 * @return providers of the hosted libraries, in discovery order
	 */
	@NotNull List<ProtocolLibraryProvider> libraries(@NotNull ClassLoader loader);

	/**
	 * Provides the account workflow that the host's libraries share: an account is one Microsoft account, signed in
	 * once, from which each of the libraries derives its own session. Creates neither a library nor a player.
	 * A host whose libraries play offline only keeps the empty default.
	 *
	 * @param loader class loader the registry discovers libraries from
	 * @param accountsDirectory directory of the stored account files
	 * @return the shared account workflow, when the host's libraries use online accounts
	 */
	default @NotNull Optional<ProtocolAuthentication> authentication(@NotNull ClassLoader loader, @NotNull Path accountsDirectory) {
		return Optional.empty();
	}
}
