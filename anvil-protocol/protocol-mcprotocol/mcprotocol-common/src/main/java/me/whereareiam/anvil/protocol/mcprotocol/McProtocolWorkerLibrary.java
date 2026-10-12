package me.whereareiam.anvil.protocol.mcprotocol;

import me.whereareiam.anvil.protocol.mcprotocol.authentication.MicrosoftAuthentication;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.worker.WorkerClient;
import me.whereareiam.anvil.protocol.worker.WorkerLibraryProvider;
import me.whereareiam.anvil.protocol.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The MCProtocolLib library as the native worker hosts it: version-isolated workers for the releases in its
 * release data, driven through the client segment's {@link McProtocolClient}, and Microsoft account authentication.
 */
public final class McProtocolWorkerLibrary implements WorkerLibraryProvider {
	/**
	 * The MCProtocolLib library as the native worker sees it, on the host and in the worker.
	 */
	public static final WorkerLibrary LIBRARY = WorkerLibrary.builder()
			.id(McProtocolClient.LIBRARY_ID)
			.name("MCProtocolLib")
			.clientPort(McProtocolClient.class)
			.releases("me/whereareiam/anvil/protocol/mcprotocol/mcprotocol-releases.toml")
			.pinTask("./gradlew :anvil-protocol:protocol-mcprotocol:pinLibraryReleases")
			// Every release brings its own copy of these in its closure.
			.ownedPackage("io/netty/")
			.ownedPackage("org/geysermc/mcprotocollib/")
			.ownedPackage("com/github/steveice10/")
			.ownedPackage("org/cloudburstmc/")
			.ownedPackage("net/kyori/adventure/")
			// Netty defaults to two event loops per processor. Older MCProtocolLib releases open one io_uring ring per
			// loop, which exhausts the locked-memory limit on machines with many processors.
			.jvmArgument("-Dio.netty.eventLoopThreads=4")
			.build();

	@Override
	public @NotNull WorkerLibrary library() {
		return LIBRARY;
	}

	@Override
	@SuppressWarnings("unchecked")
	public @NotNull WorkerClient client(@NotNull Object adapter) {
		return new McProtocolWorkerClient((McProtocolClient<Object>) adapter);
	}

	@Override
	public @NotNull Optional<WorkerAuthentication> authentication(@NotNull Path accountsDirectory) {
		return Optional.of(new MicrosoftAuthentication(accountsDirectory));
	}
}
