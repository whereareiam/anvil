package me.whereareiam.anvil.protocol.player.account;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Reads named pool declarations from {@value #FILE_NAME} in an account directory.
 * <p>
 * The file is a Java properties document with {@code schemaVersion=1} and one
 * {@code pool.<name>=<id>,<id>} entry per pool, listing distinct account IDs in lease order. It is not
 * a {@code .json} file, so it can never be mistaken for an account file. Every frontend that edits
 * pools writes this format.
 */
final class AccountPoolFile {
	static final String FILE_NAME = "pools.properties";
	static final String SCHEMA_VERSION = "1";
	static final String POOL_PREFIX = "pool.";

	private final Path file;

	AccountPoolFile(@NotNull Path accountsDirectory) {
		file = accountsDirectory.resolve(FILE_NAME);
	}

	/**
	 * Returns the account IDs declared for a pool, in lease order.
	 *
	 * @throws IllegalArgumentException when the pool is not declared
	 * @throws IllegalStateException when the file violates the format
	 * @throws UncheckedIOException when the file cannot be read
	 */
	@NotNull List<String> accountIds(@NotNull String poolName) {
		if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Unknown account pool: " + poolName);

		String declaration = read().getProperty(POOL_PREFIX + poolName);
		if (declaration == null) throw new IllegalArgumentException("Unknown account pool: " + poolName);

		List<String> ids = new ArrayList<>();
		Set<String> unique = new LinkedHashSet<>();
		for (String entry : declaration.split(",", -1)) {
			String id = entry.trim();
			if (id.isEmpty()) throw invalid("pool '" + poolName + "' contains a blank account ID");
			if (!unique.add(id)) throw invalid("pool '" + poolName + "' lists account '" + id + "' twice");
			ids.add(id);
		}

		return List.copyOf(ids);
	}

	private Properties read() {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			properties.load(input);
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read account pools: " + file, failure);
		} catch (IllegalArgumentException malformed) {
			throw new IllegalStateException("Invalid account pool file " + file + ": malformed escape", malformed);
		}

		String version = properties.getProperty("schemaVersion");
		if (version == null) throw invalid("schemaVersion is missing");
		if (!version.equals(SCHEMA_VERSION)) {
			throw invalid("schema version " + version
					+ " is unsupported; this Anvil version reads version " + SCHEMA_VERSION);
		}

		return properties;
	}

	private IllegalStateException invalid(String problem) {
		return new IllegalStateException("Invalid account pool file " + file + ": " + problem);
	}
}
