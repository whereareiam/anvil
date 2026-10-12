package me.whereareiam.anvil.protocol.worker;

import me.whereareiam.anvil.protocol.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Service-provider interface of a protocol library that the native worker hosts. The worker finds every
 * implementation through {@link ServiceLoader} from
 * {@code META-INF/services/me.whereareiam.anvil.protocol.worker.WorkerLibraryProvider} and installs each as a
 * protocol library of its own: on the host it reads the library's release data, selects the worker class path and
 * starts one worker JVM per release; in that JVM it loads the same provider again and drives the client that the
 * library builds from its client segment.
 *
 * <p>The worker creates a provider in both JVMs, so a provider must be cheap to construct and touch no class of a
 * release closure until {@link #client(Object)} is called.</p>
 *
 * <pre>{@code
 * public final class ExampleWorkerLibrary implements WorkerLibraryProvider {
 *     public WorkerLibrary library() { return LIBRARY; }
 *     public WorkerClient client(Object adapter) { return new ExampleWorkerClient((ExampleClient) adapter); }
 * }
 * }</pre>
 */
public interface WorkerLibraryProvider {
	/**
	 * Describes the library to the worker.
	 *
	 * @return the library's description; its id is the protocol library id that declarations select
	 */
	@NotNull WorkerLibrary library();

	/**
	 * Creates the worker's client of the loaded release, called once in the worker JVM after the segments are
	 * verified.
	 *
	 * @param adapter the implementation of {@link WorkerLibrary#getClientPort()} that the release's client segment
	 * provides
	 * @return client the worker drives the players' native sessions through
	 */
	@NotNull WorkerClient client(@NotNull Object adapter);

	/**
	 * Provides the library's optional account workflow, used by login and logout and to sign in players that use a
	 * stored account. Offline-only libraries keep the empty default.
	 *
	 * @param accountsDirectory local account store directory
	 * @return the library's account workflow, when it has one
	 */
	default @NotNull Optional<WorkerAuthentication> authentication(@NotNull Path accountsDirectory) {
		return Optional.empty();
	}
}
