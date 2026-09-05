package me.whereareiam.anvil.protocol.mcprotocol.provider;

import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.mcprotocol.authentication.MicrosoftAuthentication;
import me.whereareiam.anvil.protocol.mcprotocol.catalog.ProtocolCatalog;
import me.whereareiam.anvil.protocol.mcprotocol.catalog.ProtocolLibraryResolver;
import me.whereareiam.anvil.protocol.mcprotocol.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.mcprotocol.model.ProtocolDefinition;
import me.whereareiam.anvil.protocol.mcprotocol.worker.host.ProtocolWorkerProcess;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates protocol clients and shares one isolated worker process per selected client version.
 */
final class McProtocolClientPool implements ProtocolBackend {
	private final Path cacheDirectory;
	private final ProtocolCatalog catalog = new ProtocolCatalog();
	private final MicrosoftAuthentication authentication;
	private final ProtocolLibraryResolver libraries = new ProtocolLibraryResolver();
	private final Map<String, ProtocolWorkerProcess> workers = new LinkedHashMap<>();
	private boolean closed;

	McProtocolClientPool(@NotNull Path cacheDirectory, @NotNull MicrosoftAuthentication authentication) {
		this.cacheDirectory = cacheDirectory;
		this.authentication = authentication;
	}

	@Override
	public @NotNull String id() {
		return McProtocolProvider.ID;
	}

	@Override
	public @NotNull Collection<ProtocolSupport> supportedProtocols() {
		return catalog.definitions().stream().map(ProtocolDefinition::getSupport).toList();
	}

	@Override
	public synchronized @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
		if (closed) throw new IllegalStateException("MCProtocol client pool is closed");

		ProtocolDefinition definition = catalog.require(request.getClientVersion());
		AuthenticationSession session = request.getAuthentication() == AuthenticationMode.ONLINE
				? authentication.resolve(request.getAuthenticationProfile())
				: null;
		ProtocolWorkerProcess worker = workers.computeIfAbsent(request.getClientVersion(),
				ignored -> new ProtocolWorkerProcess(definition, libraries.resolve(definition, cacheDirectory)));

		return worker.create(request, session);
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
		libraries.close();
		if (failure != null) throw failure;
	}
}
