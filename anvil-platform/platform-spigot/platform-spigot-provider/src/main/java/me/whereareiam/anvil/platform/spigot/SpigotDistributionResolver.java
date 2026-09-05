package me.whereareiam.anvil.platform.spigot;

import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves local Spigot artifacts or content-pinned prebuilt JARs supplied by GetBukkit.
 */
final class SpigotDistributionResolver {
	void validate(@NotNull MinecraftServer server) {
		var distribution = server.getDistribution();
		if (distribution.isLocal() || distribution.isArtifact())
			return;
		String version = distribution.getVersion();
		if (version == null || !version.matches("[0-9]+(?:\\.[0-9]+){1,2}"))
			throw new PlatformException("GetBukkit requires an exact numeric Minecraft version");
		if (distribution.getBuild() != null)
			throw new PlatformException("GetBukkit does not use BuildTools build IDs; use Distribution.pinned(version, sha256)");
		String checksum = distribution.getSha256();
		if (checksum == null || !checksum.matches("[a-fA-F0-9]{64}"))
			throw new PlatformException("GetBukkit Spigot requires a 64-digit SHA-256 pin");
	}

	@NotNull ResolvedDistribution resolve(@NotNull MinecraftServer server, @NotNull PlatformContext context) throws IOException {
		validate(server);
		var distribution = server.getDistribution();
		if (distribution.isLocal())
			return requireJar(distribution.getLocalJar().toAbsolutePath().normalize(), "local Spigot");

		String version = distribution.getVersion();
		String checksum = distribution.getSha256().toLowerCase(Locale.ROOT);
		URI source = URI.create("https://cdn.getbukkit.org/spigot/spigot-" + version + ".jar");
		Path destination = context.getCacheDirectory().resolve("distributions/getbukkit/spigot")
				.resolve(version).resolve(checksum).resolve("spigot-" + version + ".jar");
		Path jar = context.getArtifactResolver().obtain(source, destination, checksum);
		return requireJar(jar, "GetBukkit Spigot " + version + " (SHA-256 " + checksum + ")");
	}

	private ResolvedDistribution requireJar(Path jar, String description) {
		if (!Files.isRegularFile(jar))
			throw new PlatformException("Expected Spigot artifact does not exist: " + jar);
		return ResolvedDistribution.builder().jar(jar).description(description).build();
	}
}
