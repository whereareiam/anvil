package me.whereareiam.anvil.protocol.worker.fixture;

import me.whereareiam.anvil.protocol.api.worker.WorkerClient;
import me.whereareiam.anvil.protocol.api.worker.WorkerLibraryProvider;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;

/**
 * A protocol library that exists only in tests and plugs into the worker through the tests' service descriptors:
 * its client segments implement {@link WorkerClient} itself, and its closures own the {@code io/netty/} and
 * {@code example/probe/} packages. It has no account workflow.
 */
public final class ProbeLibrary implements WorkerLibraryProvider {
	/**
	 * Description of the probe library.
	 */
	public static final WorkerLibrary LIBRARY = WorkerLibrary.builder()
			.id("probelib")
			.name("ProbeLib")
			.clientPort(WorkerClient.class)
			.releases("me/whereareiam/anvil/protocol/worker/fixture/probelib-releases.toml")
			.pinTask("./gradlew pinProbeReleases")
			.offlineUniqueIdPrefix("ProbePlayer:")
			.ownedPackage("io/netty/")
			.ownedPackage("example/probe/")
			.jvmArgument("-Dexample.probe=true")
			.build();

	@Override
	public @NotNull WorkerLibrary library() {
		return LIBRARY;
	}

	@Override
	public @NotNull WorkerClient client(@NotNull Object adapter) {
		return (WorkerClient) adapter;
	}
}
