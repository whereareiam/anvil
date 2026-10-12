package me.whereareiam.anvil.platform.lumi;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.Distribution;
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
 * Resolves a Lumi server JAR, local or from Lumi's Maven repository. A remote distribution names the Minecraft
 * version and, as its build, the Lumi release.
 */
@RequiredArgsConstructor
final class LumiDistributionResolver {
	private static final String REPOSITORY = "https://repo.lumi.su/releases/com/koshakmine/Lumi/";
	private static final String CHECKSUM = "[a-fA-F0-9]{64}";

	private final @NotNull LumiReleases releases;

	void validate(@NotNull MinecraftServer server) {
		Distribution distribution = server.getDistribution();
		if (distribution.isLocal() || distribution.isArtifact())
			return;

		String version = distribution.getVersion();
		if (version == null || !version.matches("[0-9]+(?:\\.[0-9]+){1,2}"))
			throw new PlatformException("Lumi requires an exact numeric Minecraft version");

		String release = distribution.getBuild();
		if (release == null || release.isBlank() || distribution.isLatest())
			throw new PlatformException("Lumi requires an exact Lumi release as its build, "
					+ "such as Distribution.remote(\"1.26.50\", \"1.6.9\")");

		releases.requireSupport(release, MinecraftVersion.parse(version));

		String checksum = distribution.getSha256();
		if (checksum != null && !checksum.matches(CHECKSUM))
			throw new PlatformException("Lumi JAR pin must be a 64-digit SHA-256");
	}

	@NotNull ResolvedDistribution resolve(@NotNull MinecraftServer server, @NotNull PlatformContext context) throws IOException {
		validate(server);

		Distribution distribution = server.getDistribution();
		if (distribution.isLocal())
			return ResolvedDistribution.builder().jar(localJar(distribution)).description("local Lumi").build();

		String release = distribution.getBuild();
		URI source = URI.create(REPOSITORY + release + "/Lumi-" + release + ".jar");
		String checksum = distribution.getSha256();
		if (checksum == null)
			checksum = context.getArtifactSource().read(URI.create(source + ".sha256")).strip();
		if (!checksum.matches(CHECKSUM))
			throw new PlatformException("Lumi's Maven repository returned an invalid SHA-256 for Lumi " + release);

		Path destination = context.getCacheDirectory().resolve("distributions/lumi").resolve(release)
				.resolve("Lumi-" + release + ".jar");
		Path jar = context.getArtifactSource().obtain(source, destination, checksum.toLowerCase(Locale.ROOT));

		return ResolvedDistribution.builder()
				.jar(jar)
				.description("Lumi " + release + " for Minecraft " + distribution.getVersion())
				.build();
	}

	private Path localJar(Distribution distribution) {
		Path jar = distribution.getLocalJar().toAbsolutePath().normalize();
		if (!Files.isRegularFile(jar))
			throw new PlatformException("Lumi JAR does not exist: " + jar);

		return jar;
	}
}
