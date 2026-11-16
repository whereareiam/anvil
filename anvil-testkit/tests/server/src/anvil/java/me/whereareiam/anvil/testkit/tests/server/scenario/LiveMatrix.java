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
 * The live matrix as {@code live-matrix.toml} at the repository root lists it, one table per platform: the Paper
 * server of each release, the Spigot servers that also run behind the proxies, the proxies' builds, and the NeoForge
 * servers, each with the Java the suites run it on. The build reads the same file to register one release test task
 * per Paper row and to write what the platforms' and protocol libraries' version data call verified.
 */
@Value
class LiveMatrix {
	private static final String RESOURCE = "live-matrix.toml";

	/**
	 * One direct Paper server per release, oldest first.
	 */
	@NotNull List<Release> releases;
	/**
	 * Spigot servers, which also run behind both proxies.
	 */
	@NotNull List<SpigotRelease> spigot;
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
		for (JsonNode row : root.path("paper"))
			releases.add(new Release(version(row), text(row, "build"), java(row)));

		List<SpigotRelease> spigot = new ArrayList<>();
		for (JsonNode row : root.path("spigot")) {
			MinecraftVersion version = version(row);
			Release paper = releases.stream()
					.filter(candidate -> candidate.minecraft().equals(version))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("Spigot " + version + " has no Paper row to run beside"));
			spigot.add(new SpigotRelease(version, text(row, "sha256"), java(row), paper));
		}

		JsonNode velocity = root.path("velocity");
		Proxies proxies = new Proxies(text(velocity, "version"), text(velocity, "build"), java(velocity),
				text(root.path("bungeecord"), "build"));

		List<NeoForgeRelease> neoForge = new ArrayList<>();
		for (JsonNode row : root.path("neoforge"))
			neoForge.add(new NeoForgeRelease(version(row), text(row, "release"), java(row)));

		return new LiveMatrix(List.copyOf(releases), List.copyOf(spigot), proxies, List.copyOf(neoForge));
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

	private static int java(JsonNode row) {
		JsonNode value = row.get("java");
		if (value == null || !value.isIntegralNumber())
			throw new IllegalStateException("The live matrix needs a Java feature version for 'java' in " + row);

		return value.asInt();
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
	 * @param build pinned Paper build
	 * @param java Java feature version it runs on
	 */
	record Release(@NotNull MinecraftVersion minecraft, @NotNull String build, int java) {
	}

	/**
	 * A Spigot server, which also runs behind the proxies beside the Paper server of its Minecraft version.
	 *
	 * @param minecraft Minecraft version
	 * @param sha256 checksum of the pinned Spigot JAR
	 * @param java Java feature version it runs on
	 * @param paper the Paper release of the same Minecraft version
	 */
	record SpigotRelease(@NotNull MinecraftVersion minecraft, @NotNull String sha256, int java, @NotNull Release paper) {
	}

	/**
	 * The pinned builds of the proxies. BungeeCord carries no release version and runs on the Java planning selects.
	 *
	 * @param velocityVersion Velocity version
	 * @param velocityBuild Velocity build
	 * @param velocityJava Java feature version Velocity runs on
	 * @param bungeeCordBuild BungeeCord build
	 */
	record Proxies(
			@NotNull String velocityVersion,
			@NotNull String velocityBuild,
			int velocityJava,
			@NotNull String bungeeCordBuild
	) {
	}

	/**
	 * One direct NeoForge server.
	 *
	 * @param minecraft Minecraft version
	 * @param release NeoForge release
	 * @param java Java feature version it runs on
	 */
	record NeoForgeRelease(@NotNull MinecraftVersion minecraft, @NotNull String release, int java) {
	}
}
