package me.whereareiam.anvil.protocol.java.data;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.data.exception.BlockDataUnavailableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * The block data Anvil ships for every Minecraft: Java Edition release it supports, by Minecraft version.
 *
 * <p>The data is generated at build time from PrismarineJS minecraft-data (MIT), pinned to one release of it, with
 * the movement facts minecraft-data does not carry maintained by hand from vanilla's code. Minecraft versions that
 * speak one protocol share one {@link BlockRegistry}. A supported version the data source has no block data for is
 * listed as missing and reported as such, never served another version's data. Each registry is read on first use
 * and kept; lookups are safe from any thread.</p>
 *
 * <pre>{@code
 * JavaBlockData data = JavaBlockData.shipped();
 * BlockRegistry registry = data.require(MinecraftVersion.parse("1.20.6"));
 * float friction = registry.state(stateId).getBlock().getFriction();
 * }</pre>
 */
public final class JavaBlockData {
	private static final String INDEX = "block-data.index";
	private static final String INDEX_HEADER = "anvil-block-data-index 1";

	private final @NotNull Map<MinecraftVersion, Release> releases;
	private final @NotNull Map<MinecraftVersion, BlockRegistry> registries = new ConcurrentHashMap<>();

	private JavaBlockData(@NotNull Map<MinecraftVersion, Release> releases) {
		this.releases = Collections.unmodifiableMap(new TreeMap<>(releases));
	}

	/**
	 * Returns the block data shipped with Anvil, read from its resources on first use.
	 *
	 * @return the shipped block data
	 * @throws IllegalStateException when the shipped resources are missing or malformed
	 */
	public static @NotNull JavaBlockData shipped() {
		return Shipped.DATA;
	}

	/**
	 * The Minecraft versions Anvil has block data for.
	 *
	 * @return the versions, oldest first
	 */
	public @NotNull Set<MinecraftVersion> versions() {
		return versions(true);
	}

	/**
	 * The supported Minecraft versions Anvil has no block data for, because the data source lacks them.
	 *
	 * @return the versions, oldest first
	 */
	public @NotNull Set<MinecraftVersion> missingVersions() {
		return versions(false);
	}

	/**
	 * Looks up the block registry of a Minecraft version.
	 *
	 * @param version Minecraft version, matched exactly
	 * @return the registry, or empty when Anvil has no block data for the version
	 * @throws IllegalStateException when the version's shipped resource is malformed
	 */
	public @NotNull Optional<BlockRegistry> find(@NotNull MinecraftVersion version) {
		Release release = releases.get(version);
		if (release == null || release.key() == null) return Optional.empty();

		return Optional.of(registries.computeIfAbsent(release.key(), JavaBlockData::read));
	}

	/**
	 * Returns the block registry of a Minecraft version, or reports why there is none.
	 *
	 * @param version Minecraft version, matched exactly
	 * @return the registry
	 * @throws BlockDataUnavailableException when the version is not supported, or the data source has no block data
	 *         for it
	 * @throws IllegalStateException when the version's shipped resource is malformed
	 */
	public @NotNull BlockRegistry require(@NotNull MinecraftVersion version) {
		Release release = releases.get(version);
		if (release == null)
			throw new BlockDataUnavailableException(version, "Anvil supports no such version; it ships block data for "
					+ versions() + " and knows " + missingVersions() + " without it");

		return find(version).orElseThrow(() -> new BlockDataUnavailableException(version, release.reason()));
	}

	private @NotNull Set<MinecraftVersion> versions(boolean available) {
		Set<MinecraftVersion> versions = new TreeSet<>();
		releases.forEach((version, release) -> {
			if ((release.key() != null) == available) versions.add(version);
		});

		return Collections.unmodifiableSet(versions);
	}

	private static @NotNull BlockRegistry read(@NotNull MinecraftVersion key) {
		String name = "blocks-" + key + ".txt.gz";
		try (BufferedReader input = resource(name, true)) {
			return new BlockRegistryReader(input).read();
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read the shipped block data " + name, exception);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("The shipped block data " + name + " is malformed", exception);
		}
	}

	private static @NotNull JavaBlockData index() {
		Map<MinecraftVersion, Release> releases = new TreeMap<>();
		try (BufferedReader input = resource(INDEX, false)) {
			if (!INDEX_HEADER.equals(input.readLine())) throw new IllegalStateException(INDEX + " has no '" + INDEX_HEADER + "' header");

			for (String line = input.readLine(); line != null; line = input.readLine()) {
				String[] fields = line.split(" ", 3);
				if (fields.length != 3) throw new IllegalStateException(INDEX + " has the malformed line '" + line + "'");

				Release release = switch (fields[0]) {
					case "data" -> new Release(MinecraftVersion.parse(fields[1]), "");
					case "missing" -> new Release(null, fields[2]);
					default -> throw new IllegalStateException(INDEX + " has the unknown line '" + line + "'");
				};
				String versions = fields[0].equals("data") ? fields[2] : fields[1];
				for (MinecraftVersion version : versions(versions))
					if (releases.put(version, release) != null)
						throw new IllegalStateException(INDEX + " lists Minecraft " + version + " more than once");
			}
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read the shipped block data index", exception);
		}

		return new JavaBlockData(releases);
	}

	private static @NotNull List<MinecraftVersion> versions(@NotNull String text) {
		return Arrays.stream(text.split(",")).map(MinecraftVersion::parse).toList();
	}

	private static @NotNull BufferedReader resource(@NotNull String name, boolean compressed) throws IOException {
		InputStream stream = JavaBlockData.class.getResourceAsStream(name);
		if (stream == null) throw new IllegalStateException("Anvil's block data resource " + name + " is missing");
		InputStream content = compressed ? new GZIPInputStream(stream) : stream;

		return new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8));
	}

	/**
	 * One row of the index: the key of the release's data, or {@code null} with the reason it has none.
	 */
	private record Release(@Nullable MinecraftVersion key, @NotNull String reason) {
	}

	/**
	 * Reads the shipped index once, when {@link #shipped()} is first called.
	 */
	private static final class Shipped {
		private static final JavaBlockData DATA = index();
	}
}
