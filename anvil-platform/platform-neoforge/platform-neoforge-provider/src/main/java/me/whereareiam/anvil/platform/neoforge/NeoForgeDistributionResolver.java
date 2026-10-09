package me.whereareiam.anvil.platform.neoforge;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Resolves a NeoForge installer, local or from the NeoForged Maven repository, and the server it installs.
 * A remote distribution names the Minecraft version and, as its build, the NeoForge release.
 */
final class NeoForgeDistributionResolver {
	private static final String REPOSITORY = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";
	private static final String CHECKSUM = "[a-fA-F0-9]{64}";

	void validate(@NotNull MinecraftServer server) {
		Distribution distribution = server.getDistribution();
		if (distribution.isLocal() || distribution.isArtifact())
			return;

		String version = distribution.getVersion();
		if (version == null || !version.matches("[0-9]+(?:\\.[0-9]+){1,2}"))
			throw new PlatformException("NeoForge requires an exact numeric Minecraft version");

		String release = distribution.getBuild();
		if (release == null || release.isBlank() || distribution.isLatest())
			throw new PlatformException("NeoForge requires an exact NeoForge release as its build, "
					+ "such as Distribution.remote(\"1.21.11\", \"21.11.45\")");

		String prefix = releasePrefix(MinecraftVersion.parse(version));
		if (!release.startsWith(prefix))
			throw new PlatformException("NeoForge release " + release + " is not a release for Minecraft " + version
					+ ", whose releases start with " + prefix);

		String checksum = distribution.getSha256();
		if (checksum != null && !checksum.matches(CHECKSUM))
			throw new PlatformException("NeoForge installer pin must be a 64-digit SHA-256");
	}

	@NotNull ResolvedDistribution resolve(@NotNull MinecraftServer server, @NotNull PlatformContext context) throws IOException {
		validate(server);

		NeoForgeInstallation installation = installation(server, context);
		Path installer = installer(server, context);
		installation.install(installer);

		Distribution distribution = server.getDistribution();
		String description = distribution.isLocal()
				? "local NeoForge installer"
				: "NeoForge " + distribution.getBuild() + " for Minecraft " + distribution.getVersion();

		return ResolvedDistribution.builder().jar(installation.serverJar()).description(description).build();
	}

	/**
	 * Returns where the server of this distribution is, or will be, installed in the cache.
	 */
	@NotNull NeoForgeInstallation installation(@NotNull MinecraftServer server, @NotNull PlatformContext context) throws IOException {
		Distribution distribution = server.getDistribution();
		Path root = context.getCacheDirectory().resolve("distributions/neoforge");
		if (distribution.isLocal())
			return new NeoForgeInstallation(root.resolve("local").resolve(sha256(localInstaller(distribution))));

		return new NeoForgeInstallation(root.resolve(distribution.getBuild()));
	}

	private Path installer(MinecraftServer server, PlatformContext context) throws IOException {
		Distribution distribution = server.getDistribution();
		if (distribution.isLocal())
			return localInstaller(distribution);

		String release = distribution.getBuild();
		URI source = URI.create(REPOSITORY + release + "/neoforge-" + release + "-installer.jar");
		String checksum = distribution.getSha256();
		if (checksum == null)
			checksum = context.getArtifactSource().read(URI.create(source + ".sha256")).strip();
		if (!checksum.matches(CHECKSUM))
			throw new PlatformException("NeoForged Maven returned an invalid SHA-256 for NeoForge " + release);

		Path destination = installation(server, context).directory().resolve("neoforge-" + release + "-installer.jar");
		return context.getArtifactSource().obtain(source, destination, checksum.toLowerCase(Locale.ROOT));
	}

	private Path localInstaller(Distribution distribution) {
		Path installer = distribution.getLocalJar().toAbsolutePath().normalize();
		if (!Files.isRegularFile(installer))
			throw new PlatformException("NeoForge installer does not exist: " + installer);

		return installer;
	}

	/**
	 * NeoForge releases drop Minecraft's leading {@code 1.}: {@code 1.21.11} is {@code 21.11.x} and
	 * {@code 1.21} is {@code 21.0.x}. From Minecraft 26 on they keep the whole version, {@code 26.1.2.x}.
	 */
	private String releasePrefix(MinecraftVersion version) {
		String[] parts = version.toString().split("\\.");
		if (parts[0].equals("1"))
			return parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + ".";

		return parts[0] + "." + parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + ".";
	}

	private String sha256(Path file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}

		try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
			input.transferTo(OutputStream.nullOutputStream());
		}

		return HexFormat.of().formatHex(digest.digest());
	}
}
