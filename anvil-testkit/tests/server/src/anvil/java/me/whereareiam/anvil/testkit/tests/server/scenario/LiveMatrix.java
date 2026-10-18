package me.whereareiam.anvil.testkit.tests.server.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The live matrix as {@code live-matrix.toml} lists it: the Paper build of each release, the releases that also run
 * on Spigot and behind the proxies, the proxies' builds, and the NeoForge releases. The build reads the same file to
 * register one release test task per release.
 */
@Value
class LiveMatrix {
	private static final String RESOURCE = "live-matrix.toml";

	/**
	 * One direct Paper server per release, oldest first.
	 */
	@NotNull List<Release> releases;
	/**
	 * Releases that also run on Spigot and behind both proxies.
	 */
	@NotNull List<PlatformRelease> platforms;
	/**
	 * The proxies' pinned builds.
	 */
	@NotNull Proxies proxies;
	/**
	 * Direct NeoForge servers.
	 */
	@NotNull List<NeoForgeRelease> neoForge;

	/**
	 * Reads the matrix bundled beside this class.
	 *
	 * @return the live matrix
	 * @throws IllegalStateException when the file is missing or invalid
	 */
	static @NotNull LiveMatrix read() {
		try (InputStream input = LiveMatrix.class.getResourceAsStream(RESOURCE)) {
			if (input == null)
				throw new IllegalStateException("The live matrix " + RESOURCE + " is not on the class path");

			return read(new TomlMapper().readTree(input));
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read the live matrix " + RESOURCE, failure);
		}
	}

	private static LiveMatrix read(JsonNode root) {
		List<Release> releases = new ArrayList<>();
		for (JsonNode row : root.path("release"))
			releases.add(new Release(version(row), text(row, "paper")));

		List<PlatformRelease> platforms = new ArrayList<>();
		for (JsonNode row : root.path("platform")) {
			MinecraftVersion version = version(row);
			Release release = releases.stream()
					.filter(candidate -> candidate.minecraft().equals(version))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("Platform release " + version + " names no release"));
			platforms.add(new PlatformRelease(release, text(row, "spigot-sha256")));
		}

		JsonNode proxy = root.path("proxy");
		Proxies proxies = new Proxies(text(proxy, "velocity-version"), text(proxy, "velocity-build"),
				text(proxy, "bungeecord-build"));

		List<NeoForgeRelease> neoForge = new ArrayList<>();
		for (JsonNode row : root.path("neoforge"))
			neoForge.add(new NeoForgeRelease(version(row), text(row, "release")));

		return new LiveMatrix(List.copyOf(releases), List.copyOf(platforms), proxies, List.copyOf(neoForge));
	}

	/**
	 * Finds the release of a Minecraft version.
	 *
	 * @param version Minecraft version
	 * @return the release, when the matrix runs it
	 */
	@NotNull Optional<Release> release(@NotNull MinecraftVersion version) {
		return releases.stream().filter(release -> release.minecraft().equals(version)).findFirst();
	}

	private static MinecraftVersion version(JsonNode row) {
		return MinecraftVersion.parse(text(row, "minecraft"));
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || value.asText().isBlank())
			throw new IllegalStateException("The live matrix needs a text value for '" + field + "' in " + node);

		return value.asText();
	}

	/**
	 * One release key's direct Paper server.
	 *
	 * @param minecraft Minecraft version
	 * @param paper pinned Paper build
	 */
	record Release(@NotNull MinecraftVersion minecraft, @NotNull String paper) {
	}

	/**
	 * A release that also runs on Spigot and behind the proxies.
	 *
	 * @param release the release, whose Paper build proxied Paper servers use
	 * @param spigotSha256 checksum of the pinned Spigot JAR
	 */
	record PlatformRelease(@NotNull Release release, @NotNull String spigotSha256) {
	}

	/**
	 * The pinned builds of the proxies.
	 *
	 * @param velocityVersion Velocity version
	 * @param velocityBuild Velocity build
	 * @param bungeeCordBuild BungeeCord build
	 */
	record Proxies(@NotNull String velocityVersion, @NotNull String velocityBuild, @NotNull String bungeeCordBuild) {
	}

	/**
	 * One direct NeoForge server.
	 *
	 * @param minecraft Minecraft version
	 * @param release NeoForge release
	 */
	record NeoForgeRelease(@NotNull MinecraftVersion minecraft, @NotNull String release) {
	}
}
