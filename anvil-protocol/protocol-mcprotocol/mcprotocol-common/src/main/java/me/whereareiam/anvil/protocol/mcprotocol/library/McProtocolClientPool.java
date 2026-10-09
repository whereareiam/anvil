package me.whereareiam.anvil.protocol.mcprotocol.library;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.mcprotocol.authentication.MicrosoftAuthentication;
import me.whereareiam.anvil.protocol.mcprotocol.catalog.McProtocolReleaseCatalog;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.mcprotocol.model.ReleaseDefinition;
import me.whereareiam.anvil.protocol.mcprotocol.worker.host.ProtocolWorkerProcess;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

		ReleaseDefinition definition = catalog.require(request.getRelease());
		if (!request.getRelease().getMinecraftVersions().contains(request.getClientVersion()))
			throw new IllegalArgumentException("MCProtocolLib release '" + request.getRelease().getLibraryVersion()
					+ "' does not speak Minecraft " + request.getClientVersion());
		if (!request.getRelease().isLaunchable()) throw new IllegalStateException(request.getRelease().getLaunchRefusal());

		AuthenticationSession session = request.getAuthentication() == AuthenticationMode.ONLINE
				? authentication.resolve(request.getAccountId())
				: null;
		ProtocolWorkerProcess worker = workers.computeIfAbsent(request.getRelease().getLibraryVersion(),
				ignored -> new ProtocolWorkerProcess(definition.getRelease(), closure(definition)));

		return worker.create(request, session);
	}

	private List<Path> closure(ReleaseDefinition definition) {
		Path directory = cacheDirectory.resolve("protocol")
				.resolve(McProtocolClient.LIBRARY_ID)
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
}
