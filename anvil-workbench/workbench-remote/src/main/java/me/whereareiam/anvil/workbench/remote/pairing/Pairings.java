package me.whereareiam.anvil.workbench.remote.pairing;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.workbench.protocol.WireMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The workbenches an engine paired with, kept in {@code workbenches.json} of one directory: by default
 * {@code ~/.anvil/workbenches}, or the directory {@value #DIRECTORY_ENVIRONMENT} or the {@value #DIRECTORY_PROPERTY}
 * property names, such as a folder a sync tool shares between machines.
 */
public final class Pairings {
	/**
	 * Environment variable naming the pairings directory.
	 */
	public static final String DIRECTORY_ENVIRONMENT = "ANVIL_WORKBENCHES";
	/**
	 * System property naming the pairings directory.
	 */
	public static final String DIRECTORY_PROPERTY = "anvil.workbenches";
	private static final String FILE = "workbenches.json";

	private final @NotNull Path directory;
	private final @NotNull ObjectMapper mapper = WireMapper.create();

	/**
	 * Opens the pairings in a directory, which need not exist yet.
	 *
	 * @param directory pairings directory
	 */
	public Pairings(@NotNull Path directory) {
		this.directory = directory;
	}

	/**
	 * Opens the pairings in the directory the system property, then the environment variable, then the default
	 * names.
	 *
	 * @return the engine's pairings
	 */
	public static @NotNull Pairings standard() {
		String configured = System.getProperty(DIRECTORY_PROPERTY);
		if (configured == null || configured.isBlank()) configured = System.getenv(DIRECTORY_ENVIRONMENT);
		if (configured != null && !configured.isBlank()) return new Pairings(Path.of(configured));

		return new Pairings(Path.of(System.getProperty("user.home"), ".anvil", "workbenches"));
	}

	/**
	 * Returns the directory the pairings live in.
	 *
	 * @return pairings directory
	 */
	public @NotNull Path directory() {
		return directory;
	}

	/**
	 * Returns a pairing by name.
	 *
	 * @param name workbench name
	 * @return the pairing, if the engine paired with a workbench of that name
	 * @throws IOException when the pairings cannot be read
	 */
	public synchronized @NotNull Optional<Pairing> find(@NotNull String name) throws IOException {
		return Optional.ofNullable(read().workbenches().get(name));
	}

	/**
	 * Returns every pairing, by name.
	 *
	 * @return pairings
	 * @throws IOException when the pairings cannot be read
	 */
	public synchronized @NotNull List<Pairing> all() throws IOException {
		return List.copyOf(read().workbenches().values());
	}

	/**
	 * Returns the workbench an engine uses when nothing names one.
	 *
	 * @return default workbench name, if one is set
	 * @throws IOException when the pairings cannot be read
	 */
	public synchronized @NotNull Optional<String> defaultName() throws IOException {
		return Optional.ofNullable(read().defaultName());
	}

	/**
	 * Saves a pairing, replacing one of the same name.
	 *
	 * @param pairing new pairing
	 * @throws IOException when the pairings cannot be written
	 */
	public synchronized void save(@NotNull Pairing pairing) throws IOException {
		Stored stored = read();
		Map<String, Pairing> workbenches = new TreeMap<>(stored.workbenches());
		workbenches.put(pairing.getName(), pairing);
		write(new Stored(stored.defaultName(), workbenches));
	}

	/**
	 * Removes a pairing; the default goes with it when it named that workbench.
	 *
	 * @param name workbench name
	 * @return true when a pairing was removed
	 * @throws IOException when the pairings cannot be written
	 */
	public synchronized boolean remove(@NotNull String name) throws IOException {
		Stored stored = read();
		Map<String, Pairing> workbenches = new TreeMap<>(stored.workbenches());
		boolean removed = workbenches.remove(name) != null;
		write(new Stored(name.equals(stored.defaultName()) ? null : stored.defaultName(), workbenches));
		return removed;
	}

	/**
	 * Sets the default workbench, or clears it with null.
	 *
	 * @param name workbench name, or null for the local workbench
	 * @throws IOException when the pairings cannot be written
	 * @throws IllegalArgumentException when no workbench of that name is paired
	 */
	public synchronized void setDefault(@Nullable String name) throws IOException {
		Stored stored = read();
		if (name != null && !stored.workbenches().containsKey(name))
			throw new IllegalArgumentException("No workbench named '" + name + "' is paired");
		write(new Stored(name, stored.workbenches()));
	}

	private Stored read() throws IOException {
		Path file = directory.resolve(FILE);
		return Files.exists(file) ? mapper.readValue(file.toFile(), Stored.class) : new Stored(null, Map.of());
	}

	private void write(Stored stored) throws IOException {
		Files.createDirectories(directory);
		Path file = directory.resolve(FILE);
		Path staged = directory.resolve(FILE + ".tmp");
		mapper.writerWithDefaultPrettyPrinter().writeValue(staged.toFile(), stored);
		try {
			Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException notPosix) {
			// Windows keeps the file to its user through the profile directory's own permissions.
		}
		Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/**
	 * The content of the pairings file: the default workbench and every pairing by name.
	 */
	private record Stored(@Nullable String defaultName, @NotNull Map<String, Pairing> workbenches) {
	}
}
