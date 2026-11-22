package me.whereareiam.anvil.testkit.tests.server.scenario;

import me.whereareiam.anvil.player.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.player.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.player.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Reads the release data of the installed MCProtocolLib library, which speaks every release of the live matrix,
 * without starting a worker or downloading an artifact.
 */
final class McProtocolReleases {
	private McProtocolReleases() {
	}

	/**
	 * Returns the library's releases.
	 *
	 * @param directory a scratch directory for the library's cache and accounts, which reading leaves untouched
	 * @return the releases the library ships data for
	 */
	static @NotNull List<ProtocolRelease> read(@NotNull Path directory) {
		ProtocolLibraryProvider mcprotocol = ServiceLoader.load(ProtocolLibraryProvider.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(library -> library.id().equals("mcprotocol"))
				.findFirst().orElseThrow();
		ProtocolLibraryContext context = ProtocolLibraryContext.builder()
				.workers(launch -> {
					throw new AssertionError("Reading release data must not start a worker");
				})
				.cacheDirectory(directory)
				.accountsDirectory(directory.resolve("accounts"))
				.artifacts((artifact, destination, sha256) -> {
					throw new AssertionError("Reading release data must not download artifacts");
				})
				.build();

		return mcprotocol.releases(context);
	}
}
