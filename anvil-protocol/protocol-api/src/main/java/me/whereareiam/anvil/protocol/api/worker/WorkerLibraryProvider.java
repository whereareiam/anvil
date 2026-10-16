package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Service-provider interface of a protocol library that the native worker hosts. The worker finds every
 * implementation through {@link ServiceLoader} from
 * {@code META-INF/services/me.whereareiam.anvil.protocol.api.worker.WorkerLibraryProvider} and installs each as a
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
	 * Provides how the library derives its session from a stored account, which its players that use an account
	 * sign in with. The worker owns the account files and the sign-in around it, and asks the library what it can do
	 * with each freshly signed-in account. Offline-only libraries keep the empty default.
	 *
	 * @return the library's session derivation, when it plays online
	 */
	default @NotNull Optional<WorkerAuthentication> authentication() {
		return Optional.empty();
	}
}
