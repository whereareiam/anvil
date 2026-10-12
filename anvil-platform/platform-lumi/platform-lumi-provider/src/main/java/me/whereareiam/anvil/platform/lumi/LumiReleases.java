package me.whereareiam.anvil.platform.lumi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * The provider's own part of {@code lumi-versions.toml}: the protocol Lumi accepts for each Minecraft version and
 * the versions each Lumi release accepts. Platform planning reads the rest of the file.
 */
final class LumiReleases {
	private final Map<MinecraftVersion, Integer> protocols = new TreeMap<>();
	private final Map<String, Range> releases = new TreeMap<>();

	LumiReleases(@NotNull URL resource) {
		JsonNode lumi;
		try (InputStream input = resource.openStream()) {
			lumi = new TomlMapper().readTree(input).path("lumi");
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read Lumi version data " + resource, exception);
		}

		lumi.path("protocol").properties()
				.forEach(entry -> protocols.put(MinecraftVersion.parse(entry.getKey()), entry.getValue().intValue()));
		lumi.path("release").properties().forEach(entry -> releases.put(entry.getKey(), new Range(
				MinecraftVersion.parse(entry.getValue().path("oldest").asText()),
				MinecraftVersion.parse(entry.getValue().path("newest").asText())
		)));
	}

	/**
	 * Returns the protocol number Lumi accepts for a Minecraft version.
	 *
	 * @throws PlatformException when the version has no known Lumi protocol
	 */
	int protocol(@NotNull MinecraftVersion version) {
		Integer protocol = protocols.get(version);
		if (protocol == null)
			throw new PlatformException("Lumi knows no protocol of Minecraft " + version + "; supported versions are "
					+ protocols.keySet());

		return protocol;
	}

	/**
	 * Refuses a Lumi release that is not listed or does not accept a Minecraft version.
	 *
	 * @throws PlatformException when the release cannot serve the version
	 */
	void requireSupport(@NotNull String release, @NotNull MinecraftVersion version) {
		Range range = releases.get(release);
		if (range == null)
			throw new PlatformException("Lumi release " + release + " is not in Anvil's Lumi version data; known releases are "
					+ releases.keySet());

		protocol(version);
		if (version.compareTo(range.oldest()) < 0 || version.compareTo(range.newest()) > 0)
			throw new PlatformException("Lumi release " + release + " does not support Minecraft " + version
					+ "; it accepts " + range.oldest() + " to " + range.newest());
	}

	/**
	 * Oldest and newest Minecraft version one Lumi release accepts.
	 *
	 * @param oldest oldest accepted version
	 * @param newest newest accepted version
	 */
	private record Range(MinecraftVersion oldest, MinecraftVersion newest) { }
}
