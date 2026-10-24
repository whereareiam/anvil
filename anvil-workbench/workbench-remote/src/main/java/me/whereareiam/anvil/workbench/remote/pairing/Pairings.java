package me.whereareiam.anvil.workbench.remote.pairing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
	private final @NotNull ObjectMapper mapper = new ObjectMapper();

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
		JsonNode entry = read().path("workbenches").path(name);
		if (entry.isMissingNode()) return Optional.empty();

		return Optional.of(Pairing.builder()
				.name(name)
				.address(entry.path("address").asText())
				.fingerprint(entry.path("fingerprint").asText())
				.token(entry.path("token").asText())
				.build());
	}

	/**
	 * Returns every pairing, by name.
	 *
	 * @return pairings
	 * @throws IOException when the pairings cannot be read
	 */
	public synchronized @NotNull List<Pairing> all() throws IOException {
		List<Pairing> pairings = new ArrayList<>();
		for (var name : read().path("workbenches").properties()) pairings.add(find(name.getKey()).orElseThrow());
		return pairings;
	}

	/**
	 * Returns the workbench an engine uses when nothing names one.
	 *
	 * @return default workbench name, if one is set
	 * @throws IOException when the pairings cannot be read
	 */
	public synchronized @NotNull Optional<String> defaultName() throws IOException {
		JsonNode name = read().path("default");
		return name.isTextual() && !name.asText().isBlank() ? Optional.of(name.asText()) : Optional.empty();
	}

	/**
	 * Saves a pairing, replacing one of the same name.
	 *
	 * @param pairing new pairing
	 * @throws IOException when the pairings cannot be written
	 */
	public synchronized void save(@NotNull Pairing pairing) throws IOException {
		ObjectNode root = read();
		root.withObject("/workbenches").putObject(pairing.getName())
				.put("address", pairing.getAddress())
				.put("fingerprint", pairing.getFingerprint())
				.put("token", pairing.getToken());
		write(root);
	}

	/**
	 * Removes a pairing; the default goes with it when it named that workbench.
	 *
	 * @param name workbench name
	 * @return true when a pairing was removed
	 * @throws IOException when the pairings cannot be written
	 */
	public synchronized boolean remove(@NotNull String name) throws IOException {
		ObjectNode root = read();
		boolean removed = root.withObject("/workbenches").remove(name) != null;
		if (name.equals(root.path("default").asText())) root.remove("default");
		write(root);
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
		ObjectNode root = read();
		if (name == null) {
			root.remove("default");
		} else {
			if (root.path("workbenches").path(name).isMissingNode())
				throw new IllegalArgumentException("No workbench named '" + name + "' is paired");
			root.put("default", name);
		}
		write(root);
	}

	private ObjectNode read() throws IOException {
		Path file = directory.resolve(FILE);
		return Files.exists(file) ? (ObjectNode) mapper.readTree(file.toFile()) : mapper.createObjectNode();
	}

	private void write(ObjectNode root) throws IOException {
		Files.createDirectories(directory);
		Path file = directory.resolve(FILE);
		Path staged = directory.resolve(FILE + ".tmp");
		mapper.writerWithDefaultPrettyPrinter().writeValue(staged.toFile(), root);
		try {
			Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException notPosix) {
			// Windows keeps the file to its user through the profile directory's own permissions.
		}
		Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
