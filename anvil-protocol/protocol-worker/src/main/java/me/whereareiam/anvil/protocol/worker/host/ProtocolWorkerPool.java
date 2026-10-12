package me.whereareiam.anvil.protocol.worker.host;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.worker.catalog.ReleaseCatalog;
import me.whereareiam.anvil.protocol.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.worker.model.ReleaseDefinition;
import me.whereareiam.anvil.protocol.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A protocol library whose players run in isolated workers: it creates players of the releases in its catalog and
 * shares one worker process per selected release. A release's runtime closure is cached under
 * {@code <cache>/protocol/<library id>/<release version>/<file>}.
 *
 * <pre>{@code
 * ProtocolLibrary library = new ProtocolWorkerPool(
 *         workerLibrary,
 *         context.getCacheDirectory(),
 *         ReleaseCatalog.load(workerLibrary, context.getAdditionalReleases()),
 *         accounts::resolve,
 *         context.getArtifacts()
 * );
 * }</pre>
 */
@RequiredArgsConstructor
public final class ProtocolWorkerPool implements ProtocolLibrary {
	private final @NotNull WorkerLibrary library;
	private final @NotNull Path cacheDirectory;
	private final @NotNull ReleaseCatalog catalog;
	private final @NotNull Accounts accounts;
	private final @NotNull ProtocolArtifactResolver artifacts;

	private final Map<String, ProtocolWorkerProcess> workers = new LinkedHashMap<>();
	private boolean closed;

	@Override
	public @NotNull String id() {
		return library.getId();
	}

	@Override
	public @NotNull List<ProtocolRelease> releases() {
		return catalog.releases();
	}

	@Override
	public synchronized @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
		if (closed) throw new IllegalStateException(library.getName() + " worker pool is closed");

		// The engine refused unlaunchable releases already; this guards the library against a request it cannot serve.
		ReleaseDefinition definition = catalog.require(request.getClientVersion());
		ProtocolRelease release = definition.getRelease();
		if (!release.isLaunchable()) throw new IllegalStateException(release.getLaunchRefusal());

		AuthenticationSession session = session(request);
		ProtocolWorkerProcess worker = workers.computeIfAbsent(release.getLibraryVersion(),
				ignored -> new ProtocolWorkerProcess(library, definition.getRelease(), closure(definition)));

		return worker.create(request, session);
	}

	/**
	 * Resolves what the player signs in with: nothing offline, the scenario's session identity when it declares
	 * one, and otherwise the stored account.
	 */
	private @Nullable AuthenticationSession session(PlayerRequest request) {
		PlayerLogin login = request.getLogin();
		if (!login.getAuthentication().usesAccount()) return null;

		SessionIdentity identity = login.getSessionIdentity();
		if (identity == null) return accounts.resolve(Objects.requireNonNull(login.getAccountId(), "accountId"));

		return AuthenticationSession.builder()
				.username(identity.getUsername())
				.uuid(identity.getUniqueId())
				.accessToken(identity.getAccessToken())
				.sessionServer(identity.getSessionServer())
				.build();
	}

	private List<Path> closure(ReleaseDefinition definition) {
		Path directory = cacheDirectory.resolve("protocol")
				.resolve(library.getId())
				.resolve(definition.getRelease().getLibraryVersion());

		return definition.getArtifacts().stream()
				.map(artifact -> artifacts.resolve(artifact.getUrl(), directory.resolve(artifact.fileName()), artifact.getSha256()))
				.toList();
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;

		IllegalStateException failure = null;
		for (ProtocolWorkerProcess worker : workers.values()) {
			try {
				worker.close();
			} catch (RuntimeException exception) {
				if (failure == null) failure = new IllegalStateException("Could not close all protocol workers");
				failure.addSuppressed(exception);
			}
		}

		workers.clear();
		if (failure != null) throw failure;
	}

	/**
	 * The library's stored accounts, which sign in a player that uses an account without a scenario session identity.
	 */
	@FunctionalInterface
	public interface Accounts {
		/**
		 * Resolves a stored account into the session a worker signs in with, refreshing it when needed.
		 *
		 * @param accountId id of the stored account
		 * @return session delivered to the worker only over its private standard input
		 * @throws IllegalStateException when the account is unknown or cannot be refreshed
		 */
		@NotNull AuthenticationSession resolve(@NotNull String accountId);
	}
}
