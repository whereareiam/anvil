package me.whereareiam.anvil.protocol.cloudburst;

import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.WorkerClient;
import me.whereareiam.anvil.protocol.api.worker.WorkerLibraryProvider;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The CloudburstMC Protocol library as the native worker hosts it: version-isolated workers for the Bedrock releases
 * in its release data, driven through the client segment's {@link CloudburstClient}, with offline logins and the
 * Bedrock Edition sessions of stored accounts.
 */
public final class CloudburstWorkerLibrary implements WorkerLibraryProvider {
	/**
	 * The CloudburstMC Protocol library as the native worker sees it, on the host and in the worker.
	 */
	public static final WorkerLibrary LIBRARY = WorkerLibrary.builder()
			.id(CloudburstClient.LIBRARY_ID)
			.name("CloudburstMC Protocol")
			.clientPort(CloudburstClient.class)
			.releases("me/whereareiam/anvil/protocol/cloudburst/cloudburst-releases.toml")
			.pinTask("./gradlew :anvil-protocol:protocol-cloudburst:pinLibraryReleases")
			// Bedrock has no offline UUID convention: an offline client's login names any identity. The library's own
			// prefix keeps a name's UUID stable and distinct from the offline UUIDs of other libraries.
			.offlineUniqueIdPrefix("BedrockPlayer:")
			// Every release brings its own copy of these in its closure.
			.ownedPackage("io/netty/")
			.ownedPackage("org/cloudburstmc/")
			.ownedPackage("org/jose4j/")
			.build();

	@Override
	public @NotNull WorkerLibrary library() {
		return LIBRARY;
	}

	@Override
	@SuppressWarnings("unchecked")
	public @NotNull WorkerClient client(@NotNull Object adapter) {
		return new CloudburstWorkerClient((CloudburstClient<CloudburstSession<?, ?, ?>>) adapter);
	}

	@Override
	public @NotNull Optional<WorkerAuthentication> authentication() {
		return Optional.of(new XboxAuthentication());
	}
}
