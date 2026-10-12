package me.whereareiam.anvil.protocol.worker.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * What the library-neutral worker needs to know about one protocol library. A library family describes itself once
 * and hands the description to its catalog, its worker processes on the host and its worker's entry point in the
 * child, so that none of them names a library.
 *
 * <pre>{@code
 * WorkerLibrary library = WorkerLibrary.builder()
 *         .id("mcprotocol")
 *         .name("MCProtocolLib")
 *         .clientPort(McProtocolClient.class)
 *         .mainClass(McProtocolWorkerMain.class.getName())
 *         .releases("me/whereareiam/anvil/protocol/mcprotocol/mcprotocol-releases.toml")
 *         .pinTask("./gradlew :anvil-protocol:protocol-mcprotocol:pinLibraryReleases")
 *         .ownedPackage("io/netty/")
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class WorkerLibrary {
	/**
	 * Registered library id: declarations select it, segment descriptors and stored accounts name it, and its
	 * release cache lies below it.
	 */
	@NotNull String id;
	/**
	 * Name of the library in messages, such as {@code MCProtocolLib}.
	 */
	@NotNull String name;
	/**
	 * Release-neutral client port that exactly one client segment on a worker's class path implements and declares in
	 * {@code META-INF/services/<port>}.
	 */
	@NotNull Class<?> clientPort;
	/**
	 * Binary name of the library's worker entry point, which the host launches in the worker JVM.
	 */
	@NotNull String mainClass;
	/**
	 * Class-path location of the built-in release data, such as
	 * {@code me/whereareiam/anvil/protocol/mcprotocol/mcprotocol-releases.toml}.
	 */
	@NotNull String releases;
	/**
	 * Command that pins the built-in release data, named when a built-in release cannot be launched.
	 */
	@NotNull String pinTask;
	/**
	 * Resource prefixes of the packages a release's runtime closure owns, such as {@code io/netty/}. Their classes
	 * come only from the closure: a host class path entry that contains any of them is refused for the worker.
	 */
	@Singular
	@NotNull List<String> ownedPackages;
	/**
	 * Additional arguments of the worker JVM, such as system properties the library reads.
	 */
	@Singular
	@NotNull List<String> jvmArguments;

	/**
	 * Returns the file name of the built-in release data, used in messages.
	 *
	 * @return last segment of {@link #getReleases()}
	 */
	public @NotNull String releasesFileName() {
		return releases.substring(releases.lastIndexOf('/') + 1);
	}
}
