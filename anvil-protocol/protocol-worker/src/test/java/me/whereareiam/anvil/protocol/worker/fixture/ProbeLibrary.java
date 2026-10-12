package me.whereareiam.anvil.protocol.worker.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.worker.child.WorkerClient;
import me.whereareiam.anvil.protocol.worker.model.WorkerLibrary;

/**
 * A protocol library that exists only in tests: its client segments implement {@link WorkerClient} itself, and its
 * closures own the {@code io/netty/} and {@code example/probe/} packages.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProbeLibrary {
	/**
	 * Description of the probe library.
	 */
	public static final WorkerLibrary LIBRARY = WorkerLibrary.builder()
			.id("probelib")
			.name("ProbeLib")
			.clientPort(WorkerClient.class)
			.mainClass("example.probe.ProbeWorkerMain")
			.releases("me/whereareiam/anvil/protocol/worker/fixture/probelib-releases.toml")
			.pinTask("./gradlew pinProbeReleases")
			.ownedPackage("io/netty/")
			.ownedPackage("example/probe/")
			.jvmArgument("-Dexample.probe=true")
			.build();
}
