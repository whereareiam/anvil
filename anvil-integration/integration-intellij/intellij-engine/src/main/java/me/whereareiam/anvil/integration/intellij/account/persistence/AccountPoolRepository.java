package me.whereareiam.anvil.integration.intellij.account.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and atomically updates pool declarations in one account directory.
 * <p>
 * Uses the runtime's {@code pools.properties} format: {@code schemaVersion=1} and one
 * {@code pool.<name>=<id>,<id>} entry per pool, listing distinct account IDs in lease order. The
 * runtime rejects duplicates and other schema versions, so this reader does too. The runtime leases one
 * account per ID and refuses an ID that several protocol libraries store, so saving refuses such a pool.
 */
@RequiredArgsConstructor
final class AccountPoolRepository {
	static final String FILE_NAME = "pools.properties";

	private static final String SCHEMA_VERSION = "1";
	private static final String POOL_PREFIX = "pool.";

	private final @NotNull Path directory;

	@NotNull Map<String, List<String>> read() throws IOException {
		Path file = directory.resolve(FILE_NAME);
		if (!Files.isRegularFile(file)) return Map.of();

		Properties properties = load(file);
		Map<String, List<String>> pools = new TreeMap<>();
		for (String key : properties.stringPropertyNames()) {
			if (!key.startsWith(POOL_PREFIX)) continue;

			String name = key.substring(POOL_PREFIX.length());
			pools.put(name, accountIds(name, properties.getProperty(key)));
		}

		return Collections.unmodifiableMap(pools);
	}

	private static Properties load(Path file) throws IOException {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			properties.load(input);
		} catch (IllegalArgumentException malformed) {
			throw new IOException("The pool file contains a malformed escape.", malformed);
		}

		if (!SCHEMA_VERSION.equals(properties.getProperty("schemaVersion")))
			throw new IOException("The pool file must use schema version " + SCHEMA_VERSION + ".");

		return properties;
	}

	private static List<String> accountIds(String name, String declaration) throws IOException {
		List<String> ids = new ArrayList<>();
		Set<String> unique = new LinkedHashSet<>();
		for (String entry : declaration.split(",", -1)) {
			String id = entry.trim();
			if (id.isEmpty()) throw new IOException("Pool '" + name + "' contains a blank account ID.");
			if (!unique.add(id)) throw new IOException("Pool '" + name + "' lists account '" + id + "' twice.");
			ids.add(id);
		}

		return List.copyOf(ids);
	}

	void save(
			@Nullable String previous,
			@NotNull String name,
			@NotNull List<String> ids,
			@NotNull Collection<AuthenticationAccount> accounts
	) throws IOException {
		if (name.isBlank() || ids.isEmpty()) throw new IOException("A pool needs a name and at least one account.");
		requireOneLibraryPerId(ids, accounts);

		Map<String, List<String>> pools = new TreeMap<>(read());
		if (!name.equals(previous) && pools.containsKey(name)) throw new IOException("A pool with this name already exists.");

		if (previous != null) pools.remove(previous);
		pools.put(name, ids.stream().distinct().toList());
		replace(pools);
	}

	private static void requireOneLibraryPerId(List<String> ids, Collection<AuthenticationAccount> accounts) throws IOException {
		Map<String, Set<String>> libraries = new TreeMap<>();
		for (AuthenticationAccount account : accounts)
			libraries.computeIfAbsent(account.getAccountId(), id -> new TreeSet<>()).add(account.getLibraryId());

		for (String id : ids) {
			Set<String> stored = libraries.getOrDefault(id, Set.of());
			if (stored.size() > 1)
				throw new IOException("Account '" + id + "' is stored by several protocol libraries " + stored
						+ "; a pool leases one account per ID, so store each pooled ID in one library only.");
		}
	}

	void remove(@NotNull String name) throws IOException {
		Map<String, List<String>> pools = new TreeMap<>(read());
		pools.remove(name);
		replace(pools);
	}

	void replace(@NotNull Map<String, List<String>> pools) throws IOException {
		Properties properties = new Properties();
		properties.setProperty("schemaVersion", SCHEMA_VERSION);
		pools.forEach((name, ids) -> properties.setProperty(POOL_PREFIX + name, String.join(",", ids)));

		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, ".anvil-pools-", ".tmp");
		try {
			try (OutputStream output = Files.newOutputStream(temporary)) {
				properties.store(output, "Anvil account pools");
			}

			try {
				Files.move(temporary, directory.resolve(FILE_NAME),
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, directory.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
