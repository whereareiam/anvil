package me.whereareiam.anvil.integration.intellij.account.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.account.StagedAccounts;
import org.jetbrains.annotations.NotNull;

/**
 * Temporary merged account directory borrowed by one tooling process.
 * <p>
 * The directory owns its copied files and deletes them when closed. Source directories and persistent pool
 * files are never modified.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class StagedAccountDirectory implements StagedAccounts {
	private final @NotNull Path directory;

	/**
	 * Copies the account files and pools of the given directories into a new temporary directory.
	 *
	 * @param roots account directories in priority order
	 * @throws IOException if a directory cannot be read, two directories define the same pool, or copying fails
	 */
	public static @NotNull StagedAccountDirectory create(@NotNull List<Path> roots) throws IOException {
		var staged = new StagedAccountDirectory(Files.createTempDirectory("anvil-accounts-"));
		try {
			staged.materialize(roots);
			return staged;
		} catch (IOException | RuntimeException failure) {
			try {
				staged.close();
			} catch (IOException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
	}

	@Override
	public @NotNull Path directory() {
		return directory;
	}

	private void materialize(@NotNull List<Path> roots) throws IOException {
		AccountDirectoryRepository accounts = new AccountDirectoryRepository();
		Map<String, List<String>> pools = new LinkedHashMap<>();
		for (Path root : roots) {
			accounts.copyAccountFiles(root, directory);
			for (var pool : new AccountPoolRepository(root).read().entrySet())
				if (pools.putIfAbsent(pool.getKey(), pool.getValue()) != null)
					throw new IOException("Pool '" + pool.getKey() + "' exists in both account stores.");
		}

		if (!pools.isEmpty()) new AccountPoolRepository(directory).replace(pools);
	}

	@Override
	public void close() throws IOException {
		if (!Files.exists(directory)) return;
		IOException failure = null;

		try (var files = Files.walk(directory)) {
			for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
				try {
					Files.deleteIfExists(file);
				} catch (IOException exception) {
					if (failure == null) failure = exception;
					else failure.addSuppressed(exception);
				}
			}
		}

		if (failure != null) throw failure;
	}
}
