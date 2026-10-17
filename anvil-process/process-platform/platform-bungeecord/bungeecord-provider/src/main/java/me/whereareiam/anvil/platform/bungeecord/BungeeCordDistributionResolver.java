package me.whereareiam.anvil.platform.bungeecord;

import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves a local BungeeCord JAR or an explicitly selected Jenkins build.
 */
final class BungeeCordDistributionResolver {
	ResolvedDistribution resolve(MinecraftProxy proxy, PlatformContext context) {
		var distribution = proxy.getDistribution();
		if (distribution.isLocal()) {
			Path jar = distribution.getLocalJar().toAbsolutePath().normalize();
			if (!Files.isRegularFile(jar))
				throw new PlatformException("BungeeCord JAR does not exist: " + jar);

			return ResolvedDistribution.builder().jar(jar).description("local BungeeCord").build();
		}
		String build = distribution.getBuild();
		if (build == null || build.isBlank()) {
			throw new PlatformException("BungeeCord build is required for a remote distribution");
		}

		String selected = build.equals("latest") ? "lastStableBuild" : build;
		URI uri = URI.create("https://ci.md-5.net/job/BungeeCord/" + selected
				+ "/artifact/bootstrap/target/BungeeCord.jar");
		Path destination = context.getCacheDirectory().resolve("distributions/bungeecord")
				.resolve(build).resolve("BungeeCord.jar");

		Path jar = context.getArtifactSource().obtain(uri, destination, distribution.getSha256());
		return ResolvedDistribution.builder().jar(jar).description("BungeeCord build " + build).build();
	}
}
