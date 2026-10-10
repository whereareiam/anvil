package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.mcprotocol.model.ReleaseDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * MCProtocolLib releases this Anvil version can run: the built-in release data shipped as
 * {@value #RESOURCE}, plus optional user-supplied releases in the same schema.
 *
 * <p>User-supplied releases are {@link ProtocolRelease#isAdditional() additional} and therefore untested. A
 * user-supplied release whose version equals a built-in release with identical content is ignored with an
 * info line; one with different content is refused. Each Minecraft version belongs to at most one release
 * across both sources.</p>
 *
 * <pre>{@code
 * McProtocolReleaseCatalog catalog = McProtocolReleaseCatalog.load(Path.of("extra-releases.toml"));
 * ReleaseDefinition definition = catalog.require(MinecraftVersion.parse("1.21.11"));
 * }</pre>
 */
public final class McProtocolReleaseCatalog {
	/**
	 * Class-path location of the built-in release data.
	 */
	public static final String RESOURCE = "me/whereareiam/anvil/protocol/mcprotocol/mcprotocol-releases.toml";

	private final Map<String, ReleaseDefinition> definitions = new LinkedHashMap<>();

	/**
	 * Combines built-in and user-supplied releases, ordered by release key.
	 *
	 * @param builtIn releases shipped with Anvil
	 * @param additional user-supplied releases, each marked additional
	 * @param notices receiver of info lines about ignored duplicates
	 * @throws IllegalArgumentException when a user-supplied release conflicts with a built-in release or two
	 * releases list the same Minecraft version
	 */
	McProtocolReleaseCatalog(
			@NotNull List<ReleaseDefinition> builtIn,
			@NotNull List<ReleaseDefinition> additional,
			@NotNull Consumer<String> notices
	) {
		Map<String, ReleaseDefinition> combined = new LinkedHashMap<>();
		builtIn.forEach(definition -> combined.put(definition.getRelease().getLibraryVersion(), definition));
		for (ReleaseDefinition definition : additional) {
			String version = definition.getRelease().getLibraryVersion();
			ReleaseDefinition existing = combined.get(version);
			if (existing == null) {
				combined.put(version, definition);
				continue;
			}

			if (!asBuiltIn(definition).equals(existing))
				throw new IllegalArgumentException("Additional MCProtocolLib release " + version
						+ " conflicts with the built-in release of the same version; rename it or remove it");
			notices.accept("[Anvil] Info: additional MCProtocolLib release " + version
					+ " is identical to the built-in release and is ignored.");
		}

		Map<MinecraftVersion, String> owners = new LinkedHashMap<>();
		for (ReleaseDefinition definition : combined.values())
			for (MinecraftVersion minecraft : definition.getRelease().getMinecraftVersions()) {
				String previous = owners.putIfAbsent(minecraft, definition.getRelease().getLibraryVersion());
				if (previous != null)
					throw new IllegalArgumentException("MCProtocolLib releases " + previous + " and "
							+ definition.getRelease().getLibraryVersion() + " both list Minecraft " + minecraft);
			}

		combined.values().stream()
				.sorted(Comparator.comparing(definition -> definition.getRelease().version()))
				.forEach(definition -> definitions.put(definition.getRelease().getLibraryVersion(), definition));
	}

	/**
	 * Loads the built-in release data and, when configured, a user-supplied release file.
	 *
	 * @param additionalReleases user-supplied release file in the built-in schema, or null
	 * @return validated catalog; info lines about ignored duplicates go to standard error
	 * @throws IllegalArgumentException when either file is missing or invalid, or the files conflict
	 */
	public static @NotNull McProtocolReleaseCatalog load(@Nullable Path additionalReleases) {
		McProtocolReleaseReader reader = new McProtocolReleaseReader();
		List<ReleaseDefinition> builtIn = builtIn(reader);
		if (additionalReleases == null) return new McProtocolReleaseCatalog(builtIn, List.of(), System.err::println);

		try (InputStream input = Files.newInputStream(additionalReleases)) {
			return new McProtocolReleaseCatalog(builtIn, reader.read(input, additionalReleases.toString(), true), System.err::println);
		} catch (NoSuchFileException exception) {
			throw new IllegalArgumentException("Additional MCProtocolLib release file does not exist: " + additionalReleases, exception);
		} catch (IOException exception) {
			throw new IllegalArgumentException("Could not read additional MCProtocolLib releases from " + additionalReleases, exception);
		}
	}

	/**
	 * Returns every release, ordered by release key.
	 *
	 * @return immutable releases
	 */
	public @NotNull List<ProtocolRelease> releases() {
		return definitions.values().stream().map(ReleaseDefinition::getRelease).toList();
	}

	/**
	 * Returns the release data of the one release that lists a Minecraft version.
	 *
	 * @param version Minecraft version a player speaks
	 * @return definition of the release listing it
	 * @throws IllegalArgumentException when no release of this catalog lists the version
	 */
	public @NotNull ReleaseDefinition require(@NotNull MinecraftVersion version) {
		return definitions.values().stream()
				.filter(definition -> definition.getRelease().getMinecraftVersions().contains(version))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No MCProtocolLib release speaks Minecraft " + version
						+ ". Known releases: " + definitions.keySet()));
	}

	private static List<ReleaseDefinition> builtIn(McProtocolReleaseReader reader) {
		try (InputStream input = McProtocolReleaseCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (input == null) throw new IllegalStateException("Built-in MCProtocolLib release data is missing: " + RESOURCE);

			return reader.read(input, "mcprotocol-releases.toml", false);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read built-in MCProtocolLib release data", exception);
		}
	}

	private static ReleaseDefinition asBuiltIn(ReleaseDefinition definition) {
		return definition.toBuilder().release(definition.getRelease().toBuilder().additional(false).build()).build();
	}
}
