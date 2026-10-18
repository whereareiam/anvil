package me.whereareiam.anvil.testkit.tests.server.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The physics suite's matrix as {@code physics-matrix.toml} lists it: the releases whose players run client physics,
 * and which of the Grim and Sonar judges support each. The build reads the same file to register one physics test
 * task per release and to download the pinned plugins.
 */
@Value
class PhysicsMatrix {
	private static final String RESOURCE = "physics-matrix.toml";

	/**
	 * The releases, oldest first.
	 */
	@NotNull List<Release> releases;

	/**
	 * Reads the matrix bundled beside this class.
	 *
	 * @return the physics matrix
	 * @throws IllegalStateException when the file is missing or invalid
	 */
	static @NotNull PhysicsMatrix read() {
		try (InputStream input = PhysicsMatrix.class.getResourceAsStream(RESOURCE)) {
			if (input == null)
				throw new IllegalStateException("The physics matrix " + RESOURCE + " is not on the class path");

			return read(new TomlMapper().readTree(input));
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read the physics matrix " + RESOURCE, failure);
		}
	}

	private static PhysicsMatrix read(JsonNode root) {
		List<Release> releases = new ArrayList<>();
		for (JsonNode row : root.path("release")) {
			JsonNode minecraft = row.get("minecraft");
			if (minecraft == null || !minecraft.isTextual())
				throw new IllegalStateException("The physics matrix needs a text value for 'minecraft' in " + row);

			JsonNode grimJava = row.get("grim-java");
			releases.add(new Release(
					MinecraftVersion.parse(minecraft.asText()),
					row.path("grim").asBoolean(false),
					grimJava == null ? null : grimJava.asInt(),
					row.path("sonar").asBoolean(false)
			));
		}

		return new PhysicsMatrix(List.copyOf(releases));
	}

	/**
	 * Finds the row of a Minecraft version.
	 *
	 * @param version Minecraft version
	 * @return the row, when the physics suite runs the release
	 */
	@NotNull Optional<Release> release(@NotNull MinecraftVersion version) {
		return releases.stream().filter(release -> release.minecraft().equals(version)).findFirst();
	}

	/**
	 * One release of the physics suite.
	 *
	 * @param minecraft Minecraft version
	 * @param grim whether the pinned Grim build supports the release
	 * @param grimJava the Java the release's Grim server runs on, or null for the release's default
	 * @param sonar whether the pinned Sonar build supports the release
	 */
	record Release(@NotNull MinecraftVersion minecraft, boolean grim, @Nullable Integer grimJava, boolean sonar) {
	}
}
