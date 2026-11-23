package me.whereareiam.anvil.player.library.mcprotocol.library;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.player.api.ProtocolPlayer;
import me.whereareiam.anvil.player.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.player.api.library.ProtocolLibrary;
import me.whereareiam.anvil.player.api.library.ProtocolWorkerLauncher;
import me.whereareiam.anvil.player.api.model.PlayerRequest;
import me.whereareiam.anvil.player.api.model.ProtocolRelease;
import me.whereareiam.anvil.player.library.mcprotocol.authentication.MicrosoftAuthentication;
import me.whereareiam.anvil.player.library.mcprotocol.catalog.McProtocolReleaseCatalog;
import me.whereareiam.anvil.player.library.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.player.library.mcprotocol.model.AuthenticationSession;
import me.whereareiam.anvil.player.library.mcprotocol.model.ReleaseDefinition;
import me.whereareiam.anvil.player.library.mcprotocol.worker.host.ProtocolWorkerProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates protocol clients and shares one isolated worker process per selected library release.
 * A release's runtime closure is cached under {@code <cache>/protocol/mcprotocol/<release version>/<file>}.
 */
@RequiredArgsConstructor
final class McProtocolClientPool implements ProtocolLibrary {
	private final Path cacheDirectory;
	private final McProtocolReleaseCatalog catalog;
	private final MicrosoftAuthentication authentication;
	private final ProtocolArtifactResolver artifacts;
	private final ProtocolWorkerLauncher launcher;

	private final Map<String, ProtocolWorkerProcess> workers = new LinkedHashMap<>();
	private boolean closed;

	@Override
	public @NotNull String id() {
		return McProtocolClient.LIBRARY_ID;
	}

	@Override
	public @NotNull List<ProtocolRelease> releases() {
		return catalog.releases();
	}

	@Override
	public synchronized @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
		if (closed) throw new IllegalStateException("MCProtocol client pool is closed");

		// The engine refused unlaunchable releases already; this guards the library against a request it cannot serve.
		ReleaseDefinition definition = catalog.require(request.clientVersion());
		ProtocolRelease release = definition.release();
		if (!release.isLaunchable()) throw new IllegalStateException(release.launchRefusal());

		AuthenticationSession session = session(request);
		ProtocolWorkerProcess worker = workers.computeIfAbsent(release.libraryVersion(),
				ignored -> new ProtocolWorkerProcess(definition.release(), closure(definition), launcher));

		return worker.create(request, session);
	}

	/**
	 * Resolves what the player signs in with: nothing offline, the scenario's session identity when it declares
	 * one, and otherwise the stored account.
	 */
	private @Nullable AuthenticationSession session(PlayerRequest request) {
		PlayerLogin login = request.login();
		if (!login.authentication().usesAccount()) return null;

		SessionIdentity identity = login.sessionIdentity();
		if (identity == null) return authentication.resolve(Objects.requireNonNull(login.accountId(), "accountId"));

		return AuthenticationSession.builder()
				.username(identity.username())
				.uuid(identity.uniqueId())
				.accessToken(identity.accessToken())
				.sessionServer(identity.sessionServer())
				.build();
	}

	private List<Path> closure(ReleaseDefinition definition) {
		Path directory = cacheDirectory.resolve("protocol")
				.resolve(McProtocolClient.LIBRARY_ID)
				.resolve(definition.release().libraryVersion());

		return definition.artifacts().stream()
				.map(artifact -> artifacts.resolve(artifact.url(), directory.resolve(artifact.fileName()), artifact.sha256()))
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
}
