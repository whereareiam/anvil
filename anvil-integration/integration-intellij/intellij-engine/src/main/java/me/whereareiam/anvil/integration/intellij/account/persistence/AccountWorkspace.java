package me.whereareiam.anvil.integration.intellij.account.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import org.jetbrains.annotations.NotNull;

/**
 * Temporary merged account directory borrowed by one tooling process.
 *
 * <p>The workspace owns its copied files and deletes them when closed. Source directories and
 * persistent pool files are never modified.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccountWorkspace implements AutoCloseable {
	private final @NotNull Path directory;

	/**
	 * Creates a temporary workspace from the configured project and optional global directories.
	 */
	public static @NotNull AccountWorkspace create(
			@NotNull AccountLibrary accounts,
			@NotNull Preferences preferences
	) throws IOException {
		Path project = accounts.directory().toAbsolutePath().normalize();
		Path global = preferences.accountsDirectory().toAbsolutePath().normalize();
		List<Path> roots =
				accounts.includesGlobal() && !project.equals(global)
						? List.of(project, global)
						: List.of(project);

		AccountWorkspace workspace = new AccountWorkspace(Files.createTempDirectory("anvil-accounts-"));
		try {
			workspace.materialize(roots);
			return workspace;
		} catch (IOException | RuntimeException failure) {
			try {
				workspace.close();
			} catch (IOException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
	}

	private void materialize(@NotNull List<Path> roots) throws IOException {
		AccountDirectoryRepository accountRepository = new AccountDirectoryRepository();
		Map<String, List<String>> pools = new LinkedHashMap<>();

		for (Path root : roots) {
			accountRepository.copyAccountFiles(root, directory);
			for (var pool : new AccountPoolRepository(root).read().entrySet()) {
				if (pools.putIfAbsent(pool.getKey(), pool.getValue()) != null)
					throw new IOException("Pool '" + pool.getKey() + "' exists in both account stores.");
			}
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
