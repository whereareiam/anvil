package me.whereareiam.anvil.protocol.api.worker.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * What the native worker needs to know about one protocol library it hosts. A library returns it from
 * {@link me.whereareiam.anvil.protocol.api.worker.WorkerLibraryProvider#library()}; the worker reads the release
 * catalog, selects the worker class path and verifies the segments with it, so that the worker names no library.
 *
 * <pre>{@code
 * WorkerLibrary library = WorkerLibrary.builder()
 *         .id("mcprotocol")
 *         .name("MCProtocolLib")
 *         .clientPort(McProtocolClient.class)
 *         .releases("me/whereareiam/anvil/protocol/mcprotocol/mcprotocol-releases.toml")
 *         .pinTask("./gradlew :anvil-protocol:protocol-mcprotocol:pinLibraryReleases")
 *         .ownedPackage("io/netty/")
 *         .offlineUniqueIdPrefix("OfflinePlayer:")
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
	 * Class-path location of the built-in release data, such as
	 * {@code me/whereareiam/anvil/protocol/mcprotocol/mcprotocol-releases.toml}.
	 */
	@NotNull String releases;
	/**
	 * Command that pins the built-in release data, named when a built-in release cannot be launched.
	 */
	@NotNull String pinTask;
	/**
	 * Text the worker puts before a username to derive the UUID of an offline profile, as a name-based UUID of
	 * {@code prefix + username}, such as {@code OfflinePlayer:}, the prefix the servers a library speaks to use for
	 * their own offline players. The library chooses it, so a username maps to the same UUID in every run and on
	 * every worker of the library.
	 */
	@NotNull String offlineUniqueIdPrefix;
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
	 * Whether the library claims stored accounts whose file names no library, the files written before account
	 * files recorded their owner. At most one installed library should claim them; a library that never wrote such
	 * files keeps the default {@code false}.
	 */
	boolean readsUnlabelledAccounts;

	/**
	 * Derives the UUID a player of this library claims when it logs in offline with a username.
	 *
	 * @param username username the player logs in with
	 * @return name-based UUID of {@link #getOfflineUniqueIdPrefix()} followed by the username
	 */
	public @NotNull UUID offlineUniqueId(@NotNull String username) {
		return UUID.nameUUIDFromBytes((offlineUniqueIdPrefix + username).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Returns the file name of the built-in release data, used in messages.
	 *
	 * @return last segment of {@link #getReleases()}
	 */
	public @NotNull String releasesFileName() {
		return releases.substring(releases.lastIndexOf('/') + 1);
	}
}
