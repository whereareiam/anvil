package me.whereareiam.anvil.protocol.mcprotocol;

import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.worker.child.ProtocolWorker;
import me.whereareiam.anvil.protocol.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;

/**
 * Executable composition root for one release-specific MCProtocolLib worker. The release itself, its client
 * segment and the selected capability segments are on the class path chosen by the host; the library-neutral
 * worker runs them with the client of the client segment.
 */
public final class McProtocolWorkerMain {
	/**
	 * The MCProtocolLib library as the library-neutral worker sees it, on the host and in the worker.
	 */
	public static final WorkerLibrary LIBRARY = WorkerLibrary.builder()
			.id(McProtocolClient.LIBRARY_ID)
			.name("MCProtocolLib")
			.clientPort(McProtocolClient.class)
			.mainClass(McProtocolWorkerMain.class.getName())
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

	/**
	 * Verifies the segments and the loaded release's protocol before constructing or announcing the worker.
	 *
	 * @param arguments release key version, the release's edition and the native protocol number of the key version
	 * @throws Exception when the loaded runtime does not match or the worker fails
	 */
	@SuppressWarnings("unchecked")
	public static void main(@NotNull String[] arguments) throws Exception {
		ProtocolWorker.main(arguments, LIBRARY,
				segments -> new McProtocolWorkerClient((McProtocolClient<Object>) segments.client(McProtocolClient.class)));
	}
}
